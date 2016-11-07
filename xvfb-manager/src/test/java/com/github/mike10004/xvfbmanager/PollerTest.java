/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import org.junit.Test;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.*;

public class PollerTest {

    @Test
    public void poll_immediatelyTrue() throws Exception {
        testPoller(0, 100, 1000, true, 0);
    }

    @Test
    public void poll_trueAfterOne() throws Exception {
        testPoller(1, 100, 1000, true, 1000);
    }

    @Test
    public void poll_notTrueBeforeLimit() throws Exception {
        testPoller(5, 4, 1000, false, 4000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void poll_badArgs() throws Exception {
        testPoller(5, 4, -1000, false, 0);
    }

    private void testPoller(int returnTrueAfterNAttempts, int maxPollAttempts, long interval, boolean expectedEvaluation, long expectedDuration) throws InterruptedException {
        TestSleeper sleeper = new TestSleeper();
        TestPoller poller = new TestPoller(sleeper, returnTrueAfterNAttempts);
        boolean evaluation = poller.poll(interval, maxPollAttempts);
        assertEquals("evaluation", expectedEvaluation, evaluation);
        assertEquals("duration", expectedDuration, sleeper.getDuration());
    }

    private static class TestPoller extends Poller {

        private final int returnTrueAfterNAttempts;

        public TestPoller(TestSleeper sleeper, int returnTrueAfterNAttempts) {
            super(sleeper);
            this.returnTrueAfterNAttempts = returnTrueAfterNAttempts;
        }

        @Override
        protected boolean check(int pollAttemptsSoFar) {
            return pollAttemptsSoFar >= returnTrueAfterNAttempts;
        }
    }
}