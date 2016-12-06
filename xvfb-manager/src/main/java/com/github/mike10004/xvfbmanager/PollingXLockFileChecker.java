/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class PollingXLockFileChecker implements DefaultXvfbController.XLockFileChecker {

    private final long pollIntervalMs;
    private final Sleeper sleeper;
    private final XLockFileUtility lockFileUtility;

    public PollingXLockFileChecker(long pollIntervalMs, Sleeper sleeper) {
        this(pollIntervalMs, sleeper, XLockFileUtility.getInstance());
    }

    @VisibleForTesting
    PollingXLockFileChecker(long pollIntervalMs, Sleeper sleeper, XLockFileUtility lockFileUtility) {
        this.pollIntervalMs = pollIntervalMs;
        this.sleeper = sleeper;
        this.lockFileUtility = checkNotNull(lockFileUtility);
    }

    @Override
    public void waitForCleanup(String display, long timeoutMs) throws LockFileCheckingException {
        File lockFile;
        try {
            lockFile = lockFileUtility.constructLockFilePathname(display);
        } catch (IOException e) {
            throw new LockFileCheckingException(e);
        }
        int maxNumPolls = Ints.checkedCast(Math.round(Math.ceil((float) timeoutMs / (float) pollIntervalMs)));
        long startTime = System.currentTimeMillis();
        PollOutcome<?> pollOutcome;
        try {
            pollOutcome = new Poller<Void>(sleeper) {
                @Override
                protected PollAnswer<Void> check(int pollAttemptsSoFar) {
                    long now = System.currentTimeMillis();
                    if (now - startTime > timeoutMs) {
                        return abortPolling();
                    }
                    return lockFile.exists() ? continuePolling() : resolve(null);
                }
            }.poll(pollIntervalMs, maxNumPolls);
        } catch (InterruptedException e) {
            throw new LockFileCheckingException(e);
        }
        if (pollOutcome.reason == StopReason.ABORTED || pollOutcome.reason == StopReason.TIMEOUT) {
            throw new LockFileCleanupTimeoutException(String.format("%s after %s (%d attempts)", pollOutcome.reason, pollOutcome.duration, pollOutcome.getNumAttempts()));
        }
    }
}
