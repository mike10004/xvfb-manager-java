/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.github.mike10004.nativehelper.Program.running;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation of a screenshooter. Uses {@code xwd} to capture a screenshot of the
 * framebuffer. The raw output file (as returned by {@link XvfbManager.Screenshot#getRawFile()}
 * is in {@code xwd} format. Use the {@code xwdtopnm} program to export it to a PNM file.
 */
public class DefaultScreenshooter implements XvfbManager.Screenshooter {

    private static final Logger log = LoggerFactory.getLogger(DefaultScreenshooter.class);

    private final String display;
    private final File outputDir;

    public DefaultScreenshooter(String display, File outputDir) {
        this.display = checkNotNull(display);
        this.outputDir = checkNotNull(outputDir);
    }

    @Override
    public XvfbManager.Screenshot capture() throws IOException, XvfbException {
        File xwdFile = File.createTempFile("screenshot", ".xwd", outputDir);
        ProgramWithOutputStringsResult xwdResult = running("xwd")
                .args("-display", display, "-root", "-silent", "-out", xwdFile.getAbsolutePath())
                .outputToStrings()
                .execute();
        log.debug("xwd process finished: {}", xwdResult);
        String stderrText = StringUtils.abbreviate(xwdResult.getStderrString(), 512);
        if (xwdResult.getExitCode() != 0) {
            throw new DefaultScreenshooterException("xwd failed with code " + xwdResult.getExitCode() + " and stderr: " + stderrText);
        }
        return new DefaultScreenshot(xwdFile);
    }

    static class DefaultScreenshooterException extends XvfbException {
        public DefaultScreenshooterException() {
        }

        public DefaultScreenshooterException(String message) {
            super(message);
        }

        public DefaultScreenshooterException(String message, Throwable cause) {
            super(message, cause);
        }

        public DefaultScreenshooterException(Throwable cause) {
            super(cause);
        }
    }

    private static class DefaultScreenshot implements XvfbManager.Screenshot {

        private final File rawFile;

        public DefaultScreenshot(File rawFile) {
            this.rawFile = checkNotNull(rawFile);
        }

        @Override
        public File getRawFile() {
            return rawFile;
        }

    }

}
