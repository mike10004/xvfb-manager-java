package com.github.mike10004.xvfbunittesthelp;

import io.github.mike10004.subprocess.BasicProcessTracker;
import io.github.mike10004.subprocess.ProcessTracker;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class ProcessTrackerRule extends ExternalResource {

    private static final Duration DEFAULT_DESTROY_TIMEOUT = Duration.ofMillis(1000);

    private final TestWatcher watcher;
    private final AtomicBoolean passage;
    private BasicProcessTracker processTracker;
    private final Duration processDestroyTimeout;

    public ProcessTrackerRule() {
        this(DEFAULT_DESTROY_TIMEOUT);
    }

    public ProcessTrackerRule(Duration processDestroyTimeout) {
        this.processDestroyTimeout = requireNonNull(processDestroyTimeout, "processDestroyTimeout");
        passage = new AtomicBoolean(false);
        watcher = new TestWatcher() {
            @Override
            protected void succeeded(Description description) {
                passage.set(true);
            }
        };
    }

    @Override
    public Statement apply(Statement base, Description description) {
        watcher.apply(base, description);
        return super.apply(base, description);
    }

    @Override
    protected void before() {
        processTracker = new BasicProcessTracker();
    }

    @Override
    protected void after() {
        BasicProcessTracker processTracker = this.processTracker;
        if (processTracker != null) {
            boolean testPassed = passage.get();
            if (testPassed) {
                if (processTracker.activeCount() > 0) {
                    System.err.format("%d active processes in context%n", processTracker.activeCount());
                }
            } else {
                processTracker.destroyAll(processDestroyTimeout.toMillis(), TimeUnit.MILLISECONDS);
                assertEquals("number of active processes in context must be zero", 0, processTracker.activeCount());
            }
        }
    }

    public ProcessTracker getTracker() {
        return processTracker;
    }
}
