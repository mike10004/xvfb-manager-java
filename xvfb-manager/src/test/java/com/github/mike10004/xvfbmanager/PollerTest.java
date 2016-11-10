/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.Poller.FinishReason;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class PollerTest {

    @Test
    public void poll_immediatelyTrue() throws Exception {
        testPoller(0, 0, 1000, FinishReason.TIMEOUT, 0);
    }

    @Test
    public void poll_trueAfterZero() throws Exception {
        testPoller(0, 100, 1000, FinishReason.STOPPED, 0);
    }

    @Test
    public void poll_trueAfterOne() throws Exception {
        testPoller(1, 100, 1000, FinishReason.STOPPED, 1000);
    }

    @Test
    public void poll_notTrueBeforeLimit() throws Exception {
        testPoller(5, 4, 1000, FinishReason.TIMEOUT, 4000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void poll_badArgs() throws Exception {
        testPoller(5, 4, -1000, null, 0);
    }

    private void testPoller(int returnTrueAfterNAttempts, int maxPollAttempts, long interval, FinishReason expectedFinishReason, long expectedDuration) throws InterruptedException {
        TestSleeper sleeper = new TestSleeper();
        TestPoller poller = new TestPoller(sleeper, returnTrueAfterNAttempts);
        PollOutcome<?> evaluation = poller.poll(interval, maxPollAttempts);
        System.out.format("poll result: %s%n", evaluation);
        assertEquals("evaluation.result", expectedFinishReason, evaluation.reason);
        assertEquals("duration", expectedDuration, sleeper.getDuration());
    }

    private static class TestPoller extends Poller<Long> {

        private final AtomicLong values = new AtomicLong(0L);
        private final int returnTrueAfterNAttempts;

        public TestPoller(TestSleeper sleeper, int returnTrueAfterNAttempts) {
            super(sleeper);
            this.returnTrueAfterNAttempts = returnTrueAfterNAttempts;
        }

        @Override
        protected PollAnswer<Long> check(int pollAttemptsSoFar) {
            return pollAttemptsSoFar >= returnTrueAfterNAttempts
                    ? stopPolling(values.incrementAndGet())
                    : continuePolling(values.incrementAndGet());
        }
    }

    private static class TestSleeper implements Sleeper {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final AtomicLong totalDuration = new AtomicLong(0L);

        public long getDuration() {
            return totalDuration.get();
        }

        public int getCount() {
            return counter.get();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            totalDuration.addAndGet(millis);
            counter.incrementAndGet();
        }
    }

}