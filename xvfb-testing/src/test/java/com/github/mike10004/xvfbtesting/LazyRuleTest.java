package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.ProgramWithOutputResult;
import com.github.mike10004.xvfbmanager.DefaultXvfbController;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.google.common.util.concurrent.ListenableFuture;
import com.github.mike10004.nativehelper.Platforms;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LazyRuleTest {

    private static boolean windows = Platforms.getPlatform().isWindows();

    private static class ControllerCreationCountingManager extends XvfbManager {
        public final AtomicInteger controllerCreationCounter;

        private ControllerCreationCountingManager(AtomicInteger controllerCreationCounter) {
            this.controllerCreationCounter = controllerCreationCounter;
        }

        @Override
        protected DefaultXvfbController createController(ListenableFuture<? extends ProgramWithOutputResult> future, String display, File framebufferDir) {
            controllerCreationCounter.incrementAndGet();
            return super.createController(future, display, framebufferDir);
        }
    }

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
            throw new IllegalStateException("this should never be called");
        }
    }

    @Test
    public void testLazy_noGetControllerCall() throws Exception {
        Assume.assumeTrue("test can't run on windows", !windows);
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        XvfbRule rule = XvfbRule.builder().manager(manager).lazy().build();
        new RuleUserThatDoesNotCallGetController(rule).test();
        assertEquals("creationCalls", 0, creationCalls.get());
    }

    @Test
    public void testEager_noGetControllerCall() throws Exception {
        Assume.assumeTrue("test can't run on windows", !windows);
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        XvfbRule rule = XvfbRule.builder().manager(manager).build();
        new RuleUserThatDoesNotCallGetController(rule).test();
        assertEquals("creationCalls", 1, creationCalls.get());
    }

    @Test
    public void testLazy_multipleGetControllerCall() throws Exception {
        Assume.assumeTrue("test can't run on windows", !windows);
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        final XvfbRule rule = XvfbRule.builder().manager(manager).lazy().build();
        new RuleUser(rule){
            @Override
            protected void use(XvfbController ctrl) throws Exception {
                checkState(ctrl != null);
                assertSame("controller re-creation", rule.getController(), ctrl);
            }
        }.test();
        assertEquals("creationCalls", 1, creationCalls.get());
    }

    @Test
    public void testEager_multipleGetControllerCalls() throws Exception {
        Assume.assumeTrue("test can't run on windows", !windows);
        final AtomicInteger creationCalls = new AtomicInteger();
        XvfbManager manager = new ControllerCreationCountingManager(creationCalls);
        final XvfbRule rule = XvfbRule.builder().manager(manager).build();
        new RuleUser(rule){
            @Override
            protected void use(XvfbController ctrl) throws Exception {
                checkState(ctrl != null);
                assertSame("controller re-creation", rule.getController(), ctrl);
            }
        }.test();
        assertEquals("creationCalls", 1, creationCalls.get());
    }
}
