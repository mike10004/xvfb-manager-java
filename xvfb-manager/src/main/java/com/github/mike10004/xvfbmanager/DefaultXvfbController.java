/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.github.mike10004.xvfbmanager.TreeNode.Utils;
import com.github.mike10004.xvfbmanager.XvfbManager.DisplayReadinessChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Default controller implementation. This implementation relies on a {@link ListenableFuture future}
 * to listen to the status of the {@code Xvfb} process. It checks for
 * a given window by executing {@code xwininfo} in {@link #pollForWindow(Predicate, long, int)}.
 * Most other operations are handled by the service classes provided in
 * the constructor.
 */
public class DefaultXvfbController implements XvfbController {

    private static final Logger log = LoggerFactory.getLogger(DefaultXvfbController.class);

    private static final Iterable<String> requiredPrograms = Iterables.concat(XWindowPoller.getRequiredPrograms());

    public static Iterable<String> getRequiredPrograms() {
        return requiredPrograms;
    }

    /**
     * Default poll interval for {@link #waitUntilReady()}}.
     */
    public static final long DEFAULT_POLL_INTERVAL_MS = 250;

    /**
     * Default max number of polls for {@link #waitUntilReady()}}.
     */
    public static final int DEFAULT_MAX_NUM_POLLS = 8;

    protected static final long LOCK_FILE_CLEANUP_POLL_INTERVAL_MS = 100;
    protected static final long LOCK_FILE_CLEANUP_TIMEOUT_MS = 1000;

    private final ProcessMonitor<?, ?> xvfbMonitor;
    private final String display;
    private final DisplayReadinessChecker displayReadinessChecker;
    private final XLockFileChecker lockFileChecker;
    private final Screenshooter<?> screenshooter;
    private final Sleeper sleeper;
    private final AtomicBoolean abort;

    public DefaultXvfbController(ProcessMonitor<?, ?> xvfbMonitor, String display,
                                 DisplayReadinessChecker displayReadinessChecker,
                                 Screenshooter<?> screenshooter, Sleeper sleeper) {
        this(xvfbMonitor, display, displayReadinessChecker, screenshooter, sleeper, new PollingXLockFileChecker(LOCK_FILE_CLEANUP_POLL_INTERVAL_MS, sleeper));
    }

    @VisibleForTesting
    protected DefaultXvfbController(ProcessMonitor<?, ?> xvfbMonitor, String display,
                                    DisplayReadinessChecker displayReadinessChecker,
                                    Screenshooter<?> screenshooter, Sleeper sleeper, XLockFileChecker lockFileChecker) {
        this.xvfbMonitor = requireNonNull(xvfbMonitor);
        this.display = checkNotNull(display);
        this.displayReadinessChecker = checkNotNull(displayReadinessChecker);
        this.screenshooter = checkNotNull(screenshooter);
        this.sleeper = checkNotNull(sleeper);
        abort = new AtomicBoolean(false);
        this.lockFileChecker = checkNotNull(lockFileChecker);
    }

    void setAbort(@SuppressWarnings("SameParameterValue") boolean abort) {
        this.abort.getAndSet(abort);
    }

    public void waitUntilReady() throws InterruptedException {
        waitUntilReady(DEFAULT_POLL_INTERVAL_MS, DEFAULT_MAX_NUM_POLLS);
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public Map<String, String> configureEnvironment(Map<String, String> environment) {
        environment.put(ENV_DISPLAY, display);
        return environment;
    }

    @Override
    public Map<String, String> newEnvironment() {
        return configureEnvironment(createEmptyMutableMap());
    }

    protected Map<String, String> createEmptyMutableMap() {
        return new HashMap<>();
    }

    private boolean checkAbort() {
        return abort.get();
    }

    private String formatXvfbExitedMessage(ProcessResult<?, ?> result) {
        String info = null;
        if (result.exitCode() != 0) {
            info = result.toString();
        }
        return "xvfb already exited with code " + result.exitCode() + (info == null ? "" : ": " + info);
    }

    private boolean isXvfbAlreadyDone() {
        if (xvfbMonitor.future().isDone()) {
            try {
                ProcessResult<?, ?> result = xvfbMonitor.await();
                String message = formatXvfbExitedMessage(result);
                log.error(message);
            } catch (InterruptedException e) {
                throw new IllegalStateException("ProcessMonitor.await() should return immediately if Future.isDone() is true", e);
            }
            return true;
        } else {
            return false;
        }
    }

    public void waitUntilReady(long pollIntervalMs, int maxNumPolls) throws InterruptedException {
        PollOutcome<Boolean> pollResult = new Poller<Boolean>(sleeper) {
            @Override
            protected PollAnswer<Boolean> check(int pollAttemptsSoFar) {
                if (isXvfbAlreadyDone()) {
                    return abortPolling();
                }
                if (checkAbort()) {
                    return abortPolling();
                }
                boolean ready = displayReadinessChecker.checkReadiness(display);
                return ready ? resolve(true) : continuePolling();
            }
        }.poll(pollIntervalMs, maxNumPolls);
        boolean displayReady = (pollResult.reason == StopReason.RESOLVED) && pollResult.content != null && pollResult.content;
        if (!displayReady) {
            throw new XvfbException("display never became ready: " + pollResult);
        }
    }

    private static final int SIGTERM_TIMEOUT_MILLIS = 500;

    @Override
    public void stop() {
        if (xvfbMonitor.process().isAlive()) {
            xvfbMonitor.destructor().sendTermSignal().timeout(SIGTERM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).kill();
            waitForXLockFileCleanup();
        }
    }

    protected interface XLockFileChecker {
        void waitForCleanup(String display, long timeoutMs) throws LockFileCheckingException;

        @SuppressWarnings("unused")
        class LockFileCheckingException extends XvfbException {
            public LockFileCheckingException() {
            }

            public LockFileCheckingException(String message) {
                super(message);
            }

            public LockFileCheckingException(String message, Throwable cause) {
                super(message, cause);
            }

            public LockFileCheckingException(Throwable cause) {
                super(cause);
            }
        }

        @SuppressWarnings("unused")
        class LockFileCleanupTimeoutException extends LockFileCheckingException {
            public LockFileCleanupTimeoutException(String message) {
                super(message);
            }
        }
    }

    protected void waitForXLockFileCleanup() {
        lockFileChecker.waitForCleanup(display, LOCK_FILE_CLEANUP_TIMEOUT_MS);
    }

    @Override
    public Screenshooter<?> getScreenshooter() throws XvfbException {
        return screenshooter;
    }

    /**
     * Invokes {@link #stop()}.
     */
    @Override
    public void close() {
        stop();
    }

    @Override
    public Optional<TreeNode<XWindow>> pollForWindow(java.util.function.Predicate<XWindow> windowFinder, long intervalMs, int maxPollAttempts) throws InterruptedException {
        XWindowPoller poller = new XWindowPoller(xvfbMonitor.tracker(), display, windowFinder);
        PollOutcome<TreeNode<XWindow>> pollResult = poller.poll(intervalMs, maxPollAttempts);
        return Optional.ofNullable(pollResult.content);
    }

    private static class XWindowPoller extends Poller<TreeNode<XWindow>> {

        private static final String PROG_XWININFO = "xwininfo";

        private static final ImmutableSet<String> requiredPrograms = ImmutableSet.of(PROG_XWININFO);

        public static Iterable<String> getRequiredPrograms() {
            return requiredPrograms;
        }

        private final String display;
        private final java.util.function.Predicate<XWindow> evaluator;
        private final ProcessTracker processTracker;

        public XWindowPoller(ProcessTracker processTracker, String display, java.util.function.Predicate<XWindow> evaluator) {
            super();
            this.processTracker = requireNonNull(processTracker);
            this.display = checkNotNull(display);
            this.evaluator = checkNotNull(evaluator);
        }

        private static final int XWININFO_SIGTERM_TIMEOUT_MILLIS = 1000;

        @Override
        protected PollAnswer<TreeNode<XWindow>> check(int pollAttemptsSoFar) {
            ProcessMonitor<String, String> xwininfoMonitor = Subprocess.running(PROG_XWININFO)
                    .args("-display", display)
                    .args("-root", "-tree")
                    .build()
                    .launcher(processTracker)
                    .outputStrings(Charset.defaultCharset()) // presumably writes in system charset
                    .launch();
            ProcessResult<String, String> result = null;
            try {
                result = xwininfoMonitor.await();
            } catch (InterruptedException e) {
                log.error("interrupted while waiting for xwininfo result", e);
                xwininfoMonitor.destructor().sendTermSignal()
                        .timeout(XWININFO_SIGTERM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        .kill();
            }
            if (result != null && result.exitCode() == 0) {
                try {
                    TreeNode<XWindow> root = CharSource.wrap(result.content().stdout()).readLines(new XwininfoXwindowParser());
                    //noinspection StaticPseudoFunctionalStyleMethod
                    final @Nullable XWindow match = Iterables.find(root, evaluator::test, null);
                    if (match != null) {
                        //noinspection StaticPseudoFunctionalStyleMethod
                        TreeNode<XWindow> targetWindowNode = Iterables.find(Utils.<XWindow>traverser().breadthFirstTraversal(root), input -> match == checkNotNull(input).getLabel());
                        return resolve(targetWindowNode);
                    } else {
                        return continuePolling();
                    }
                } catch (IOException e) {
                    throw new XvfbException(e);
                }
            } else {
                return continuePolling();
            }
        }
    }

    static class XwininfoXwindowParser extends XwininfoParser<XWindow> {

        static final Pattern linePattern = Pattern.compile("\\s*(0x[a-f0-9]+)\\s((?:\\Q(has no name)\\E)|(?:\".+\")):", Pattern.CASE_INSENSITIVE);

        @Override
        protected XWindow parseWindow(String line, boolean root)  {
            String id, title = null;
            if (root) {
                Matcher m = Pattern.compile("\\b0x[a-f0-9]+\\b", Pattern.CASE_INSENSITIVE).matcher(line);
                checkState(m.find(), "no id found on line %s", line);
                id = m.group(0);
            } else {
                Matcher m = linePattern.matcher(line);
                if (!m.find()) {
                    throw new IllegalArgumentException("line does not match pattern: " + line);
                }
                id = m.group(1);
                title = m.group(2);
                if ("(has no name)".equals(title)) {
                    title = null;
                } else {
                    title = CharMatcher.is('"').trimFrom(title);
                }
            }
            return new XWindow(id, title, line);
        }
    }

    static abstract class XwininfoParser<E> implements LineProcessor<TreeNode<E>> {

        static int COMMON_INDENT = 2;
        static int INDENT_PER_LEVEL = 3;

        private TreeNode<E> root = null;
        private TreeNode<E> prev = null;
        private CharMatcher ws = CharMatcher.whitespace();
        private int previousIndent = 0;

        protected boolean skip(String line, String explanation) {
            return true;
        }

        protected abstract E parseWindow(String line, boolean root);

        @Override
        public boolean processLine(@SuppressWarnings("NullableProblems") String line) throws IOException {
            if (line.trim().isEmpty()) {
                return skip(line, "empty");
            }
            if (line.startsWith("xwininfo:")) {
                return skip(line, "header");
            }
            if (line.startsWith("  Parent window id: 0x0 (none)")) {
                return skip(line, "extraneous");
            }
            if (line.matches("\\s*\\d+ child(?:ren)?[:.]\\s*")) { // "1 child:" or "6 children:" or "0 children."
                return skip(line, "childcount");
            }
            TreeNode<E> current = new ListTreeNode<>(parseWindow(line, root == null));
            if (root == null) {
                root = current;
                prev = root;
                previousIndent = measureIndent(line);
                return foundRoot(root);
            }
            int indent = measureIndent(line);
            if (indent == previousIndent) {
                TreeNode<E> parent = checkNotNull(prev.getParent(), "thought prev would not be root");
                parent.addChild(current);
                foundSibling(indent, current);
            } else {
                if (indent > previousIndent) { // we are at prev's child
                    prev.addChild(current);
                    foundChild(indent, current);
                } else { // back up as many levels as indent indicates
                    int previousLevels = (previousIndent - COMMON_INDENT) / INDENT_PER_LEVEL;
                    int levels = (indent - COMMON_INDENT) / INDENT_PER_LEVEL;
                    assert previousLevels > levels;
                    TreeNode<E> parent = prev.getParent();
                    for (int i = 0; i < (previousLevels - levels); i++) {
                        parent = parent.getParent();
                    }
                    parent.addChild(current);
                    foundAncestor(indent, current);
                }
            }
            previousIndent = indent;
            prev = current;
            return true;
        }

        protected void foundAncestor(int indent, TreeNode<E> node) {
        }

        protected void foundSibling(int indent, TreeNode<E> node) {
        }

        protected void foundChild(int indent, TreeNode<E> node) {
        }

        protected boolean foundRoot(TreeNode<E> root) {
            return true;
        }

        private int measureIndent(CharSequence seq) {
            for (int i = 0; i < seq.length(); i++) {
                char ch = seq.charAt(i);
                if (!ws.matches(ch)) {
                    return i;
                }
            }
            return 0;
        }

        @Override
        public TreeNode<E> getResult() {
            return root;
        }

        @VisibleForTesting
        TreeNode<E> parse(CharSource text) throws IOException {
            return text.readLines(this);
        }
    }
}
