package com.github.mike10004.xvfbmanager;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class DefaultDisplayReadinessCheckerTest {

    @Test
    public void isCorrectOutputForDisplay() throws Exception {
        String requiredDisplay = ":99";
        String stdoutString = "name of display:    :99\nversion number:    11.0\nvendor string:    The X.Org Foundation\nvendor release number:    11804000\nX.Org version: 1.18.4\nmaximum request size:  16777212 bytes\nmotion buffer size:  256\nbitmap unit, bit order, padding:    32, LSBFir...";
//        System.out.println(stdoutString);
        assertEquals("isCorrect...", true, DefaultDisplayReadinessChecker.isCorrectOutputForDisplay(stdoutString, requiredDisplay));
    }

}