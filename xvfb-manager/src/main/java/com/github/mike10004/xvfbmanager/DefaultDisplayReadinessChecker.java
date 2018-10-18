package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.github.mike10004.nativehelper.subprocess.Subprocess.running;
import static java.util.Objects.requireNonNull;

/**
 * Class that calls an X program to gather information about a display in order
 * to determine whether the display is ready. Executes {@code xdpyinfo}, specifying
 * the {@code -display} argument, and interprets a clean exit to indicate that
 * the display is ready.
 */
public class DefaultDisplayReadinessChecker implements XvfbManager.DisplayReadinessChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultDisplayReadinessChecker.class);

    private static final String PROG_XDPYINFO = "xdpyinfo";

    private static final ImmutableSet<String> requiredPrograms = ImmutableSet.of(PROG_XDPYINFO);

    private final ProcessTracker processTracker;

    public DefaultDisplayReadinessChecker(ProcessTracker processTracker) {
        this.processTracker = requireNonNull(processTracker);
    }

    public static Iterable<String> getRequiredPrograms() {
        return requiredPrograms;
    }

    private static final int XDPYINFO_SIGTERM_TIMEOUT_MILLIS = 1000;

    /**
     * Checks display readiness. Executes {@code xdpyinfo} and returns true on
     * a clean exit code.
     * @param display the display to check, e.g. ":123"
     * @return true iff {@code xdpyinfo} exits clean
     */
    @Override
    public boolean checkReadiness(String display) {
        ProcessMonitor<String, String> monitor = running("xdpyinfo")
                .args("-display", display)
                .build()
                .launcher(processTracker)
                .outputStrings(Charset.defaultCharset()) // xdpyinfo uses platform charset, presumably
                .launch();
        ProcessResult<String, String> result;
        try {
            result = monitor.await();
        } catch (InterruptedException e) {
            log.error("interrupted while waiting for check readiness", e);
            ProcessKilling.termOrKill(monitor.destructor(), XDPYINFO_SIGTERM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return false;
        }
        executedCheckProgram(result);
        return representsReady(result, display);
    }

    protected boolean representsReady(ProcessResult<String, String> result, String requiredDisplay) {
        if (result.exitCode() != 0) {
            return false;
        }
        return isCorrectOutputForDisplay(result.content().stdout(), requiredDisplay);
    }

    protected static boolean isCorrectOutputForDisplay(String stdout, String requiredDisplay) {
        Pattern pattern = Pattern.compile("^name of display:\\s+" + Pattern.quote(requiredDisplay) + "$", Pattern.MULTILINE);
        return pattern.matcher(stdout).find();
    }

    protected void executedCheckProgram(ProcessResult<String, String> result) {
        LoggerFactory.getLogger(DefaultDisplayReadinessChecker.class).debug("xdpyinfo: {}", result);
    }
}
