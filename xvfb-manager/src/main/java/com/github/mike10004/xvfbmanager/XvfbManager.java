/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.*;
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

import static com.github.mike10004.nativehelper.Program.running;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class XvfbManager {

    private static final Logger log = LoggerFactory.getLogger(XvfbManager.class);

    private final ExecutorService executorService;
    private final File xvfbExecutable;
    private final XvfbConfig xvfbConfig;

    public XvfbManager(ExecutorService executorService, File xvfbExecutable) {
        this(executorService, xvfbExecutable, XvfbConfig.DEFAULT);
    }

    /**
     * Constructs a default instance of the class.
     * @throws IOException if Xvfb executable cannot be resolved
     * @see #createDefaultExecutorService()
     */
    public XvfbManager() throws IOException {
        this(createDefaultExecutorService(), resolveXvfbExecutable());
    }

    public XvfbManager(ExecutorService executorService, File xvfbExecutable, XvfbConfig xvfbConfig) {
        this.executorService = checkNotNull(executorService);
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

    public static class XvfbConfig {
        public final String geometry;

        public XvfbConfig(String geometry) {
            this.geometry = checkNotNull(geometry);
            checkArgument(geometry.matches("\\d+x\\d+x\\d+(?:\\+32)?"), "argument must have form WxHxD where W=width, H=height, and D=depth; default is 1280x1024x24+32");
        }

        @Override
        public String toString() {
            return "XvfbConfig{" +
                    "geometry='" + geometry + '\'' +
                    '}';
        }

        public static final XvfbConfig DEFAULT = new XvfbConfig("1280x1024x24+32");
    }

    protected static File resolveXvfbExecutable() throws FileNotFoundException {
        Optional<File> file = Whicher.gnu().which("Xvfb");
        if (!file.isPresent()) {
            throw new FileNotFoundException("Xvfb executable");
        }
        return file.get();
    }

    protected Screenshooter createScreenshooter(String display, File framebufferDir) {
        return new DefaultScreenshooter(display, framebufferDir);
    }

    protected Sleeper createSleeper() {
        return Sleeper.DEFAULT;
    }

    protected DisplayReadinessChecker createDisplayReadinessChecker(String display, File framebufferDir) {
        return new DefaultDisplayReadinessChecker();
    }

    public DefaultXvfbController.Builder createControllerBuilder(String display, File framebufferDir) {
        return DefaultXvfbController.builder(display)
                .withReadinessChecker(createDisplayReadinessChecker(display, framebufferDir))
                .withScreenshooter(createScreenshooter(display, framebufferDir))
                .withSleeper(createSleeper());
    }

    public XvfbController start(int displayNumber, File framebufferDir) {
        String display = toDisplayValue(displayNumber);
        ProgramWithOutputStrings xvfb = Program.running(xvfbExecutable)
                .args(display)
                .args("-screen", "0", xvfbConfig.geometry)
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

    public interface DisplayReadinessChecker {
        boolean checkReadiness(String display);
    }

    public interface Screenshot {
        File getRawFile();
    }

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
