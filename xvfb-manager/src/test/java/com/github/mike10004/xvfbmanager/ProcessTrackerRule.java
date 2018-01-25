package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.subprocess.ProcessTracker;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

public class ProcessTrackerRule extends ExternalResource {

    private final TestWatcher watcher;
    private final AtomicBoolean passage;
    private ProcessTracker processContext;

    public ProcessTrackerRule() {
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
        processContext = ProcessTracker.create();
    }

    @Override
    protected void after() {
        ProcessTracker processContext = this.processContext;
        if (processContext != null) {
            boolean testPassed = passage.get();
            if (testPassed) {
                if (processContext.activeCount() > 0) {
                    System.err.format("%d active processes in context%n", processContext.activeCount());
                }
            } else {
                assertEquals("number of active processes in context must be zero", 0, processContext.activeCount());
            }
        }
    }

    public ProcessTracker getTracker() {
        return processContext;
    }
}
