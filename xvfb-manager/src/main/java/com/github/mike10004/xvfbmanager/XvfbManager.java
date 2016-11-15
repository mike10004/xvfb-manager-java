/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputFiles;
import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.github.mike10004.nativehelper.ProgramWithOutputResult;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.DefaultXvfbController.Screenshooter;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.novetta.ibg.common.sys.Whicher;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Class that helps manage the creation of virtual framebuffer processes.
 */
public class XvfbManager {

    private static final Logger log = LoggerFactory.getLogger(XvfbManager.class);
    private static final int SCREEN = 0;

    private final File xvfbExecutable;
    private final XvfbConfig xvfbConfig;

    /**
     * Constructs a default instance of the class.
     * @throws IOException if Xvfb executable cannot be resolved
     * @see #resolveXvfbExecutable()
     */
    public XvfbManager() throws IOException {
        this(resolveXvfbExecutable(), XvfbConfig.DEFAULT);
    }

    /**
     * Constructs an instance of the class with a given configuration.
     * @param xvfbConfig the configuration
     * @throws IOException if Xvfb executable cannot be resolved
     */
    public XvfbManager(XvfbConfig xvfbConfig) throws IOException {
        this(resolveXvfbExecutable(), xvfbConfig);
    }

    /**
     * Constructs an instance of the class that will launch the given executable
     * with the given configuration.
     * @param xvfbExecutable pathname of the {@code Xvfb} executable
     * @param xvfbConfig virtual framebuffer configuration
     */
    public XvfbManager(File xvfbExecutable, XvfbConfig xvfbConfig) {
        this.xvfbExecutable = checkNotNull(xvfbExecutable);
        this.xvfbConfig = checkNotNull(xvfbConfig);
    }

