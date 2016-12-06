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
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

public class PollingXLockFileChecker implements DefaultXvfbController.XLockFileChecker {

    private final long pollIntervalMs;
    private final Sleeper sleeper;
    private final Function<String, String> environment;

    public PollingXLockFileChecker(long pollIntervalMs, Sleeper sleeper) {
        this(pollIntervalMs, sleeper, systemEnvironment);
    }

    private static final Function<String, String> systemEnvironment = new Function<String, String>() {
        @Override
        public @Nullable String apply(String name) {
            return System.getenv(name);
        }
    };

    @VisibleForTesting
    PollingXLockFileChecker(long pollIntervalMs, Sleeper sleeper, Function<String, String> environment) {
        this.pollIntervalMs = pollIntervalMs;
        this.sleeper = sleeper;
        this.environment = environment;
    }

    protected File constructLockFilePathname(String display) throws IOException {
        checkArgument(display.matches(":\\d+"), "invalid display: '%s' (expected format ':N')", display);
        int displayNum = Integer.parseInt(display.substring(1));
        String filename = constructLockFilename(displayNum);
        File parent = getXLockFileParentPath();
        return new File(parent, filename);
    }

    private static final String[] tmpdirEnvironmentVariableNames = {"TMP", "TMPDIR", "TEMP"};

    /**
     * Tries to return the host temporary directory in which the X lock file would be stored.
     * This may be different from the value of system property {@code java.io.tmpdir}.
     * @return the directory pathname
     * @throws FileNotFoundException on unexpected state
     */
    protected File getXLockFileParentPath() throws FileNotFoundException {
        String path = "/tmp";
        for (String envVarName : tmpdirEnvironmentVariableNames) {
            String envVarValue = environment.apply(envVarName);
            if (envVarValue != null) {
                return new File(envVarValue);
            }
        }
        File dir = new File(path);
        return dir.isDirectory() ? dir : FileUtils.getTempDirectory();
    }

    protected String constructLockFilename(int displayNum) {
        return ".X" + displayNum + "-lock";
    }

    @Override
    public void waitForCleanup(String display, long timeoutMs) throws LockFileCheckingException {
        File lockFile;
        try {
            lockFile = constructLockFilePathname(display);
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
