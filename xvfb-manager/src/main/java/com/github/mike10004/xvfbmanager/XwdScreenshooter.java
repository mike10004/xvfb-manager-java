/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implementation of a screenshooter that executes an X utility.
 * Uses {@code xwd} to capture a screenshot of the
 * framebuffer. The raw output file (as returned by {@link Screenshot#asByteSource()}
 * is in {@code xwd} format. Use the {@code xwdtopnm} program to export it to a PNM file.
 */
public class XwdScreenshooter implements Screenshooter<XwdFileScreenshot> {

    private static final String PROG_XWD = "xwd";

    private static final ImmutableSet<String> requiredPrograms = ImmutableSet.of(PROG_XWD);

    public static Iterable<String> getRequiredPrograms() {
        return requiredPrograms;
    }

    private static final Logger log = LoggerFactory.getLogger(XwdScreenshooter.class);

    private final String display;
    private final File outputDir;

    public XwdScreenshooter(String display, File outputDir) {
        this.display = checkNotNull(display);
        this.outputDir = checkNotNull(outputDir);
    }

    @Override
    public XwdFileScreenshot capture() throws IOException, XvfbException {
        File xwdFile = File.createTempFile("screenshot", ".xwd", outputDir);
        ProgramWithOutputStringsResult xwdResult = Program.running(PROG_XWD)
                .args("-display", display, "-root", "-silent", "-out", xwdFile.getAbsolutePath())
                .outputToStrings()
                .execute();
        log.debug("xwd process finished: {}", xwdResult);
        String stderrText = StringUtils.abbreviate(xwdResult.getStderrString(), 512);
        if (xwdResult.getExitCode() != 0) {
            throw new ScreenshooterException("xwd failed with code " + xwdResult.getExitCode() + " and stderr: " + stderrText);
        }
        return XwdFileScreenshot.from(xwdFile);
    }

}
