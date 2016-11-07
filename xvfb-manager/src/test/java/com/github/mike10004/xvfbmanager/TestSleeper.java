/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class TestSleeper implements Sleeper {
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
