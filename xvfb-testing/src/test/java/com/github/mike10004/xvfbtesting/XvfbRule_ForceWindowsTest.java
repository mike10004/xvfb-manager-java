package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Platforms;
import io.github.mike10004.subprocess.SubprocessLaunchException;
import com.github.mike10004.xvfbmanager.XvfbController;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class XvfbRule_ForceWindowsTest {

    private final TestCase testCase;

    public XvfbRule_ForceWindowsTest(TestCase testCase) {
        this.testCase = testCase;
    }

    @ClassRule
    public static PlatformRule platformRule = PlatformRule.requireWindows();

    @Parameterized.Parameters
    public static List<TestCase> rules() {
        //noinspection RedundantArrayCreation
        return Arrays.asList(new TestCase[]{
                new TestCase(XvfbRule.builder().notDisabledOnWindows().build(), false, false),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().eager().build(), true, false),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().disabled(false).build(), false, false),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().eager().disabled(false).build(), true, false),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().build(), false, true),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().eager().build(), true, true),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().disabled(false).build(), false, true),
                new TestCase(XvfbRule.builder().notDisabledOnWindows().eager().disabled(false).build(), true, true),
        });
    }

    public static class TestCase {
        public final XvfbRule rule;
        public final boolean eagerStart;
        public final boolean invokeGetController;

        private TestCase(XvfbRule rule, boolean eagerStart, boolean invokeGetController) {
            this.rule = rule;
            this.eagerStart = eagerStart;
            this.invokeGetController = invokeGetController;
        }

        @Override
        public String toString() {
            return "TestCase{" +
                    "rule=" + rule +
                    ", eagerStart=" + eagerStart +
                    ", invokeGetController=" + invokeGetController +
                    '}';
        }
    }

    @Test
    @SuppressWarnings("RedundantThrows")
    public void testDisablingOnWindows() throws Exception {
        System.out.format("testCase = %s%n", testCase);
        AtomicBoolean reachedPreBefore = new AtomicBoolean(false);
        RuleUser ruleUser = new RuleUser(testCase.rule) {
            @Override
            protected void getControllerAndUse(XvfbRule rule) throws Exception {
                if (testCase.invokeGetController) {
                    super.getControllerAndUse(rule);
                }
            }

            @Override
            protected void preBefore() throws Exception {
                reachedPreBefore.set(true);
            }

            @Override
            protected void postBefore() throws Exception {
                assertFalse("made it past before() with eager start", testCase.eagerStart);
            }

            @Override
            protected void preAfter() throws Exception {
            }

            @Override
            protected void postAfter() throws Exception {
            }

            @Override
            protected void use(XvfbController ctrl) throws Exception {
                fail("this should never be called because we overrode getControllerAndUse");
            }
        };
        try {
            ruleUser.test();
            assertFalse("exception should be thrown if invokeGetController is true", testCase.invokeGetController);
        } catch (SubprocessLaunchException e) {
            assertTrue("cause should be IOException 'Cannot run program'", e.getCause() instanceof IOException);
            assertTrue("no exception should be thrown when invokeGetController is false: " + e, testCase.invokeGetController || testCase.eagerStart);
        }
        assertTrue("preBefore was reached by " + testCase, reachedPreBefore.get());
    }
}
