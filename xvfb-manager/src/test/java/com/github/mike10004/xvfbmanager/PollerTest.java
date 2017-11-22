/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.Poller.PollOutcome;
import com.github.mike10004.xvfbmanager.Poller.SimplePoller;
import com.github.mike10004.xvfbmanager.Poller.StopReason;
import com.google.common.base.Suppliers;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class PollerTest {

    @Test
    public void poll_immediatelyTrue() throws Exception {
        testPoller(0, 0, 1000, StopReason.TIMEOUT, 0);
    }

    @Test
    public void poll_trueAfterZero() throws Exception {
        testPoller(0, 100, 1000, StopReason.RESOLVED, 0);
    }

    @Test
    public void poll_trueAfterOne() throws Exception {
        testPoller(1, 100, 1000, StopReason.RESOLVED, 1000);
    }

    @Test
    public void poll_notTrueBeforeLimit() throws Exception {
        testPoller(5, 4, 1000, StopReason.TIMEOUT, 4000);
    }

    @Test
    public void poll_abortFromCheck_0() throws Exception {
        poll_abortFromCheck(0);
    }

    @Test
    public void poll_abortFromCheck_1() throws Exception {
        poll_abortFromCheck(1);
    }

    @Test
    public void poll_abortFromCheck_2() throws Exception {
        poll_abortFromCheck(2);
    }

    public void poll_abortFromCheck(final int attempts) throws Exception {
        TestSleeper sleeper = new TestSleeper();
        PollOutcome<?> outcome = new Poller<Void>(sleeper) {
            @Override
            protected PollAnswer<Void> check(int pollAttemptsSoFar) {
                return pollAttemptsSoFar >= attempts ? abortPolling() : continuePolling();
            }
        }.poll(1000, Integer.MAX_VALUE); // poll forever
        assertEquals("reason", StopReason.ABORTED, outcome.reason);
        assertEquals("duration", attempts * 1000, sleeper.getDuration());
        assertEquals("sleep count", attempts, sleeper.getCount());
    }

    @Test
    public void poll_timeoutOverridesAbort_0() throws Exception {
        poll_timeoutOverridesAbort(0);
    }

    @Test
    public void poll_timeoutOverridesAbort_1() throws Exception {
        poll_timeoutOverridesAbort(1);
    }

    public void poll_timeoutOverridesAbort(final int attempts) throws Exception {
        TestSleeper sleeper = new TestSleeper();
        PollOutcome<?> outcome = new Poller<Void>(sleeper) {
            @Override
            protected PollAnswer<Void> check(int pollAttemptsSoFar) {
                return pollAttemptsSoFar >= attempts ? abortPolling() : continuePolling();
            }
        }.poll(1000, attempts); // poll forever
        assertEquals("reason", StopReason.TIMEOUT, outcome.reason);
        assertEquals("duration", attempts * 1000, sleeper.getDuration());
        assertEquals("sleep count", attempts, sleeper.getCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void poll_badArgs() throws Exception {
        testPoller(5, 4, -1000, null, 0);
    }

    @Test
    public void testDoNotSleepIfAboutToTimeOut_1() throws Exception {
        testDoNotSleepIfAboutToTimeOut(1000, 1, StopReason.TIMEOUT, 1000, 1);
    }

    @Test
    public void testDoNotSleepIfAboutToTimeOut_2() throws Exception {
        testDoNotSleepIfAboutToTimeOut(1000, 2, StopReason.TIMEOUT, 2000, 2);
    }

    public void testDoNotSleepIfAboutToTimeOut(long intervalMs, int maxPolls, StopReason stopReason, long expectedDuration, int expectedSleeps) throws Exception {
        TestSleeper sleeper = new TestSleeper();
        PollOutcome<Void> outcome = new SimplePoller(sleeper, Suppliers.ofInstance(false)).poll(intervalMs, maxPolls);
        assertEquals("stopReason", stopReason, outcome.reason);
        assertEquals("duration", expectedDuration, sleeper.getDuration());
        assertEquals("sleep count", expectedSleeps, sleeper.getCount());
    }

    private void testPoller(int returnTrueAfterNAttempts, int maxPollAttempts, long interval, StopReason expectedFinishReason, long expectedDuration) throws InterruptedException {
        TestSleeper sleeper = new TestSleeper();
        TestPoller poller = new TestPoller(sleeper, returnTrueAfterNAttempts);
        PollOutcome<?> evaluation = poller.poll(interval, maxPollAttempts);
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
                    : continuePolling();
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