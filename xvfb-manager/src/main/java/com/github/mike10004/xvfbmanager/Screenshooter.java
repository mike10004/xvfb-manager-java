package com.github.mike10004.xvfbmanager;

import java.io.IOException;

/**
 * Interface for a class that can capture a screenshot of a virtual framebuffer.
 * @param <T> screenshot type
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
    class ScreenshooterException extends XvfbException {
        public ScreenshooterException() {
        }

        public ScreenshooterException(String message) {
            super(message);
        }

        public ScreenshooterException(String message, Throwable cause) {
            super(message, cause);
        }

        public ScreenshooterException(Throwable cause) {
            super(cause);
        }
    }

}