    /**
     * Creates an executor service with a 2-thread pool and thread factory that creates daemon threads.
     * @return the executor service
     */
    public static ExecutorService createDefaultExecutorService() {
        return Executors.newFixedThreadPool(2, new ThreadFactory() {

            private ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultThreadFactory.newThread(r);
                t.setDaemon(true);
                return t;
            }
        });
    }

    protected static String toDisplayValue(int displayNumber) {
        checkArgument(displayNumber >= 0, "displayNumber must be nonnegative");
        return String.format(":%d", displayNumber);
    }

    protected static File resolveXvfbExecutable() throws FileNotFoundException {
        Optional<File> file = Whicher.gnu().which("Xvfb");
        if (!file.isPresent()) {
            throw new FileNotFoundException("Xvfb executable");
        }
        return file.get();
    }

    protected Screenshooter createScreenshooter(String display, File framebufferDir) {
        return new FramebufferDirScreenshooter(display, framebufferDir, SCREEN, framebufferDir);
    }

    protected Sleeper createSleeper() {
        return Sleeper.DefaultSleeper.getInstance();
    }

    protected DisplayReadinessChecker createDisplayReadinessChecker(String display, File framebufferDir) {
        return new DefaultDisplayReadinessChecker();
    }

    protected DefaultXvfbController createController(ListenableFuture<? extends ProgramWithOutputResult> future, String display, File framebufferDir) {
        return new DefaultXvfbController(future, display, createDisplayReadinessChecker(display, framebufferDir), createScreenshooter(display, framebufferDir), createSleeper());
    }

    /**
     * Starts Xvfb on a vacant display. A directory for temp files will be created and deleted
     * when the process is stopped.
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start() throws IOException {
        return start(createDefaultExecutorService());
    }

    /**
     * Starts Xvfb on the specified display number. A directory for temp files will be created and deleted
     * when the process is stopped.
     * @param displayNumber the display number
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(int displayNumber) throws IOException {
        return start(displayNumber, createDefaultExecutorService());
    }

    /**
     * Starts Xvfb on a vacant display, writing temp files to the specified directory.
     * @param scratchDir the temp directory
     * @return a controller for the process
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(Path scratchDir) throws IOException {
        return start(scratchDir, createDefaultExecutorService());
    }

    /**
     * Starts Xvfb on the specified display and writes temp files to the specified directory.
     * @param displayNumber the display number
     * @param scratchDir the temp directory
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(int displayNumber, Path scratchDir) throws IOException {
        return start(displayNumber, scratchDir, createDefaultExecutorService());
    }

    /**
     * Starts Xvfb on the specified display using the specified executor service, writing temp
     * files to the specified directory.
     * @param displayNumber the display number
     * @param scratchDir the temp directory
     * @param executorService the executor service
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(int displayNumber, Path scratchDir, ExecutorService executorService) throws IOException {
        return doStart(displayNumber, nonDeletingExistingDirectoryProvider(scratchDir), executorService);
    }

    /**
     * Starts Xvfb on the specified display using the specified executor service. A directory for temp files
     * is created and deleted when the process is stopped.
     * @param displayNumber the display number
     * @param executorService the executor service
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(int displayNumber, ExecutorService executorService) throws IOException {
        return doStart(displayNumber, newTempDirProvider(FileUtils.getTempDirectory().toPath()), executorService);
    }

    /**
     * Starts Xvfb on a vacant display using the specified executor service. A directory for temp files
     * is created and deleted when the process is stopped.
     * @param executorService the executor service
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(ExecutorService executorService) throws IOException {
        return doStart(null, newTempDirProvider(FileUtils.getTempDirectory().toPath()), executorService);
    }

    /**
     * Starts Xvfb on a vacant display using the specified executor service and writing temp files
     * to the given directory.
     * @param scratchDir the temp directory
     * @param executorService the executor service
     * @return the process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    public XvfbController start(Path scratchDir, ExecutorService executorService) throws IOException {
        return doStart(null, nonDeletingExistingDirectoryProvider(scratchDir), executorService);
    }

    /**
     * Starts Xvfb, maybe auto-selecting a display number.
     * @param displayNumber display number, or null to auto-select
     * @param scratchDirProvider provider of scratch directory
     * @param executorService executor service
     * @return process controller
     * @throws IOException if the files and directories the process requires cannot be created or written to
     */
    private XvfbController doStart(final @Nullable Integer displayNumber, ScratchDirProvider scratchDirProvider, ExecutorService executorService) throws IOException {
        String display = null;
        final boolean AUTO_DISPLAY = displayNumber == null;
        Program.Builder pb = Program.running(xvfbExecutable);
        if (AUTO_DISPLAY) {
            pb.args("-displayfd", String.valueOf(extractFdReflectively(DISPLAY_RECEIVER)));
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
        ProgramWithOutputFiles xvfb = pb.outputToFiles(stdoutFile, stderrFile);
        log.trace("executing {}", xvfb);
        ListenableFuture<ProgramWithOutputFilesResult> xvfbFuture = xvfb.executeAsync(executorService);
        Futures.addCallback(xvfbFuture, new LoggingCallback("xvfb", XVFB_OUTPUT_CHARSET));
        if (scratchDirProvider.isDeleteOnStop()) {
            Futures.addCallback(xvfbFuture, new DirectoryDeletingCallback(scratchDir.toFile()));
        }
        if (AUTO_DISPLAY) {
            File outputFileContainingDisplay = selectCorrespondingFile(DISPLAY_RECEIVER, stdoutFile, stderrFile);
            int autoDisplayNumber = pollForDisplayNumber(Files.asCharSource(outputFileContainingDisplay, XVFB_OUTPUT_CHARSET));
            display = toDisplayValue(autoDisplayNumber);
        } else {
            checkState(display != null, "display should have been set manually from %s", displayNumber);
        }
        DefaultXvfbController controller = createController(xvfbFuture, display, framebufferDir.toFile());
        Futures.addCallback(xvfbFuture, new AbortFlagSetter(controller));
        return controller;
    }

    private static final FileDescriptor DISPLAY_RECEIVER = FileDescriptor.err;

    private static final Charset XVFB_OUTPUT_CHARSET = Charset.defaultCharset(); // xvfb is platform-dependent

    protected static File selectCorrespondingFile(FileDescriptor fd, File stdoutFile, File stderrFile) throws IllegalArgumentException {
        if (fd.equals(FileDescriptor.out)) {
            return stdoutFile;
        } else if (fd.equals(FileDescriptor.err)) {
            return stderrFile;
        } else {
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
            public Path provideDirectory() throws IOException {
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

    private static class SingleFieldInclusionStrategy implements ExclusionStrategy {

        private final String fieldName;

        private SingleFieldInclusionStrategy(String fieldName) {
            this.fieldName = checkNotNull(fieldName);
        }

        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return !fieldName.equals(f.getName());
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }

    private static final String FILE_DESCRIPTOR_FIELD_NAME = "fd";

    static int extractFdReflectively(FileDescriptor fdObject) {
        JsonElement json = new GsonBuilder()
                .addSerializationExclusionStrategy(new SingleFieldInclusionStrategy(FILE_DESCRIPTOR_FIELD_NAME))
                .create().toJsonTree(fdObject);
        int fd = json.getAsJsonObject().get(FILE_DESCRIPTOR_FIELD_NAME).getAsInt();
        return fd;
    }

    private static class AbortFlagSetter implements FutureCallback<ProgramResult> {

        private final DefaultXvfbController xvfbController;

        private AbortFlagSetter(DefaultXvfbController xvfbController) {
            this.xvfbController = xvfbController;
        }

        @Override
        public void onSuccess(ProgramResult result) {
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

    /**
     * Interface for screenshots of the current framebuffer.
     */
    public interface Screenshot {
        /**
         * Gets the pathname of a file containing the screenshot.
         * @return the file
         */
        File getRawFile();

        void convertToPnmFile(File pnmFile) throws IOException;
    }

    static class LoggingCallback implements FutureCallback<ProgramWithOutputResult> {

        private final String name;
        private final Charset contentCharset;

        public LoggingCallback(String name, Charset contentCharset) {
            this.name = name;
            this.contentCharset = checkNotNull(contentCharset);
        }

        @Override
        public void onSuccess(ProgramWithOutputResult result) {
            if (result.getExitCode() != 0) {
                log.info("{}: {}", summarize(result, contentCharset));
            } else {
                log.debug("{}: {}", name, summarize(result, contentCharset));
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

        private String summarize(ProgramWithOutputResult result, Charset charset) {
            String stdout = "", stderr = "";
            try {
                stdout = result.getStdout().asCharSource(charset).read();
            } catch (IOException e) {
                log.info("failed to summarize stdout from {}" + result);
            }
            try {
                stderr = result.getStderr().asCharSource(charset).read();
            } catch (IOException e) {
                log.info("failed to summarize stderr from {}" + result);
            }
            int abbreviatedLength = 128;
            stdout = StringUtils.abbreviate(stdout, abbreviatedLength);
            stderr = StringUtils.abbreviate(stderr, abbreviatedLength);
            return String.format("%s: exit %d, stdout=\"%s\", stderr=\"%s\"", name, result.getExitCode(),
                    StringEscapeUtils.escapeJava(stdout), StringEscapeUtils.escapeJava(stderr));
        }

    }

    static class DirectoryDeletingCallback implements FutureCallback<ProgramResult> {

        private final File directory;

        DirectoryDeletingCallback(File directory) {
            this.directory = checkNotNull(directory);
        }

        @Override
        public void onSuccess(@Nullable ProgramResult result) {
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
                            .info("failed to delete directory {}: {}", directory, e);
                }
            }
        }
    }
}
