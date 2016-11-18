/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.io.IOException;

public class DisabledXvfbController implements XvfbController {

    /**
     * Returns immediately without doing anything.
     */
    @Override
    public void waitUntilReady() {

    }

    /**
     * Returns immediately without doing anything.
     * @param pollIntervalMs ignored
     * @param maxNumPolls ignored
     */
    @Override
    public void waitUntilReady(long pollIntervalMs, int maxNumPolls) {
    }

    /**
     * Does nothing.
     */
    @Override
    public void stop() {
    }

    /**
     * Always returns null.
     * @return null
     */
    @Override
    public @Nullable String getDisplay() {
        return null;
    }

    /**
     * Gets a screenshooter that throws an exception on capture.
     * @return a failing screenshooter instance
     */
    @Override
    public Screenshooter<?> getScreenshooter() {
        return DisabledScreenshooter.instance;
    }

    /**
     * Returns absent immediately.
     * @param windowFinder the predicate
     * @param intervalMs the interval
     * @param maxPollAttempts the max poll attempts
     * @return {@link Optional#absent()}
     */
    @Override
    public Optional<TreeNode<XWindow>> pollForWindow(Predicate<XWindow> windowFinder, long intervalMs, int maxPollAttempts) {
        return Optional.absent();
    }

    /**
     * Does nothing.
     */
    @Override
    public void close() {
    }

    private static class DisabledScreenshooter<T extends Screenshot> implements Screenshooter<T> {

        private static final DisabledScreenshooter<?> instance = new DisabledScreenshooter<>();

        @Override
        public T capture() throws IOException, XvfbException {
            throw new XvfbException("disabled");
        }
    }
}
