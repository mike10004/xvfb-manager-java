/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Whicher;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Class that helps manage the creation of virtual framebuffer processes.
 */
public class XvfbManager {

    private static final Logger log = LoggerFactory.getLogger(XvfbManager.class);
    private static final ProcessTracker GLOBAL_PROCESS_TRACKER = ProcessTracker.create();

    private static final int SCREEN = 0;

    private final Supplier<File> xvfbExecutableSupplier;
    private final XvfbConfig xvfbConfig;
    private final ProcessTracker processTracker;

    /**
     * Constructs a default instance of the class.
     * @see #createXvfbExecutableResolver()
     */
    public XvfbManager() {
        this(createXvfbExecutableResolver(), XvfbConfig.getDefault());
    }

    /**
     * Constructs an instance of the class with a given configuration.
     * @param xvfbConfig the configuration
     * @see #createXvfbExecutableResolver()
     */
    public XvfbManager(XvfbConfig xvfbConfig) {
        this(createXvfbExecutableResolver(), xvfbConfig);
    }

    /**
     * Constructs an instance of the class that will launch the given executable
     * with the given configuration.
     * @param xvfbExecutable pathname of the {@code Xvfb} executable
     * @param xvfbConfig virtual framebuffer configuration
     */
    public XvfbManager(File xvfbExecutable, XvfbConfig xvfbConfig) {
        this(Suppliers.ofInstance(checkNotNull(xvfbExecutable, "xvfbExecutable must be non-null; use Supplier of null instance if null is desired")), xvfbConfig);
    }

    /**
     * Constructs an instance of the class that will launch the given executable
     * with the given configuration.
     * @param xvfbExecutableSupplier supplier of the pathname of the {@code Xvfb} executable
     * @param xvfbConfig virtual framebuffer configuration
     */
    public XvfbManager(Supplier<File> xvfbExecutableSupplier, XvfbConfig xvfbConfig) {
        this(xvfbExecutableSupplier, xvfbConfig, GLOBAL_PROCESS_TRACKER);
    }

    public XvfbManager(ProcessTracker processTracker) {
        this(createXvfbExecutableResolver(), XvfbConfig.getDefault(), processTracker);
    }

    public XvfbManager(Supplier<File> xvfbExecutableSupplier, XvfbConfig xvfbConfig, ProcessTracker processTracker) {
        this.xvfbExecutableSupplier = checkNotNull(xvfbExecutableSupplier);
        this.xvfbConfig = checkNotNull(xvfbConfig);
        this.processTracker = requireNonNull(processTracker);
    }

    protected static String toDisplayValue(int displayNumber) {
        checkArgument(displayNumber >= 0, "displayNumber must be nonnegative");
        return String.format(":%d", displayNumber);
    }

