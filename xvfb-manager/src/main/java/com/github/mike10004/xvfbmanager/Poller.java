/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

abstract class Poller {

    private final Sleeper sleeper;

    protected Poller(Sleeper sleeper) {
        this.sleeper = checkNotNull(sleeper);
    }

    public boolean poll(long intervalMs, int maxNumPolls) throws InterruptedException {
        checkArgument(intervalMs > 0, "interval must be > 0, not %s", intervalMs);
        int pollAttemptsSoFar = 0;
        boolean evaluation = false;
        while ((pollAttemptsSoFar < maxNumPolls) && !isAborted() && !(evaluation = check(pollAttemptsSoFar))) {
            pollAttemptsSoFar++;
            sleeper.sleep(intervalMs);
        }
        return evaluation;
    }

    protected abstract boolean check(int pollAttemptsSoFar);

    protected boolean isAborted() {
        return false;
    }
}
