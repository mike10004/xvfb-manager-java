package com.github.mike10004.xvfbmanager;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Screenshooter implementation that copies the framebuffer file from the
 * framebuffer directory. See the {@code -fbdir} option to {@code Xvfb}
 * in the manual.
 */
public class FramebufferDirScreenshooter implements Screenshooter<XwdFileScreenshot> {

    private final File outputDir;
    private final File framebufferDir;
    private final int screen;

    /**
     * Constructs a new instance of the class.
     * @param framebufferDir the directory containing the framebuffer file
     * @param screen the screen; see {@code -screen} option in the {@code Xvfb} manual
     * @param outputDir directory to which the output file will be written
     */
    public FramebufferDirScreenshooter(File framebufferDir, int screen, File outputDir) {
        super();
        this.outputDir = checkNotNull(outputDir);
        this.framebufferDir = checkNotNull(framebufferDir);
        this.screen = screen;
    }

    protected String constructFramebufferFilename() {
        return String.format("Xvfb_screen%d", screen);
    }

    @Override
    public XwdFileScreenshot capture() throws IOException, XvfbException {
        File outputFile = constructOutputPathname(outputDir);
        String framebufferFilename = constructFramebufferFilename();
        File framebufferFile = new File(framebufferDir, framebufferFilename);
        com.google.common.io.Files.copy(framebufferFile, outputFile);
        return XwdFileScreenshot.from(outputFile);
    }

    /**
     * Constructs the output file pathname. This implementation creates
     * a temporary file with unique name.
     * @param outputDir directory in which file will be a child
     * @return the pathname
     * @throws IOException if something goes awry
     */
    protected File constructOutputPathname(File outputDir) throws IOException {
        return File.createTempFile("screenshot", ".xwd", outputDir);
    }
}
