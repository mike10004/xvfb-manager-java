/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class FramebufferDirScreenshooter extends AbstractScreenshooter {

    private final File framebufferDir;
    private final int screen;

    public FramebufferDirScreenshooter(String display, File framebufferDir, int screen, File outputDir) {
        super(display, outputDir);
        this.framebufferDir = checkNotNull(framebufferDir);
        this.screen = screen;
    }

    protected String constructFramebufferFilename() {
        return String.format("Xvfb_screen%d", screen);
    }

    @Override
    public XvfbManager.Screenshot capture() throws IOException, XvfbException {
        File outputFile = File.createTempFile("screenshot", ".xwd", outputDir);
        String framebufferFilename = constructFramebufferFilename();
        File framebufferFile = new File(framebufferDir, framebufferFilename);
        com.google.common.io.Files.copy(framebufferFile, outputFile);
        return new DefaultScreenshot(outputFile, outputDir.toPath());
    }
}
