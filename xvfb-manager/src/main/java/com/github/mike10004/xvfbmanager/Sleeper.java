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
     * @throws InterruptedException
     */
    void sleep(long millis) throws InterruptedException;

    class DefaultSleeper implements Sleeper {

        private DefaultSleeper() {
        }

        private static final DefaultSleeper instance = new DefaultSleeper();

        public static DefaultSleeper getInstance() {
            return instance;
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

}
