/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import java.io.IOException;

/**
 * Interface for a class that controls a virtual framebuffer process.
 */
public interface XvfbController extends AutoCloseable {

    /**
     * Waits using default values for the polling interval and maximum polls.
     * @throws InterruptedException
     * @see #waitUntilReady(long, int)
     */
    void waitUntilReady() throws InterruptedException;

    /**
     * Waits until the X display is ready, polling at a given interval at most
     * the specified number of times.
     * @param pollIntervalMs interval between polls
     * @param maxNumPolls maximum number of polls to execute
     * @throws InterruptedException
     */
    void waitUntilReady(long pollIntervalMs, int maxNumPolls) throws InterruptedException;

    /**
     * Stops the virtual framebuffer process.
     */
    void stop();

    /**
     * Captures a screenshot of the virtual framebuffer.
     * @return the screenshot
     * @throws IOException
     * @throws XvfbException
     */
    XvfbManager.Screenshot captureScreenshot() throws IOException, XvfbException;
}
