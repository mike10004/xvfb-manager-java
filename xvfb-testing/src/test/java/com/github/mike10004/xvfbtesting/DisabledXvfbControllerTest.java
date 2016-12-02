package com.github.mike10004.xvfbtesting;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static org.junit.Assert.*;

public class DisabledXvfbControllerTest {

    @Test
    public void configureEnvironment() throws Exception {
        assertEquals(ImmutableMap.of(), DisabledXvfbController.getInstance().configureEnvironment(ImmutableMap.of()));
    }

}