    /**
     * Creates a supplier that returns a valid executable or null if none was found.
     * @return a supplier
     */
    protected static Supplier<File> createXvfbExecutableResolver() {
        return new Supplier<File>() {
            @Override
            public File get() {
                try {
                    return resolveXvfbExecutable();
                } catch (FileNotFoundException e) {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "DefaultXvfbExecutableResolver";
            }
        };
    }

    protected static File resolveXvfbExecutable() throws FileNotFoundException {
        java.util.Optional<File> file = Whicher.gnu().which("Xvfb");
        if (!file.isPresent()) {
            throw new FileNotFoundException("Xvfb executable");
        }
        return file.get();
    }

    protected Screenshooter<?> createScreenshooter(String display, File framebufferDir) {
        return new FramebufferDirScreenshooter(framebufferDir, SCREEN, framebufferDir);
    }

    protected Sleeper createSleeper() {
        return Sleeper.DefaultSleeper.getInstance();
    }

    protected DisplayReadinessChecker createDisplayReadinessChecker(ProcessTracker tracker, String display, File framebufferDir) {
        return new DefaultDisplayReadinessChecker(tracker);
    }

    protected DefaultXvfbController createController(ProcessMonitor<File, File> future, String display, File framebufferDir) {
        return new DefaultXvfbController(future, display, createDisplayReadinessChecker(processTracker, display, framebufferDir), createScreenshooter(display, framebufferDir), createSleeper());
    }

    /**
     * Starts Xvfb on the specified display using the specified executor service, writing temp
     * files to the specified directory.
     * @param displayNumber the display number
     * @param scratchDir the temp directory
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(int displayNumber, Path scratchDir) throws IOException {
        return doStart(displayNumber, nonDeletingExistingDirectoryProvider(scratchDir));
    }

    /**
     * Starts Xvfb on the specified display using the specified executor service. A directory for temp files
     * is created and deleted when the process is stopped.
     * @param displayNumber the display number
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(int displayNumber) throws IOException {
        return doStart(displayNumber, newTempDirProvider(FileUtils.getTempDirectory().toPath()));
    }

    /**
     * Starts Xvfb on a vacant display. A directory for temp files will be created and deleted
     * when the process is stopped.
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start() throws IOException {
        return doStart(null, newTempDirProvider(FileUtils.getTempDirectory().toPath()));
    }

    /**
     * Starts Xvfb on a vacant display using the specified executor service and writing temp files
     * to the given directory.
     * @param scratchDir the temp directory
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(Path scratchDir) throws IOException {
        return doStart(null, nonDeletingExistingDirectoryProvider(scratchDir));
    }

    /**
     * Starts Xvfb, maybe auto-selecting a display number.
     * @param displayNumber display number, or null to auto-select
     * @param scratchDirProvider provider of scratch directory
     * @return process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    private XvfbController doStart(final @Nullable Integer displayNumber,
                                   ScratchDirProvider scratchDirProvider) throws IOException {
        String display = null;
        final boolean AUTO_DISPLAY = displayNumber == null;
        Subprocess.Builder pb;
        File xvfbExecutable = xvfbExecutableSupplier.get();
        if (xvfbExecutable == null) {
            pb = Subprocess.running("Xvfb");
        } else {
            pb = Subprocess.running(xvfbExecutable);
        }
        if (AUTO_DISPLAY) {
            pb.args("-displayfd", String.valueOf(DISPLAY_RECEIVER_FD));
        } else {
            display = toDisplayValue(displayNumber);
            pb.args(display);
        }
        Path scratchDir = scratchDirProvider.provideDirectory();
        Path framebufferDir = java.nio.file.Files.createTempDirectory(scratchDir, "xvfb-framebuffer");
        pb.args("-screen", String.valueOf(SCREEN), xvfbConfig.geometry);
        pb.args("-fbdir", framebufferDir.toAbsolutePath().toString());
        File stdoutFile = File.createTempFile("xvfb-stdout", ".txt", scratchDir.toFile());
        File stderrFile = File.createTempFile("xvfb-stderr", ".txt", scratchDir.toFile());
        Subprocess xvfbSubprocess = pb.build();
        log.trace("executing {}", xvfbSubprocess);
        Subprocess.Launcher<File, File> launcher = xvfbSubprocess.launcher(processTracker)
                .outputFiles(stdoutFile, stderrFile);
        ProcessMonitor<File, File> xvfbMonitor = launcher.launch();
        Executor callbacker = getCallbackExecutor();
        Futures.addCallback(xvfbMonitor.future(), new LoggingCallback<>("xvfb"), callbacker);
        if (scratchDirProvider.isDeleteOnStop()) {
            Futures.addCallback(xvfbMonitor.future(), new DirectoryDeletingCallback<>(scratchDir.toFile()), callbacker);
        }
        if (AUTO_DISPLAY) {
            File outputFileContainingDisplay = selectCorrespondingFile(DISPLAY_RECEIVER_FD, stdoutFile, stderrFile);
            int autoDisplayNumber = pollForDisplayNumber(Files.asCharSource(outputFileContainingDisplay, XVFB_OUTPUT_CHARSET));
            display = toDisplayValue(autoDisplayNumber);
        } else {
            //noinspection ConstantConditions
            checkState(display != null, "display should have been set manually from %s", displayNumber);
        }
        DefaultXvfbController controller = createController(xvfbMonitor, display, framebufferDir.toFile());
        Futures.addCallback(xvfbMonitor.future(), new AbortFlagSetter<>(controller), callbacker);
        return controller;
    }

    protected Executor getCallbackExecutor() {
        return MoreExecutors.directExecutor();
    }

    /**
     * File descriptor of the stream on which the display is printed. The program
     * prints on standard error, which in Linux is always file descriptor 2. We used
     * to be more abstract and extract the integer from {@link FileDescriptor#err},
     * but that was just showing off, really, and it used introspection implemented
     * by Gson that is scheduled to be removed in a future Java release.
     */
    private static final int DISPLAY_RECEIVER_FD = 2; // stderr

    private static final Charset XVFB_OUTPUT_CHARSET = Charset.defaultCharset(); // xvfb is platform-dependent

    protected static File selectCorrespondingFile(int fd, File stdoutFile, File stderrFile) throws IllegalArgumentException {
        switch (fd) {
            case 1:
                return stdoutFile;
            case 2:
                return stderrFile;
            default:
                throw new IllegalArgumentException("no known file corresponds to " + fd);
        }
    }

    interface ScratchDirProvider {
        Path provideDirectory() throws IOException;
        boolean isDeleteOnStop();
    }

    protected static ScratchDirProvider nonDeletingExistingDirectoryProvider(final Path directory) {
        return new ScratchDirProvider() {

            @Override
            public Path provideDirectory() {
                return directory;
            }

            @Override
            public boolean isDeleteOnStop() {
                return false;
            }
        };
    }

    protected static ScratchDirProvider newTempDirProvider(final Path parent) {
        return new ScratchDirProvider() {
            @Override
            public Path provideDirectory() throws IOException {
                return java.nio.file.Files.createTempDirectory(parent, "xvfb-manager");
            }

            @Override
            public boolean isDeleteOnStop() {
                return true;
            }
        };
    }

    private static final long AUTO_DISPLAY_POLL_INTERVAL_MS = 100;
    private static final int AUTO_DISPLAY_POLLS_MAX = 20;

    protected int pollForDisplayNumber(final CharSource cs) {
        Poller<Integer> poller = new Poller<Integer>() {
            @Override
            protected PollAnswer<Integer> check(int pollAttemptsSoFar) {
                @Nullable String lastLine = null;
                try {
                    lastLine = Iterables.getFirst(cs.readLines().reverse(), null);
                } catch (IOException e) {
                    log.info("failed to read from {}", cs);
                }
                if (lastLine != null) {
                    lastLine = lastLine.trim();
                    if (lastLine.matches("\\d+")) {
                        int displayNumber = Integer.parseInt(lastLine);
                        return resolve(displayNumber);
                    } else {
                        log.debug("last line of xvfb output is not an integer: {}", StringUtils.abbreviate(lastLine, 128));
                    }
                }
                return continuePolling();
            }
        };
        PollOutcome<Integer> pollOutcome;
        try {
            pollOutcome = poller.poll(AUTO_DISPLAY_POLL_INTERVAL_MS, AUTO_DISPLAY_POLLS_MAX);
        } catch (InterruptedException e) {
            throw new XvfbException("interrupted while polling for display number", e);
        }
        if (pollOutcome.reason == StopReason.RESOLVED) {
            assert pollOutcome.content != null : "poll resolved but outcome content is null";
            return pollOutcome.content;
        } else {
            throw new XvfbException("polling for display number (because of -displayfd option) did not behave as expected; poll terminated due to " + pollOutcome.reason);
        }

    }

    private static class AbortFlagSetter<T> implements FutureCallback<ProcessResult<T, T>> {

        private final DefaultXvfbController xvfbController;

        private AbortFlagSetter(DefaultXvfbController xvfbController) {
            this.xvfbController = xvfbController;
        }

        @Override
        public void onSuccess(ProcessResult<T, T> result) {
        }

        @Override
        public void onFailure(Throwable t) {
            xvfbController.setAbort(true);
        }
    }

    /**
     * Interface for classes that can check whether the display has reached a ready state.
     */
    public interface DisplayReadinessChecker {
        /**
         * Checks whether the display is ready.
         * @param display the display to check, e.g. ":123"
         * @return true iff the display is ready
         */
        boolean checkReadiness(String display);
    }

    static class LoggingCallback<T> implements FutureCallback<T> {

        private final String name;

        public LoggingCallback(String name) {
            this.name = name;
        }

        @Override
        public void onSuccess(T result) {
            if (result instanceof ProcessResult && ((ProcessResult)result).exitCode() != 0) {
                log.info("{}: {}", name, result);
            } else {
                log.debug("{}: {}", name, result);
            }
        }

        @Override
        public void onFailure(Throwable t) {
            if (t instanceof java.util.concurrent.CancellationException) {
                log.debug("{}: cancelled", name);
            } else {
                log.info("{}: {}", name, t);
            }
        }

    }

    static class DirectoryDeletingCallback<T> implements FutureCallback<ProcessResult<T, T>> {

        private final File directory;

        DirectoryDeletingCallback(File directory) {
            this.directory = checkNotNull(directory);
        }

        @Override
        public void onSuccess(@Nullable ProcessResult<T, T> result) {
            deleteDirectory();
        }

        @Override
        public void onFailure(Throwable t) {
            deleteDirectory();
        }

        protected void deleteDirectory() {
            try {
                FileUtils.deleteDirectory(directory);
            } catch (IOException e) {
                if (directory.exists()) {
                    LoggerFactory.getLogger(DirectoryDeletingCallback.class)
                            .info("failed to delete directory {}: {}", directory, e.toString());
                }
            }
        }
    }

    public ProcessTracker getProcessTracker() {
        return processTracker;
    }

    @Override
    public String toString() {
        return "XvfbManager{" +
                "xvfbExecutableSupplier=" + xvfbExecutableSupplier +
                ", xvfbConfig=" + xvfbConfig +
                ", processTracker=" + processTracker +
                '}';
    }
}
