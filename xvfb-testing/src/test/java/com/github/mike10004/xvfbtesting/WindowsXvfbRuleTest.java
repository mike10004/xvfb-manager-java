package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Platforms;
import com.github.mike10004.xvfbmanager.XvfbController;
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
        @SuppressWarnings("deprecation")
        XvfbRule rule = XvfbRule.builder().disabledOnWindows().build();
        new RuleUser(rule) {
            @Override
            protected void use(XvfbController ctrl) throws Exception {
                assertEquals("display", null, ctrl.getDisplay());
                assertNotNull("screenshooter", ctrl.getScreenshooter());
                assertFalse("window", ctrl.pollForWindow(x -> true, 1000, 10).isPresent());

                // check that these do not throw exceptions
                ctrl.waitUntilReady();
                ctrl.waitUntilReady(1000, 10);
                ctrl.stop();
            }
        }.test();
    }
}
