/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

/**
 * Interface for classes that sleep. The {@link DefaultSleeper default implementation}
 * calls {@link Thread#sleep(long)}. Unit tests may mock this class
 * or re-implement it to suit their needs.
 */
public interface Sleeper {
    /**
     * Sleep for the given number of milliseconds.
     * @param millis the sleep duration
     * @throws InterruptedException if sleep is interrupted
     */
    void sleep(long millis) throws InterruptedException;

    /**
     * Default sleeper implementation. Uses {@link Thread#sleep(long)}.
     */
    class DefaultSleeper implements Sleeper {

        private DefaultSleeper() {
        }

        private static final DefaultSleeper instance = new DefaultSleeper();

        /**
         * Gets the singleton instance of this class.
         * @return the singleton
         */
        public static DefaultSleeper getInstance() {
            return instance;
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

}
