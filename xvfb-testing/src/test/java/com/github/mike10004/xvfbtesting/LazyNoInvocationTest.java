package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbtesting.LazyRuleTest.ControllerCreationCountingManager;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Unit test that runs on all platforms. A lazy rule is created but {@link XvfbRule#getController()} is never
 * invoked, so no attempt to execute an Xvfb process should be made.
 */
public class LazyNoInvocationTest {

    private final AtomicInteger creationCalls = new AtomicInteger();

    @Rule
    public final XvfbRule rule = XvfbRule.builder()
            .manager(new ControllerCreationCountingManager(creationCalls))
            .lazy().build();

    @Test
    public void testLazy_getControllerNeverCalled() throws Exception {
        // do not invoke getController()
        assertEquals("creationCalls", 0, creationCalls.get());
    }

    @After
    public void checkCreationCalls() {
        assertEquals("creationCalls", 0, creationCalls.get());
    }
}
