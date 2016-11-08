/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputStrings;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.novetta.ibg.common.sys.Whicher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
     * Constructs an instance of the class that will launch the given executable
     * with the given configuration.
     * @param xvfbExecutable pathname of the {@code Xvfb} executable
     * @param xvfbConfig virtual framebuffer configuration
     */
    public XvfbManager(File xvfbExecutable, XvfbConfig xvfbConfig) {
        this.xvfbExecutable = checkNotNull(xvfbExecutable);
        this.xvfbConfig = checkNotNull(xvfbConfig);
    }

    protected static ExecutorService createDefaultExecutorService() {
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
        return Sleeper.DEFAULT;
    }

    protected DisplayReadinessChecker createDisplayReadinessChecker(String display, File framebufferDir) {
        return new DefaultDisplayReadinessChecker();
    }

    protected DefaultXvfbController.Builder createControllerBuilder(String display, File framebufferDir) {
        return DefaultXvfbController.builder(display)
                .withReadinessChecker(createDisplayReadinessChecker(display, framebufferDir))
                .withScreenshooter(createScreenshooter(display, framebufferDir))
                .withSleeper(createSleeper());
    }

    public XvfbController start(int displayNumber, File framebufferDir) {
        return start(displayNumber, framebufferDir, createDefaultExecutorService());
    }

    /**
     * Starts Xvfb on a given display number, rendering to a file in a given directory.
     * See {@code Xvfb} man page regarding {@code -fbdir} option for information on the
     * framebuffer directory contents.
     * @param displayNumber the display number; can be any positive integer not already in use
     * @param framebufferDir the framebuffer directory
     * @return a controller for the process
     */
    public XvfbController start(int displayNumber, File framebufferDir, ExecutorService executorService) {
        String display = toDisplayValue(displayNumber);
        ProgramWithOutputStrings xvfb = Program.running(xvfbExecutable)
                .args(display)
                .args("-screen", String.valueOf(SCREEN), xvfbConfig.geometry)
                .args("-fbdir", framebufferDir.getAbsolutePath())
                .outputToStrings();
        log.trace("executing {}", xvfb);
        ListenableFuture<ProgramWithOutputStringsResult> xvfbFuture = xvfb.executeAsync(executorService);
        Futures.addCallback(xvfbFuture, new LoggingCallback("xvfb") {
            @Override
            public void onSuccess(ProgramResult result) {
                if (result.getExitCode() != 0) {
                    log.info("Xvfb failure: {}", result);
                } else {
                    super.onSuccess(result);
                }
            }
        });
        DefaultXvfbController controller = createControllerBuilder(display, framebufferDir).build(xvfbFuture);
        Futures.addCallback(xvfbFuture, new WaitableFlagSetter(controller));
        return controller;
    }

    private static class WaitableFlagSetter implements FutureCallback<ProgramWithOutputStringsResult> {

        private final DefaultXvfbController xvfbController;

        private WaitableFlagSetter(DefaultXvfbController xvfbController) {
            this.xvfbController = xvfbController;
        }

        @Override
        public void onSuccess(@Nullable ProgramWithOutputStringsResult result) {
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

    /**
     * Interface for a class that can capture a screenshot of a virtual framebuffer.
     */
    public interface Screenshooter {
        Screenshot capture() throws IOException, XvfbException;
    }

    static class LoggingCallback implements FutureCallback<ProgramResult> {

        private final String name;

        public LoggingCallback(String name) {
            this.name = name;
        }

        @Override
        public void onSuccess(ProgramResult result) {
            log.debug("{}: {}", name, result);
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

    static class DefaultSleeper implements Sleeper {

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }
}
