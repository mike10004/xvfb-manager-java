/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.Poller.FinishReason;
import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.google.common.base.Suppliers;
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
        testPoller(0, 100, 1000, FinishReason.RESOLVED, 0);
    }

    @Test
    public void poll_trueAfterOne() throws Exception {
        testPoller(1, 100, 1000, FinishReason.RESOLVED, 1000);
    }

    @Test
    public void poll_notTrueBeforeLimit() throws Exception {
        testPoller(5, 4, 1000, FinishReason.TIMEOUT, 4000);
    }

    @Test
    public void poll_abortFromCheck() throws Exception {
        TestSleeper sleeper = new TestSleeper();
        PollOutcome<?> outcome = new Poller<Void>(sleeper) {
            @Override
            protected PollAnswer<Void> check(int pollAttemptsSoFar) {
                return abortPolling();
            }
        }.poll(1000, Integer.MAX_VALUE); // poll forever
        assertEquals("reason", FinishReason.ABORTED, outcome.reason);
        assertEquals("duration", 1000, sleeper.getDuration());
        assertEquals("sleep count", 1, sleeper.getCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void poll_badArgs() throws Exception {
        testPoller(5, 4, -1000, null, 0);
    }

    @Test
    public void testDoNotSleepIfAboutToTimeOut() throws Exception {
        TestSleeper sleeper = new TestSleeper();
        Poller.checking(sleeper, Suppliers.ofInstance(true)).poll(1000, 1);
        assertEquals("duration", 1000, sleeper.getDuration());
        assertEquals("sleep count", 1, sleeper.getCount());
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
                    ? resolve(values.incrementAndGet())
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