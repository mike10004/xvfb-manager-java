/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Interface for a class that controls a virtual framebuffer process.
 */
public interface XvfbController extends Closeable {

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
     * Gets the display number in the format {@code :N} where {@code N} is the display number.
     * @return the display
     */
    String getDisplay();

    /**
     * Captures a screenshot of the virtual framebuffer.
     * @return the screenshot
     * @throws IOException
     * @throws XvfbException
     */
    XvfbManager.Screenshot captureScreenshot() throws IOException, XvfbException;

    class XWindow {
        public final String id;
        public final @Nullable
        String title;
        public final String line;

        public XWindow(String id, @Nullable String title, String extra) {
            this.id = id;
            this.title = title;
            this.line = extra;
        }

        @Override
        public String toString() {
            return "XWindow{" +
                    "id=" + id +
                    ", title='" + title + '\'' +
                    ", length=" + (line == null ? -1 : line.length()) +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            XWindow xWindow = (XWindow) o;

            if (id != null ? !id.equals(xWindow.id) : xWindow.id != null) return false;
            if (title != null ? !title.equals(xWindow.title) : xWindow.title != null) return false;
            return line != null ? line.equals(xWindow.line) : xWindow.line == null;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (title != null ? title.hashCode() : 0);
            result = 31 * result + (line != null ? line.hashCode() : 0);
            return result;
        }
    }

    Optional<TreeNode<XWindow>> pollForWindow(Predicate<XWindow> windowFinder, long intervalMs, int maxPollAttempts) throws InterruptedException;

}
