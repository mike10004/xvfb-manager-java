/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultXvfbController implements XvfbController {

    public static final long DEFAULT_POLL_INTERVAL_MS = 250;
    public static final int DEFAULT_MAX_NUM_POLLS = 8;

    private final ListenableFuture<ProgramWithOutputStringsResult> xvfbFuture;
    private final String display;
    private final XvfbManager.DisplayReadinessChecker displayReadinessChecker;
    private final XvfbManager.Screenshooter screenshooter;
    private final Sleeper sleeper;
    private final AtomicBoolean abort;

    public DefaultXvfbController(ListenableFuture<ProgramWithOutputStringsResult> xvfbFuture, String display, XvfbManager.DisplayReadinessChecker displayReadinessChecker, XvfbManager.Screenshooter screenshooter, Sleeper sleeper) {
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
        boolean ready = new Poller(sleeper) {
            @Override
            protected boolean check(int pollAttemptsSoFar) {
                return displayReadinessChecker.checkReadiness(display);
            }

            @Override
            protected boolean isAborted() {
                return checkAbort();
            }
        }.poll(pollIntervalMs, maxNumPolls);
        if (!ready) {
            throw new XvfbException("display never became ready");
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
     * @throws Exception
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
            this.screenshooter = new DefaultScreenshooter(display, FileUtils.getTempDirectory());
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

        public DefaultXvfbController build(ListenableFuture<ProgramWithOutputStringsResult> xvfbFuture) {
            return new DefaultXvfbController(xvfbFuture, display, checker, screenshooter, sleeper);
        }
    }
}
