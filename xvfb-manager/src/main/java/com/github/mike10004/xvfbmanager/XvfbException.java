/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

/**
 * Exception relating to virtual framebuffer management.
 */
public class XvfbException extends RuntimeException {

    public XvfbException() {
    }

    public XvfbException(String message) {
        super(message);
    }

    public XvfbException(String message, Throwable cause) {
        super(message, cause);
    }

    public XvfbException(Throwable cause) {
        super(cause);
    }
}
