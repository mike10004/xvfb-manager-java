/*
 * (c) 2016 Mike Chaberski
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.github.mike10004.xvfbmanager.Screenshot.FileByteSource;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Screenshot implementation that uses an X utility to convert a screenshot
 * file to portable anymap format. Uses {@code xwdtopnm} to convert to PNM.
 */
public class XwdFileToPngConverter implements ScreenshotConverter<Screenshot, ImageioReadableScreenshot> {

    private static final Logger log = LoggerFactory.getLogger(XwdFileToPngConverter.class);

    private static final String PROG_XWDTOPNM = "xwdtopnm";

    private static final ImmutableSet<String> requiredPrograms = ImmutableSet.of(PROG_XWDTOPNM);

    private final ProcessTracker processTracker;
    private final Path tempDir;

    public XwdFileToPngConverter(ProcessTracker processTracker, Path tempDir) {
        this.tempDir = checkNotNull(tempDir);
        this.processTracker = requireNonNull(processTracker);
    }

    @Override
    public ImageioReadableScreenshot convert(Screenshot source) throws IOException, XvfbException {
        File pnmFile = File.createTempFile("xwdtopnm-stdout", ".ppm", tempDir.toFile());
        try {
            return convert(source, pnmFile);
        } finally {
            if (!pnmFile.delete()) {
                log.info("failed to delete {}", pnmFile);
            }
        }
    }

    protected ImageioReadableScreenshot convert(Screenshot source, File pnmFile) throws IOException, XvfbException {
        File stderrFile = File.createTempFile("xwdtopnm-stderr", ".txt", tempDir.toFile());
        try {
            return convert(source, pnmFile, stderrFile);
        } finally {
            if (!stderrFile.delete()) {
                log.info("failed to delete {}", stderrFile);
            }
        }
    }

    public ImageioReadableScreenshot convert(Screenshot source, File pnmFile, File stderrFile) throws IOException, XvfbException {
        final File inputFile;
        final boolean deleteInputFile;
        ByteSource inputSource = source.asByteSource();
        if (inputSource instanceof FileByteSource) {
            inputFile = ((FileByteSource)inputSource).file;
            deleteInputFile = false;
        } else {
            inputFile = File.createTempFile("xwdtopnm-stdin", ".xwd", tempDir.toFile());
            inputSource.copyTo(Files.asByteSink(inputFile));
            deleteInputFile = true;
        }
        try {
            return convert(pnmFile, stderrFile, inputFile);
        } finally {
            if (deleteInputFile) {
                if (!inputFile.delete()) {
                    log.info("failed to delete {}", inputFile);
                }
            }
        }
    }

    protected ImageioReadableScreenshot convert(File pnmFile, File stderrFile, File inputFile) throws IOException, XvfbException {
        if (!inputFile.isFile()) {
            throw new FileNotFoundException(inputFile.getAbsolutePath());
        }
        if (inputFile.length() <= 0) {
            throw new IOException("input file is empty: " + inputFile);
        }
        ProcessMonitor<File, File> xwdtopnm = Subprocess.running(PROG_XWDTOPNM)
                .args("-")
                .build()
                .launcher(processTracker)
                .outputFiles(pnmFile, stderrFile, Files.asByteSource(inputFile))
                .launch();
        ProcessResult<File, File> result = null;
        try {
            result = xwdtopnm.await();
        } catch (InterruptedException e) {
            ProcessKilling.termOrKill(xwdtopnm.destructor(), 250, TimeUnit.MILLISECONDS);
            throw new IOException("interrupted while waiting for xwdtopnm to finish", e);
        }
        log.debug("xwdtopnm: {}", result);
        if (result.exitCode() != 0) {
            String stderrText = Files.asCharSource(stderrFile, Charset.defaultCharset()).read();
            throw new IOException("xwdtopnm failed with exit code " + result.exitCode() + ": " + StringUtils.abbreviate(stderrText, 256));
        }
        byte[] pngBytes = convertPnmToPng(Files.asByteSource(pnmFile));
        return new ImageioReadableScreenshot(ByteSource.wrap(pngBytes));
    }

    protected byte[] convertPnmToPng(ByteSource pnmBytes) throws IOException {
        try (InputStream in = pnmBytes.openStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("no PNM image reader is registered");
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream(2048)) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
        }
    }

    public static Iterable<String> getRequiredPrograms() {
        return requiredPrograms;
    }

}
