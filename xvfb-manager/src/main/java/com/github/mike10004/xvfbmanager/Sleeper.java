/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

/**
 * Interface for classes that sleep. The {@link #DEFAULT default implementation}
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

    /**
     * Default implementation of the sleeper interface.
     * Calls {@link Thread#sleep(long)}.
     */
    Sleeper DEFAULT = new Poller.DefaultSleeper();

}
