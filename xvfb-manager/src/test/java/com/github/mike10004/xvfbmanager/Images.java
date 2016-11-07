/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.ProgramWithOutputFilesResult;
import com.google.common.annotations.Beta;
import com.google.common.base.Converter;
import com.google.common.base.Optional;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.*;
import com.novetta.ibg.common.image.ImageInfo;
import com.novetta.ibg.common.sys.Whicher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static com.github.mike10004.nativehelper.Program.running;
import static com.google.common.base.Preconditions.checkNotNull;

class Images {

    public static boolean isImageMagickInstalled() {
        String[] progs = {"mogrify", "convert", "identify"};
        Whicher whicher = Whicher.gnu();
        for (String prog : progs) {
            if (!whicher.which(prog).isPresent()) {
                return false;
            }
        }
        return true;
    }

    private Images() {}

    static abstract class ImageConverter<T> extends Converter<T, File> {

        @Override
        protected T doBackward(File file) {
            throw new UnsupportedOperationException("backward conversion not supported");
        }

        protected static class ImageConverterException extends XvfbException {
            public ImageConverterException(Throwable cause) {
                super(cause);
            }
        }

        @Override
        protected final File doForward(T input) throws ImageConverterException {
            try {
                return convertToFile(input);
            } catch (IOException e) {
                throw new ImageConverterException(e);
            }
        }

        protected abstract File convertToFile(T input) throws IOException;
    }

    private static class ExposedFileByteSource extends ByteSource {

        public final File file;
        private final ByteSource delegate;

        private ExposedFileByteSource(File file) {
            this.file = checkNotNull(file);
            this.delegate = Files.asByteSource(file);
        }

        @Override
        public CharSource asCharSource(Charset charset) {
            return delegate.asCharSource(charset);
        }

        @Override
        public InputStream openStream() throws IOException {
            return delegate.openStream();
        }

        @Override
        public InputStream openBufferedStream() throws IOException {
            return delegate.openBufferedStream();
        }

        @Override
        public ByteSource slice(long offset, long length) {
            return delegate.slice(offset, length);
        }

        @Override
        public boolean isEmpty() throws IOException {
            return delegate.isEmpty();
        }

        @Override
        @Beta
        public Optional<Long> sizeIfKnown() {
            return delegate.sizeIfKnown();
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public long copyTo(OutputStream output) throws IOException {
            return delegate.copyTo(output);
        }

        @Override
        public long copyTo(ByteSink sink) throws IOException {
            return delegate.copyTo(sink);
        }

        @Override
        public byte[] read() throws IOException {
            return delegate.read();
        }

        @Override
        @Beta
        public <T> T read(ByteProcessor<T> processor) throws IOException {
            return delegate.read(processor);
        }

        @Override
        public HashCode hash(HashFunction hashFunction) throws IOException {
            return delegate.hash(hashFunction);
        }

        @Override
        public boolean contentEquals(ByteSource other) throws IOException {
            return delegate.contentEquals(other);
        }

        @Override
        public String toString() {
            return "ByteSource{file=" + file + "}";
        }
    }

    static Converter<File, ByteSource> fileToByteSourceConverter() {
        return new Converter<File, ByteSource>() {
            @Override
            protected ByteSource doForward(File file) {
                return new ExposedFileByteSource(file);
            }

            @Override
            protected File doBackward(ByteSource byteSource) {
                if (byteSource instanceof ExposedFileByteSource) {
                    return ((ExposedFileByteSource)byteSource).file;
                } else {
                    throw new IllegalArgumentException("cannot determine file pathname from byte source " + byteSource);
                }
            }
        };
    }

    static class AwtImageConverter extends ImageConverter<ByteSource> {

        protected final ImageInfo.Format outputFormat;
        private final File outputFile;

        public AwtImageConverter(ImageInfo.Format outputFormat, File outputFile) {
            super();
            this.outputFormat = checkNotNull(outputFormat);
            this.outputFile = checkNotNull(outputFile);
        }

        @Override
        protected File convertToFile(ByteSource inputImageSource) {
            BufferedImage image;
            try {
                try (InputStream in = inputImageSource.openBufferedStream()) {
                    image = ImageIO.read(in);
                }
                if (image == null) {
                    throw new IOException("input in unreadable format: " + inputImageSource);
                }
                ImageIO.write(image, outputFormat.getPreferredExtension(), outputFile);
                return outputFile;
            } catch (IOException e) {
                throw new ImageConverterException(e);
            }
        }
    }

    static class XwdToPnmConverter extends ImageConverter<File> {

        private final File outputFile;
        private final Path tempDir;

        public XwdToPnmConverter(File outputFile, Path tempDir) {
            super();
            this.tempDir = checkNotNull(tempDir);
            this.outputFile = checkNotNull(outputFile);
        }

        @Override
        protected File convertToFile(File xwdFile) throws IOException {
            File stderrFile = File.createTempFile("XwdToPnmConverter-stderr", ".txt", tempDir.toFile());
            ProgramWithOutputFilesResult xwdtopnmResult = running("xwdtopnm")
                    .arg(xwdFile.getAbsolutePath())
                    .outputToFiles(outputFile, stderrFile)
                    .execute();
            System.out.format("xwdtopnm: %s%n", xwdtopnmResult);
            return outputFile;
        }

    }
}
