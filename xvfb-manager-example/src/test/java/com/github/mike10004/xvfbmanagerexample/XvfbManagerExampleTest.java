package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbunittesthelp.PackageManager;
import org.junit.Assume;
import org.junit.BeforeClass;

import java.io.IOException;

public class XvfbManagerExampleTest {

    @BeforeClass
    public static void checkPrequisites() throws IOException {
        Assume.assumeTrue(PackageManager.queryPackageInstalled("xvfb"));
        Assume.assumeTrue("xvfb version not high enough to test auto-display support", PackageManager.checkAutoDisplaySupport());
    }

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