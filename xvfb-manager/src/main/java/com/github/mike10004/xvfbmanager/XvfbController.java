package com.github.mike10004.xvfbmanager;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Interface for a class that controls a virtual framebuffer process.
 */
public interface XvfbController extends Closeable {

    String ENV_DISPLAY = "DISPLAY";

    /**
     * Waits until the display is ready, using default values for the polling
     * interval and maximum polls. Implementations may select the defaults
     * to use.
     * @throws InterruptedException if waiting is interrupted
     * @see #waitUntilReady(long, int)
     */
    void waitUntilReady() throws InterruptedException;

    /**
     * Waits until the X display is ready, polling at a given interval until the
     * display is ready or the given number of polls has been executed.
     * @param pollIntervalMs interval between polls in milliseconds
     * @param maxNumPolls maximum number of polls to execute
     * @throws InterruptedException if waiting is interrupted
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
     * Sets the display environment variable in the given environment.
     * @param environment map of environment variables in which display is to be set
     * @return the argument environment object
     * @see #ENV_DISPLAY
     */
    Map<String, String> configureEnvironment(Map<String, String> environment);

    /**
     * Creates a new, mutable environment variable map with the display variable set.
     * @return the new environment map
     * @see #configureEnvironment(Map)
     */
    @SuppressWarnings("unused")
    Map<String, String> newEnvironment();

    /**
     * Captures a screenshot of the virtual framebuffer.
     * @return the screenshot
     * @throws XvfbException if screenshooting goes awry
     */
    Screenshooter<?> getScreenshooter() throws XvfbException;

    /**
     * Class representing information about a window rendered by an X server.
     */
    class XWindow {

        /**
         * Window ID. This is commonly an integer in hexadecimal format, for example {@code 0x38ab0e}.
         */
        public final String id;

        /**
         * Window title. Null means the window has no title. The title may also
         * be empty, though that is not common.
         */
        public final @Nullable String title;

        /**
         * The line of output from which this window instance was parsed. This is a
         * line from {@code }
         */
        public final String line;

        /**
         * Constructs a new instance of the class.
         * @param id window id
         * @param title window title
         * @param line line of output from which this window information was parsed
         */
        public XWindow(String id, @Nullable String title, String line) {
            this.id = id;
            this.title = title;
            this.line = line;
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
