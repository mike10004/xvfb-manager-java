/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import org.slf4j.LoggerFactory;

import static com.github.mike10004.nativehelper.Program.running;

/**
 * Class that calls an X program to gather information about a display in order
 * to determine whether the display is ready. Executes {@code xdpyinfo}, specifying
 * the {@code -display} argument, and interprets a clean exit to indicate that
 * the display is ready.
 */
public class DefaultDisplayReadinessChecker implements XvfbManager.DisplayReadinessChecker {

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
        return result.getExitCode() == 0;
    }

    protected void executedCheckProgram(ProgramWithOutputStringsResult result) {
        LoggerFactory.getLogger(DefaultDisplayReadinessChecker.class).debug("xdpyinfo: {}", result);
    }
}
