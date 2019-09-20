package com.github.mike10004.xvfbtesting;

import io.github.mike10004.subprocess.ProcessMonitor;
import com.github.mike10004.xvfbmanager.DefaultXvfbController;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LazyRuleTest {

    @ClassRule
    public static PlatformRule platformRule = PlatformRule.requireNotWindows();

    static class ControllerCreationCountingManager extends XvfbManager {
        public final AtomicInteger controllerCreationCounter;

        public ControllerCreationCountingManager(AtomicInteger controllerCreationCounter) {
            this.controllerCreationCounter = controllerCreationCounter;
        }

        @Override
        protected DefaultXvfbController createController(ProcessMonitor<File, File> future, String display, File framebufferDir) {
            controllerCreationCounter.incrementAndGet();
            return super.createController(future, display, framebufferDir);
        }
    }

    @SuppressWarnings("RedundantThrows")
    private static class RuleUserThatDoesNotCallGetController extends RuleUser {

        public RuleUserThatDoesNotCallGetController(XvfbRule xvfb) {
            super(xvfb);
        }

        @Override
        protected void getControllerAndUse(XvfbRule rule) throws Exception {
            // avoid calling getController in order to confirm that it is never created
        }

        @Override
        protected void use(XvfbController ctrl) throws Exception {
            throw new IllegalStateException("this should never be called, only overridden by subclasses");
        }
    }

    @Test
    public void testLazy_noGetControllerCall() throws Exception {
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        XvfbRule rule = XvfbRule.builder().manager(manager).lazy().build();
        new RuleUserThatDoesNotCallGetController(rule).test();
        assertEquals("creationCalls", 0, creationCalls.get());
    }

    @Test
    public void testEager_noGetControllerCall() throws Exception {
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        XvfbRule rule = XvfbRule.builder().manager(manager).eager().build();
        new RuleUserThatDoesNotCallGetController(rule).test();
        assertEquals("creationCalls", 1, creationCalls.get());
    }

    @Test
    public void testLazy_multipleGetControllerCall() throws Exception {
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        final XvfbRule rule = XvfbRule.builder().manager(manager).lazy().build();
        new RuleUser(rule){
            @Override
            protected void use(XvfbController ctrl) {
                checkState(ctrl != null);
                assertSame("controller re-creation", rule.getController(), ctrl);
            }
        }.test();
        assertEquals("creationCalls", 1, creationCalls.get());
    }

    @Test
    public void testEager_multipleGetControllerCalls() throws Exception {
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        final XvfbRule rule = XvfbRule.builder().manager(manager).eager().build();
        new RuleUser(rule){
            @Override
            protected void use(XvfbController ctrl) {
                checkState(ctrl != null);
                assertSame("controller re-creation", rule.getController(), ctrl);
            }
        }.test();
        assertEquals("creationCalls", 1, creationCalls.get());
    }

}
