/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputFiles;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractScreenshooter implements XvfbManager.Screenshooter {

    private static final Logger log = LoggerFactory.getLogger(AbstractScreenshooter.class);

    protected final String display;
    protected final File outputDir;

    public AbstractScreenshooter(String display, File outputDir) {
        this.display = checkNotNull(display);
        this.outputDir = checkNotNull(outputDir);
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

    protected static class DefaultScreenshot implements XvfbManager.Screenshot {

        private final File rawFile;
        private final Path tempDir;

        public DefaultScreenshot(File rawFile, Path tempDir) {
            this.rawFile = checkNotNull(rawFile);
            this.tempDir = checkNotNull(tempDir);
        }

        @Override
        public File getRawFile() {
            return rawFile;
        }

        @Override
        public void convertToPnmFile(File pnmFile) throws IOException {
            File stderrFile = File.createTempFile("xwdtopnm-stderr", ".txt", tempDir.toFile());
            try {
                ProgramWithOutputFiles xwdtopnm = Program.running("xwdtopnm")
                        .args(getRawFile().getAbsolutePath())
                        .outputToFiles(pnmFile, stderrFile);
                ProgramResult result = xwdtopnm.execute();
                log.debug("xwdtopnm: {}", result);
                if (result.getExitCode() != 0) {
                    String stderrText = com.google.common.io.Files.toString(stderrFile, Charset.defaultCharset());
                    throw new IOException("xwdtopnm failed with exit code " + result.getExitCode() + ": " + StringUtils.abbreviate(stderrText, 256));
                }
            } finally {
                if (!stderrFile.delete()) {
                    log.info("failed to delete {}", stderrFile);
                }
            }
        }
    }

}
