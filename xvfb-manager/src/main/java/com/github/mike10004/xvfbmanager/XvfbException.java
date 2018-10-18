package com.github.mike10004.xvfbmanager;

/**
 * Exception relating to virtual framebuffer management.
 */
public class XvfbException extends RuntimeException {

    /**
     * Constructs an instance.
     */
    public XvfbException() {
    }

    /**
     * Constructs an instance with the given message.
     * @param message the message
     */
    public XvfbException(String message) {
        super(message);
    }

    /**
     * Constructs an instance with the given message and cause.
     * @param message the message
     * @param cause the cause
     */
    public XvfbException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an instance with the given cause.
     * @param cause the cause
     */
    public XvfbException(Throwable cause) {
        super(cause);
    }
}
