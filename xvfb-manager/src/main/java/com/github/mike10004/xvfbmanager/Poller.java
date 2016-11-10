/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class Poller<T> {

    private final Sleeper sleeper;

    protected Poller(Sleeper sleeper) {
        this.sleeper = checkNotNull(sleeper);
    }

    public PollOutcome<T> poll(long intervalMs, int maxNumPolls) throws InterruptedException {
        checkArgument(intervalMs > 0, "interval must be > 0, not %s", intervalMs);
        int pollAttemptsSoFar = 0;
        PollAnswer<T> evaluation = null;
        boolean timeout;
        boolean aborted = false;
        while (!(timeout = !(pollAttemptsSoFar < maxNumPolls))
                && !(aborted = isAborted())
                && (PollAction.CONTINUE == (evaluation = checkAndForceNotNull(pollAttemptsSoFar)).action)) {
            pollAttemptsSoFar++;
            sleeper.sleep(intervalMs);
        }
        final FinishReason pollResult;
        if (timeout) {
            pollResult = FinishReason.TIMEOUT;
        } else if (aborted) {
            pollResult = FinishReason.ABORTED;
        } else {
            //noinspection ConstantConditions
            checkState(evaluation != null, "expect evaluation to have been returned at least once (since we haven't timed out or aborted)");
            pollResult = FinishReason.STOPPED;
        }
        return new PollOutcome<>(pollResult, maybeGetContent(evaluation));
    }

    private PollAnswer<T> checkAndForceNotNull(int pollAttemptsSoFar) {
        PollAnswer<T> answer = check(pollAttemptsSoFar);
        checkNotNull(answer, "check() must return non-null with non-null action");
        return answer;
    }

    private static @Nullable <E> E maybeGetContent(@Nullable PollAnswer<E> answer) {
        return answer == null ? null : answer.content;
    }

    protected static <E> PollAnswer<E> stopPolling(@Nullable E value) {
        return new PollAnswer<>(PollAction.STOP, value);
    }

    protected static <E> PollAnswer<E> continuePolling(@Nullable E value) {
        return new PollAnswer<>(PollAction.CONTINUE, value);
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
        STOPPED,
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
        STOP,
        CONTINUE
    }

    protected abstract PollAnswer<T> check(int pollAttemptsSoFar);

    protected boolean isAborted() {
        return false;
    }

}
