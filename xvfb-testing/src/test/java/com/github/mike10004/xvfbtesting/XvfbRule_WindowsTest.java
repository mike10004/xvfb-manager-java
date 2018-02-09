package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Platforms;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.google.common.base.Suppliers;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class XvfbRule_WindowsTest {

    private final XvfbRule rule;

    public XvfbRule_WindowsTest(XvfbRule rule) {
        this.rule = rule;
    }

    @ClassRule
    public static PlatformRule platformRule = PlatformRule.requireWindows();

    @Parameterized.Parameters
    public static List<XvfbRule> rules() {
        return Arrays.asList(
                XvfbRule.builder().build(),
                XvfbRule.builder().eager().build(),
                XvfbRule.builder().disabled().build(),
                XvfbRule.builder().disabled(true).build(),
                XvfbRule.builder().disabled(Suppliers.ofInstance(true)).build(),
                XvfbRule.builder().eager().disabled().build(),
                XvfbRule.builder().eager().disabled(true).build(),
                XvfbRule.builder().eager().disabled(Suppliers.ofInstance(true)).build(),
                XvfbRule.builder().notDisabledOnWindows().disabled(true).build(),
                XvfbRule.builder().notDisabledOnWindows().disabled(Suppliers.ofInstance(true)).build()
        );
    }

    @Test
    public void testDisablingOnWindows() throws Exception {
        System.out.format("rule = %s%n", rule);
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
