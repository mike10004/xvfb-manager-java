package com.github.mike10004.xvfbunittesthelp;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class PackageManagerTest {

    @Test
    public void parseMajorMinor() throws Exception {
        Map<String, int[]> testCases = ImmutableMap.<String, int[]>builder()
                .put("8.25-2ubuntu2", new int[]{8, 25})
                .put("2.23-0ubuntu4", new int[]{2, 23})
                .put("2.2.52-3", new int[]{2, 2})
                .put("1:3.18.2-1ubuntu1", new int[]{3, 18})
                .put("2:1.18.4-0ubuntu0.1", new int[]{1, 18})
                .put("1:3.18.3-0ubuntu1.16.04.1", new int[]{3, 18})
                .put("0.12+16.04.20160126-0ubuntu1", new int[]{0, 12})
                .put("15.10", new int[]{15, 10})
                .put("16.04", new int[]{16, 4})
                .put("0.142", new int[]{0, 142})
                .put("0.47ubuntu8.3", new int[]{0, 47})
                .put("3.0pl1-128ubuntu2", new int[]{3, 0})
                .put("003.02.01-9ubuntu3", new int[]{3, 2})
                .put("9.20160110ubuntu0.2", new int[]{9, 20160110})
                .build();
        for (String version : testCases.keySet()) {
            int[] expected = testCases.get(version);
            int[] actual = PackageManager.parseMajorMinor(version);
            assertArrayEquals("major.minor not correctly parsed from " + version, expected, actual);
        }
        // 229-4ubuntu11
    }

    @Test
    public void parseMajorMinor_unexpected() {
        Iterable<String> versions = Arrays.asList("229-4ubuntu11", "20160104ubuntu1", "481-2.1ubuntu0.1");
        for (String version : versions) {
            try {
                PackageManager.parseMajorMinor(version);
                fail("should have failed on " + version);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

}