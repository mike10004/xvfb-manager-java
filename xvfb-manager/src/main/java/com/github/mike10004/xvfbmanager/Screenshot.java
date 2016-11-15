/*
 * (c) 2016 Mike Chaberski
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Interface for screenshots of a virtual framebuffer.
 */
public interface Screenshot {

    /**
     * Gets a byte source providing access to a screenshot.
     * @return the byte source
     */
    ByteSource asByteSource();

    class FileByteSource extends ByteSource {

        public final File file;
        private final ByteSource delegate;

        public FileByteSource(File file) {
            this(file, Files.asByteSource(file));
        }

        protected FileByteSource(File file, ByteSource delegate) {
            this.file = checkNotNull(file);
            this.delegate = checkNotNull(delegate);
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
    }

    class BasicScreenshot<B extends ByteSource> implements Screenshot {

        private final B byteSource;

        public BasicScreenshot(B byteSource) {
            this.byteSource = checkNotNull(byteSource);
        }

        @Override
        public B asByteSource() {
            return byteSource;
        }
    }
}
