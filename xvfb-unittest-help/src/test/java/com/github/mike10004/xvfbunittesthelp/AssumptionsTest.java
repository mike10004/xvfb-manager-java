package com.github.mike10004.xvfbunittesthelp;

import org.hamcrest.Matcher;
import org.junit.Test;

import static org.junit.Assert.*;

public class AssumptionsTest {

    @Test
    public void createFatal() throws Exception {
        String propName = Assumptions.SYSPROP_VIOLATIONS_ARE_FATAL;
        String oldValue = System.getProperty(propName);
        System.setProperty(propName, "true");
        try {
            Assumer assumer = new Assumptions.SystemPropertiesAssumerCreator().get();
            assertTrue(assumer.getClass().getName(), assumer instanceof FatalAssumer);
        } finally {
            if (oldValue != null) {
                System.setProperty(propName, oldValue);
            } else {
                System.clearProperty(propName);
            }
        }
    }

    @Test
    public void createArbitrary() throws Exception {
        String propName = Assumptions.SYSPROP_ASSUMER;
        String oldValue = System.getProperty(propName);
        System.setProperty(propName, ArbitraryAssumer.class.getName());
        try {
            Assumer assumer = new Assumptions.SystemPropertiesAssumerCreator().get();
            assertTrue(assumer.getClass().getName(), assumer instanceof ArbitraryAssumer);
        } finally {
            if (oldValue != null) {
                System.setProperty(propName, oldValue);
            } else {
                System.clearProperty(propName);
            }
        }
    }

    public static class ArbitraryAssumer implements Assumer {

        @Override
        public void assumeTrue(boolean b) {

        }

        @Override
        public void assumeFalse(boolean b) {

        }

        @Override
        public void assumeTrue(String message, boolean b) {

        }

        @Override
        public void assumeFalse(String message, boolean b) {

        }

        @Override
        public void assumeNotNull(Object... objects) {

        }

        @Override
        public <T> void assumeThat(T actual, Matcher<T> matcher) {

        }

        @Override
        public <T> void assumeThat(String message, T actual, Matcher<T> matcher) {

        }

        @Override
        public void assumeNoException(Throwable e) {

        }

        @Override
        public void assumeNoException(String message, Throwable e) {

        }
    }
}