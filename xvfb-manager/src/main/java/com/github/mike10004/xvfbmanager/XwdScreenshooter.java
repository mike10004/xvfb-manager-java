/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of a screenshooter that executes an X utility.
 * Uses {@code xwd} to capture a screenshot of the
 * framebuffer. The raw output file (as returned by {@link XvfbManager.Screenshot#getRawFile()}
 * is in {@code xwd} format. Use the {@code xwdtopnm} program to export it to a PNM file.
 */
public class XwdScreenshooter extends AbstractScreenshooter {

    private static final Logger log = LoggerFactory.getLogger(XwdScreenshooter.class);

    public XwdScreenshooter(String display, File outputDir) {
        super(display, outputDir);
    }

    @Override
    public XvfbManager.Screenshot capture() throws IOException, XvfbException {
        File xwdFile = File.createTempFile("screenshot", ".xwd", outputDir);
        ProgramWithOutputStringsResult xwdResult = Program.running("xwd")
                .args("-display", display, "-root", "-silent", "-out", xwdFile.getAbsolutePath())
                .outputToStrings()
                .execute();
        log.debug("xwd process finished: {}", xwdResult);
        String stderrText = StringUtils.abbreviate(xwdResult.getStderrString(), 512);
        if (xwdResult.getExitCode() != 0) {
            throw new DefaultScreenshooterException("xwd failed with code " + xwdResult.getExitCode() + " and stderr: " + stderrText);
        }
        return new DefaultScreenshot(xwdFile, outputDir.toPath());
    }

}
