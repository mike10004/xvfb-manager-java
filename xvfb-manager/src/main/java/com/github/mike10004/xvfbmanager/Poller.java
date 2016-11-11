/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.google.common.base.Supplier;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class Poller<T> {

    private final Sleeper sleeper;

    public Poller() {
        this(Sleeper.DEFAULT);
    }

    protected Poller(Sleeper sleeper) {
        this.sleeper = checkNotNull(sleeper);
    }

    static class DefaultSleeper implements Sleeper {

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

    public PollOutcome<T> poll(long intervalMs, int maxNumPolls) throws InterruptedException {
        checkArgument(intervalMs > 0, "interval must be > 0, not %s", intervalMs);
        int numPreviousPollAttempts = 0;
        PollAnswer<T> evaluation = null;
        boolean timeout;
        for (;;) {
            if (timeout = numPreviousPollAttempts >= maxNumPolls) {
                break;
            }
            evaluation = checkAndForceNotNull(numPreviousPollAttempts);
            if (evaluation.action != PollAction.CONTINUE) {
                break;
            }
            sleeper.sleep(intervalMs);
            numPreviousPollAttempts++;
        }
        final FinishReason pollResult;
        if (timeout) {
            pollResult = FinishReason.TIMEOUT;
        } else if (evaluation.action == PollAction.ABORT) {
            pollResult = FinishReason.ABORTED;
        } else if (evaluation.action == PollAction.RESOLVE){
            pollResult = FinishReason.RESOLVED;
        } else {
            throw new IllegalStateException("bug: unexpected combination of timeoutedness and StopReason == " + evaluation.action);
        }
        return new PollOutcome<>(pollResult, maybeGetContent(evaluation));
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

    protected static <E> PollAnswer<E> continuePolling(@Nullable E value) {
        return value == null ? PollAnswers.getContinue() : new PollAnswer<>(PollAction.CONTINUE, value);
    }

    protected static <E> PollAnswer<E> abortPolling(@Nullable E value) {
        return value == null ? PollAnswers.getAbort() : new PollAnswer<>(PollAction.ABORT, value);
    }

    protected static <E> PollAnswer<E> continuePolling() {
        return continuePolling(null);
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

    public static class PollOutcome<E> {
        public final FinishReason reason;
        public final @Nullable E content;

        public PollOutcome(FinishReason reason, E content) {
            this.reason = checkNotNull(reason);
            this.content = content;
        }

        @Override
        public String toString() {
            return "PollOutcome{" +
                    "reason=" + reason +
                    ", content=" + content +
                    '}';
        }
    }

    public enum FinishReason {
        RESOLVED,
        ABORTED,
        TIMEOUT
    }

    public static class PollAnswer<E> {
        public final PollAction action;
        public final @Nullable E content;

        public PollAnswer(PollAction action, E content) {
            this.action = checkNotNull(action);
            this.content = content;
        }
    }

    public enum PollAction {
        RESOLVE,
        ABORT,
        CONTINUE
    }

    protected abstract PollAnswer<T> check(int pollAttemptsSoFar);

    /**
     * Creates a simple poller that evaluates a condition on each poll.
     * @param condition the condition to evaluate
     * @return the poller
     */
    public static Poller<Void> checking(final Supplier<Boolean> condition) {
        return checking(Sleeper.DEFAULT, condition);
    }

    protected static Poller<Void> checking(Sleeper sleeper, final Supplier<Boolean> condition) {
        return new Poller<Void>() {
            @Override
            protected PollAnswer<Void> check(int pollAttemptsSoFar) {
                boolean state = condition.get();
                if (state) {
                    return resolve(null);
                } else {
                    return continuePolling();
                }
            }
        };
    }
}
