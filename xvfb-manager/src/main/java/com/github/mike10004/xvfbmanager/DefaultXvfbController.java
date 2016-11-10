/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputResult;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.github.mike10004.xvfbmanager.Poller.FinishReason;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class DefaultXvfbController implements XvfbController {

    public static final long DEFAULT_POLL_INTERVAL_MS = 250;
    public static final int DEFAULT_MAX_NUM_POLLS = 8;

    private final ListenableFuture<? extends ProgramWithOutputResult> xvfbFuture;
    private final String display;
    private final XvfbManager.DisplayReadinessChecker displayReadinessChecker;
    private final XvfbManager.Screenshooter screenshooter;
    private final Sleeper sleeper;
    private final AtomicBoolean abort;

    public DefaultXvfbController(ListenableFuture<? extends ProgramWithOutputResult> xvfbFuture, String display, XvfbManager.DisplayReadinessChecker displayReadinessChecker, XvfbManager.Screenshooter screenshooter, Sleeper sleeper) {
        this.xvfbFuture = checkNotNull(xvfbFuture);
        this.display = checkNotNull(display);
        this.displayReadinessChecker = checkNotNull(displayReadinessChecker);
        this.screenshooter = checkNotNull(screenshooter);
        this.sleeper = checkNotNull(sleeper);
        abort = new AtomicBoolean(false);
    }

    void setAbort(boolean waitable) {
        this.abort.getAndSet(waitable);
    }

    public void waitUntilReady() throws InterruptedException {
        waitUntilReady(DEFAULT_POLL_INTERVAL_MS, DEFAULT_MAX_NUM_POLLS);
    }

    @Override
    public String getDisplay() {
        return display;
    }

    static class ReadinessPollingException extends XvfbException {
        public ReadinessPollingException() {
        }

        public ReadinessPollingException(String message) {
            super(message);
        }

        public ReadinessPollingException(String message, Throwable cause) {
            super(message, cause);
        }

        public ReadinessPollingException(Throwable cause) {
            super(cause);
        }
    }

    private boolean checkAbort() throws ReadinessPollingException {
        boolean w = abort.get();
        if (w) {
            throw new ReadinessPollingException("not abort");
        }
        return w;
    }

    public void waitUntilReady(long pollIntervalMs, int maxNumPolls) throws InterruptedException {
        PollOutcome<Boolean> pollResult = new Poller<Boolean>(sleeper) {
            @Override
            protected PollAnswer<Boolean> check(int pollAttemptsSoFar) {
                boolean ready = displayReadinessChecker.checkReadiness(display);
                return ready ? stopPolling(true) : continuePolling(false);
            }

            @Override
            protected boolean isAborted() {
                return checkAbort();
            }
        }.poll(pollIntervalMs, maxNumPolls);
        boolean displayReady = (pollResult.reason == FinishReason.STOPPED) && pollResult.content != null && pollResult.content;
        if (!displayReady) {
            throw new XvfbException("display never became ready: " + pollResult);
        }
    }

    @Override
    public void stop() {
        if (!xvfbFuture.isDone()) {
            xvfbFuture.cancel(true);
        }
    }

    @Override
    public XvfbManager.Screenshot captureScreenshot() throws IOException {
        return screenshooter.capture();
    }

    protected static Builder builder(String display) {
        return new Builder(display);
    }

    /**
     * Invokes {@link #stop()}.
     */
    @Override
    public void close() {
        stop();
    }

    protected static class Builder {
        private final String display;
        private XvfbManager.DisplayReadinessChecker checker;
        private XvfbManager.Screenshooter screenshooter;
        private Sleeper sleeper;

        public Builder(String display) {
            this.display = checkNotNull(display);
            checkArgument(!display.isEmpty(), "display variable value must be nonempty");
            this.sleeper = Sleeper.DEFAULT;
            this.screenshooter = new XwdScreenshooter(display, FileUtils.getTempDirectory());
        }

        public Builder withReadinessChecker(XvfbManager.DisplayReadinessChecker checker) {
            this.checker = checkNotNull(checker);
            return this;
        }

        public Builder withScreenshooter(XvfbManager.Screenshooter screenshooter) {
            this.screenshooter = checkNotNull(screenshooter);
            return this;
        }

        public Builder withSleeper(Sleeper sleeper) {
            this.sleeper = checkNotNull(sleeper);
            return this;
        }

        public DefaultXvfbController build(ListenableFuture<? extends ProgramWithOutputResult> xvfbFuture) {
            return new DefaultXvfbController(xvfbFuture, display, checker, screenshooter, sleeper);
        }
    }

    @Override
    public Optional<TreeNode<XWindow>> pollForWindow(Predicate<XWindow> windowFinder, long intervalMs, int maxPollAttempts) throws InterruptedException {
        XWindowPoller poller = new XWindowPoller(display, windowFinder);
        PollOutcome<TreeNode<XWindow>> pollResult = poller.poll(intervalMs, maxPollAttempts);
        return Optional.fromNullable(pollResult.content);
    }

    private static class XWindowPoller extends Poller<TreeNode<XWindow>> {

        private final String display;
        private final Predicate<XWindow> evaluator;

        public XWindowPoller(String display, Predicate<XWindow> evaluator) {
            super(Sleeper.DEFAULT);
            this.display = checkNotNull(display);
            this.evaluator = checkNotNull(evaluator);
        }

        @Override
        protected PollAnswer<TreeNode<XWindow>> check(int pollAttemptsSoFar) {
            ProgramWithOutputStringsResult result = Program.running("xwininfo")
                    .args("-display", display)
                    .args("-root", "-tree")
                    .outputToStrings()
                    .execute();
            if (result.getExitCode() == 0) {
                try {
                    TreeNode<XWindow> root = CharSource.wrap(result.getStdoutString()).readLines(new XwininfoXwindowParser());
                    final @Nullable XWindow match = Iterables.find(root, evaluator, null);
                    if (match != null) {
                        TreeNode<XWindow> targetWindowNode = Iterables.find(TreeNode.Utils.<XWindow>traverser().breadthFirstTraversal(root), new Predicate<TreeNode<XWindow>>() {
                            @Override
                            public boolean apply(TreeNode<XWindow> input) {
                                return match == input.getLabel();
                            }
                        });
                        return stopPolling(targetWindowNode);
                    } else {
                        return continuePolling(root);
                    }
                } catch (IOException e) {
                    throw new XvfbException(e);
                }
            } else {
                return continuePolling(null);
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
        public boolean processLine(String line) throws IOException {
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
