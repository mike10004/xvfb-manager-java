package com.github.mike10004.xvfbmanagerexample;

import java.io.IOException;

public class XvfbManagerExampleTest {

    @org.junit.Test
    public void main_firefox() throws IOException {
        runMainWithArgs("firefox");
    }

    @org.junit.Test
    public void main_chrome() throws IOException {
        runMainWithArgs("chrome");
    }

    private void runMainWithArgs(String browserKey) throws IOException {
        XvfbManagerExample.browseAndCaptureScreenshot(browserKey);
    }

}