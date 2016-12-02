package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.google.common.base.Predicates;
import com.novetta.ibg.common.sys.Platforms;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class WindowsXvfbRuleTest {

    @BeforeClass
    public static void checkPlatform() {
        Assume.assumeTrue("this test is only for Windows", Platforms.getPlatform().isWindows());
    }

    @Test
    public void testDisablingOnWindows() throws Exception {
        XvfbRule rule = XvfbRule.builder().disabledOnWindows().build();
        new RuleUser(rule) {
            @Override
            protected void use(XvfbController ctrl) throws Exception {
                assertEquals("display", null, ctrl.getDisplay());
                assertNotNull("screenshooter", ctrl.getScreenshooter());
                assertFalse("window", ctrl.pollForWindow(Predicates.alwaysTrue(), 1000, 10).isPresent());

                // check that these do not throw exceptions
                ctrl.waitUntilReady();
                ctrl.waitUntilReady(1000, 10);
                ctrl.stop();
            }
        }.test();
    }
}
