/*
 * (c) 2016 Mike Chaberski
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputFiles;
import com.github.mike10004.xvfbmanager.Screenshot.FileByteSource;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Screenshot implementation that uses an X utility to convert a screenshot
 * file to portable anymap format. Uses {@code xwdtopnm} to convert to PNM.
 */
public class XwdFileToPngConverter implements ScreenshotConverter<Screenshot, ImageioReadableScreenshot> {

    private static final Logger log = LoggerFactory.getLogger(XwdFileToPngConverter.class);

    private final Path tempDir;

    public XwdFileToPngConverter(Path tempDir) {
        this.tempDir = checkNotNull(tempDir);
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
        ByteSource inputSource = source.getRawFile();
        if (inputSource instanceof FileByteSource) {
            inputFile = ((FileByteSource)inputSource).file;
            deleteInputFile = false;
        } else {
            inputFile = File.createTempFile("xwdtopnm-stdin", ".xwd", tempDir.toFile());
            deleteInputFile = true;
        }
        try {
            return convert(source, pnmFile, stderrFile, inputFile);
        } finally {
            if (deleteInputFile) {
                if (!inputFile.delete()) {
                    log.info("failed to delete {}", inputFile);
                }
            }
        }
    }

    public ImageioReadableScreenshot convert(Screenshot source, File pnmFile, File stderrFile, File inputFile) throws IOException, XvfbException {
        ProgramWithOutputFiles xwdtopnm = Program.running("xwdtopnm")
                .args("-")
                .reading(inputFile)
                .outputToFiles(pnmFile, stderrFile);
        ProgramResult result = xwdtopnm.execute();
        log.debug("xwdtopnm: {}", result);
        if (result.getExitCode() != 0) {
            String stderrText = com.google.common.io.Files.toString(stderrFile, Charset.defaultCharset());
            throw new IOException("xwdtopnm failed with exit code " + result.getExitCode() + ": " + StringUtils.abbreviate(stderrText, 256));
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
}
