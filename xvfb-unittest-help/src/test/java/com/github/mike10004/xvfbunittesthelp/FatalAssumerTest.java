package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.xvfbunittesthelp.FatalAssumer.AssumptionViolatedError;
import org.junit.Test;

import static org.junit.Assert.*;

public class FatalAssumerTest {

    @Test
    public void format() throws Exception {
        String actual = FatalAssumer.format("hello %s", "world");
        assertEquals("formatted", "hello world", actual);
    }

    @Test
    public void assumeTrue_message() throws Exception {
        String message = "hello world";
        try {
            new FatalAssumer().assumeTrue(message, false);
        } catch (AssumptionViolatedError e) {
            assertEquals("message", message, e.getMessage());
        }
    }

}