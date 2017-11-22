/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.Sleeper.DefaultSleeper;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class that facilitates polling for an arbitrary condition. Polling is
 * the act of repeatedly querying the state at defined intervals. Polling
 * stops when this poller's {@link #check(int) evaluation function}
 * answers with a reason to stop, or the iterator of intervals to wait
 * between polls is exhausted. Reasons to stop include
 * {@link PollAction#RESOLVE resolution}, meaning the poller is satisfied
 * with the result, or {@link PollAction#ABORT abortion} meaning polling
 * must stop early without a resolution.
 *
 * @param <T> type of content returned upon resolution
 */
public abstract class Poller<T> {

    private final Sleeper sleeper;

    /**
     * Creates a new poller. The poller waits between polls using the
     * default {@link Sleeper}. Subclasses can use an alternate sleeper,
     * is helpful if you want to test your poller without actually waiting.
     */
    public Poller() {
        this(DefaultSleeper.getInstance());
    }

    protected Poller(Sleeper sleeper) {
        this.sleeper = checkNotNull(sleeper);
    }

    /**
     * Polls at regular intervals.
     * @param intervalMs the interval in milliseconds
     * @param maxNumPolls the maximum number of polls to be executed
     * @return the poll outcome
     * @throws InterruptedException if waiting is interrupted
     */
    public PollOutcome<T> poll(long intervalMs, int maxNumPolls) throws InterruptedException {
        return poll(new RegularIntervals(intervalMs, maxNumPolls));
    }

    protected static class RegularIntervals implements Iterator<Long> {

        private final Long intervalMs;
        private final int maxNumPolls;
        private int numPreviousPollAttempts;

        public RegularIntervals(long intervalMs, int maxNumPolls) {
            checkArgument(intervalMs > 0, "interval must be > 0, not %s", intervalMs);
            this.intervalMs = intervalMs;
            this.maxNumPolls = maxNumPolls;
        }

        @Override
        public synchronized boolean hasNext() {
            return numPreviousPollAttempts < maxNumPolls;
        }

        @Override
        public synchronized Long next() {
            numPreviousPollAttempts++;
            return intervalMs;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove() not supported on " + this);
        }
    }

    /**
     * Starts polling and returns an outcome when polling stops.
     * @param intervalsMs an iterator of interval lengths in milliseconds
     * @return the poll outcome
     * @throws InterruptedException if waiting is interrupted
     */
    public PollOutcome<T> poll(Iterator<Long> intervalsMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int numPreviousPollAttempts = 0;
        PollAnswer<T> evaluation = null;
        boolean timeout;
        for (;;) {
            if (timeout = !intervalsMs.hasNext()) {
                break;
            }
            long intervalMs = checkNotNull(intervalsMs.next(), "interval iterator must return non-nulls").longValue();
            checkArgument(intervalMs > 0, "intervals iterator must return positive values; got %s", intervalMs);
            evaluation = checkAndForceNotNull(numPreviousPollAttempts);
            numPreviousPollAttempts++;
            if (evaluation.action != PollAction.CONTINUE) {
                break;
            }
            sleeper.sleep(intervalMs);
        }
        long finishTime = System.currentTimeMillis();
        final StopReason pollResult;
        if (timeout) {
            pollResult = StopReason.TIMEOUT;
        } else if (evaluation.action == PollAction.ABORT) {
            pollResult = StopReason.ABORTED;
        } else if (evaluation.action == PollAction.RESOLVE){
            pollResult = StopReason.RESOLVED;
        } else {
            throw new IllegalStateException("bug: unexpected combination of timeoutedness and StopReason == " + evaluation.action);
        }
        Duration duration = Duration.of(finishTime - startTime, ChronoUnit.MILLIS);
        return new PollOutcome<>(pollResult, maybeGetContent(evaluation), duration, numPreviousPollAttempts);
    }

    private PollAnswer<T> checkAndForceNotNull(int numPreviousPollAttempts) {
        PollAnswer<T> answer = check(numPreviousPollAttempts);
        checkNotNull(answer, "check() must return non-null with non-null action");
        return answer;
    }

    private static @Nullable <E> E maybeGetContent(@Nullable PollAnswer<E> answer) {
        return answer == null ? null : answer.content;
    }

    protected static <E> PollAnswer<E> resolve(@Nullable E value) {
        return value == null ? PollAnswers.getResolve() : new PollAnswer<>(PollAction.RESOLVE, value);
    }

    protected static <E> PollAnswer<E> continuePolling() {
        return PollAnswers.getContinue();
    }

    @SuppressWarnings("SameParameterValue")
    protected static <E> PollAnswer<E> abortPolling(@Nullable E value) {
        return value == null ? PollAnswers.getAbort() : new PollAnswer<>(PollAction.ABORT, value);
    }

    protected static <E> PollAnswer<E> abortPolling() {
        return abortPolling(null);
    }

    @SuppressWarnings("unchecked")
    private static class PollAnswers {
        private static final PollAnswer ABORT_WITH_NULL_VALUE = new PollAnswer(PollAction.ABORT, null);
        private static final PollAnswer RESOLVE_WITH_NULL_VALUE = new PollAnswer(PollAction.RESOLVE, null);
        private static final PollAnswer CONTINUE_WITH_NULL_VALUE = new PollAnswer(PollAction.CONTINUE, null);

        public static <E> PollAnswer<E> getAbort() {
            return getAnswerWithNullValue(PollAction.ABORT);
        }

        public static <E> PollAnswer<E> getResolve() {
            return getAnswerWithNullValue(PollAction.RESOLVE);
        }

        public static <E> PollAnswer<E> getContinue() {
            return getAnswerWithNullValue(PollAction.CONTINUE);
        }

        public static <E> PollAnswer<E> getAnswerWithNullValue(PollAction action) {
            checkNotNull(action, "action");
            switch (action) {
                case ABORT:
                    return ABORT_WITH_NULL_VALUE;
                case RESOLVE:
                    return RESOLVE_WITH_NULL_VALUE;
                case CONTINUE:
                    return CONTINUE_WITH_NULL_VALUE;
                default:
                    throw new IllegalStateException("bug: unhandled enum " + action);
            }
        }
    }

    /**
     * Class that represents the outcome of a poll. Instances of this type are constructed
     * by the poller at the conclusion of all polling. To clarify: a poll outcome refers
     * to the end result after many poll attempts, and a {@link PollAnswer poll answer}
     * is the answer to any individual poll attempt.
     * @param <E> type of the resolved content
     */
    public static class PollOutcome<E> {

        /**
         * Reason polling stopped.
         */
        public final StopReason reason;

        /**
         * An object that represents the resolved state of the poll.
         */
        public final @Nullable E content;

        /**
         * Gets the polling duration. This may not be exact.
         */
        public final Duration duration;

        private final int numAttempts;

        private PollOutcome(StopReason reason, @Nullable E content, Duration duration, int numAttempts) {
            this.reason = checkNotNull(reason);
            this.content = content;
            this.duration = checkNotNull(duration);
            this.numAttempts = numAttempts;
        }

        @Override
        public String toString() {
            return "PollOutcome{" +
                    "reason=" + reason +
                    ", content=" + content +
                    ", duration=" + duration +
                    ", attempts=" + numAttempts +
                    '}';
        }

        /**
         * Gets the number of times the poll was attempted. This is the numbef of
         * times the {@link #check(int) check()} function is invoked.
         * @return count of attempts
         */
        public int getNumAttempts() {
            return numAttempts;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PollOutcome<?> that = (PollOutcome<?>) o;

            if (numAttempts != that.numAttempts) return false;
            if (reason != that.reason) return false;
            if (content != null ? !content.equals(that.content) : that.content != null) return false;
            return duration.equals(that.duration);
        }

        @Override
        public int hashCode() {
            int result = reason.hashCode();
            result = 31 * result + (content != null ? content.hashCode() : 0);
            result = 31 * result + duration.hashCode();
            result = 31 * result + numAttempts;
            return result;
        }
    }

    /**
     * Enmeration of reasons that polling stopped.
     */
    public enum StopReason {

        /**
         * State was resolved to the poller's satisfaction.
         */
        RESOLVED,

        /**
         * State was not resolved to the poller's satisfaction,
         * but polling must cease anyway.
         */
        ABORTED,

        /**
         * The poller's iterator of intervals was exhausted
         * prior to resolution or abortion of polling.
         */
        TIMEOUT
    }

    /**
     * Class that represents an answer in response to a poll request.
     * Instances of this class are constructed with the poller's
     * {@link Poller#continuePolling() continuePolling()},
     * {@link Poller#abortPolling() abortPolling()}, and
     * {@link Poller#resolve(Object) resolve()} methods.
     *
     * <p>To clarify: a poll outcome refers
     * to the end result after many poll attempts, and a poll answer
     * is the answer to any individual poll attempt.</p>
     * @param <E> the type of content in the resolution
     */
    public static class PollAnswer<E> {

        /**
         * Action the poller should take after receiving this answer.
         */
        public final PollAction action;

        /**
         * Content of the answer. If the {@link #action} is {@link PollAction#RESOLVE},
         * then this is likely to be non-null. Otherwise, it is likely to be null.
         * Implementations may elect to flout these conventions.
         */
        public final @Nullable E content;

        private PollAnswer(PollAction action, @Nullable E content) {
            this.action = checkNotNull(action);
            this.content = content;
        }
    }

    /**
     * Enumeration of actions a poller's check function can return.
     */
    public enum PollAction {

        /**
         * Stop polling because the state in question has been resolved.
         */
        RESOLVE,

        /**
         * Stop polling without a resolution.
         */
        ABORT,

        /**
         * Keep polling.
         */
        CONTINUE
    }

    /**
     * Checks whether the state being questioned has been resolved. Subclasses
     * must override this method to return an {@link PollAnswer answer} that
     * may or may not contain a content object. In a conventional implementation,
     * if the state has been resolved, this method would return an answer with content
     * object representing a resolution along with {@link PollAction#RESOLVE};
     * if the state has not yet been resolved, this method would return
     * {@code null} as the answer content along with {@link PollAction#CONTINUE} if
     * we should continue polling or {@link PollAction#ABORT} if polling should stop
     * immediately anyway.
     *
     * <p>Implementations of this method should return an answer constructed with the
     * {@link #continuePolling()}, {@link #abortPolling()}, or {@link #resolve(Object)}
     * methods.</p>
     *
     * <p>Unconventional implementations may elect to return a non-null content object
     * with {@link PollAction#ABORT} to provide the {@link #poll(Iterator) poll()}
     * caller a degenerate answer, perhaps indicating why state will never be resolved.</p>
     * @param pollAttemptsSoFar the number of poll attempts prior to this poll attempt
     * @return a poll answer
     */
    protected abstract PollAnswer<T> check(int pollAttemptsSoFar);

    /**
     * Creates a simple poller that evaluates a condition on each poll.
     * @param condition the condition to evaluate; poll will be resolved if it
     *                  returns true, and if it returns false, the poller will
     *                  keep polling
     * @return the poller
     */
    @SuppressWarnings("unused")
    public static Poller<Void> checking(final Supplier<Boolean> condition) {
        return new SimplePoller(condition);
    }

    protected static class SimplePoller extends Poller<Void> {

        private final Supplier<Boolean> condition;

        public SimplePoller(Sleeper sleeper, Supplier<Boolean> condition) {
            super(sleeper);
            this.condition = checkNotNull(condition);
        }

        public SimplePoller(Supplier<Boolean> condition) {
            super();
            this.condition = checkNotNull(condition);
        }

        @Override
        protected PollAnswer<Void> check(int pollAttemptsSoFar) {
            boolean state = condition.get();
            if (state) {
                return resolve(null);
            } else {
                return continuePolling();
            }
        }
    }

}
