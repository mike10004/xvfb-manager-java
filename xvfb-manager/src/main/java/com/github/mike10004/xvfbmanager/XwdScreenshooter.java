package com.github.mike10004.xvfbmanager;

import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ProcessResult;
import io.github.mike10004.subprocess.ProcessTracker;
import io.github.mike10004.subprocess.Subprocess;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

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

    private final ProcessTracker processTracker;
    private final String display;
    private final File outputDir;

    public XwdScreenshooter(ProcessTracker processTracker, String display, File outputDir) {
        this.processTracker = requireNonNull(processTracker);
        this.display = checkNotNull(display);
        this.outputDir = checkNotNull(outputDir);
    }

    @Override
    public XwdFileScreenshot capture() throws IOException, XvfbException {
        File xwdFile = File.createTempFile("screenshot", ".xwd", outputDir);
        ProcessMonitor<String, String> xwdMonitor = Subprocess.running(PROG_XWD)
                .args("-display", display, "-root", "-silent", "-out", xwdFile.getAbsolutePath())
                .build()
                .launcher(processTracker)
                .outputStrings(Charset.defaultCharset()) // xwd presumably uses platform charset
                .launch();
        ProcessResult<String, String> xwdResult;
        try {
            xwdResult = xwdMonitor.await();
        } catch (InterruptedException e) {
            log.error("interrupted while waiting for " + PROG_XWD, e);
            ProcessKilling.termOrKill(xwdMonitor.destructor(), 100, TimeUnit.MILLISECONDS);
            throw new ScreenshooterException("failed to take screenshot due to interruption", e);
        }
        log.debug("xwd process finished: {}", xwdResult);
        String stderrText = StringUtils.abbreviate(xwdResult.content().stderr(), 512);
        if (xwdResult.exitCode() != 0) {
            throw new ScreenshooterException("xwd failed with code " + xwdResult.exitCode() + " and stderr: " + stderrText);
        }
        return XwdFileScreenshot.from(xwdFile);
    }

}
