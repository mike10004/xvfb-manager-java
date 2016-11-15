/*
 * (c) 2016 Mike Chaberski
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import java.io.IOException;

/**
 * Interface for a class that can capture a screenshot of a virtual framebuffer.
 * <T> screenshot type
 */
public interface Screenshooter<T extends Screenshot> {
    /**
     * Captures a screenshot.
     * @return the screenshot
     * @throws IOException if capture encounters an I/O error
     * @throws XvfbException if a virtual framebuffer error occurs
     */
    T capture() throws IOException, XvfbException;

    @SuppressWarnings("unused")
    class DefaultScreenshooterException extends XvfbException {
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

}
