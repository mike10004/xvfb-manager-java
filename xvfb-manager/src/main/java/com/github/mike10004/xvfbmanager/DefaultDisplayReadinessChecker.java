/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.collect.ImmutableSet;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static com.github.mike10004.nativehelper.Program.running;

/**
 * Class that calls an X program to gather information about a display in order
 * to determine whether the display is ready. Executes {@code xdpyinfo}, specifying
 * the {@code -display} argument, and interprets a clean exit to indicate that
 * the display is ready.
 */
public class DefaultDisplayReadinessChecker implements XvfbManager.DisplayReadinessChecker {

    private static final String PROG_XDPYINFO = "xdpyinfo";

    private static final ImmutableSet<String> requiredPrograms = ImmutableSet.of(PROG_XDPYINFO);

    public static Iterable<String> getRequiredPrograms() {
        return requiredPrograms;
    }

    /**
     * Checks display readiness. Executes {@code xdpyinfo} and returns true on
     * a clean exit code.
     * @param display the display to check, e.g. ":123"
     * @return true iff {@code xdpyinfo} exits clean
     */
    @Override
    public boolean checkReadiness(String display) {
        ProgramWithOutputStringsResult result = running("xdpyinfo")
                .args("-display", display)
                .outputToStrings()
                .execute();
        executedCheckProgram(result);
        return representsReady(result, display);
    }

    protected boolean representsReady(ProgramWithOutputStringsResult result, String requiredDisplay) {
        if (result.getExitCode() != 0) {
            return false;
        }
        return isCorrectOutputForDisplay(result.getStdoutString(), requiredDisplay);
    }

    protected static boolean isCorrectOutputForDisplay(String stdout, String requiredDisplay) {
        Pattern pattern = Pattern.compile("^name of display:\\s+" + Pattern.quote(requiredDisplay) + "$", Pattern.MULTILINE);
        return pattern.matcher(stdout).find();
    }

    protected void executedCheckProgram(ProgramWithOutputStringsResult result) {
        LoggerFactory.getLogger(DefaultDisplayReadinessChecker.class).debug("xdpyinfo: {}", result);
    }
}
