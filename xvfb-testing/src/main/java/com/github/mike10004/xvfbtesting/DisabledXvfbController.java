/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.Screenshooter;
import com.github.mike10004.xvfbmanager.Screenshot;
import com.github.mike10004.xvfbmanager.TreeNode;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbException;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

class DisabledXvfbController implements XvfbController {

    private static final DisabledXvfbController instance = new DisabledXvfbController();

    public static DisabledXvfbController getInstance() {
        return instance;
    }

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
     * Does nothing
     * @param environment map of environment variables
     * @return the argument
     */
    @Override
    public Map<String, String> configureEnvironment(Map<String, String> environment) {
        return environment;
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