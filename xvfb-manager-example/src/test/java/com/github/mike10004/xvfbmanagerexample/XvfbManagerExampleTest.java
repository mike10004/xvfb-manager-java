package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbunittesthelp.PackageManager;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class XvfbManagerExampleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void checkPrequisites() throws IOException {
        Assume.assumeTrue("xvfb must be installed for these tests", PackageManager.getInstance().queryPackageInstalled("xvfb"));
        Assume.assumeTrue("xvfb version not high enough to test auto-display support", PackageManager.getInstance().checkAutoDisplaySupport());
    }

    /**
     * Copy GitHub API tokens from environment variables to system properties.
     * The webdriver manager downloads some stuff through the GitHub API, which has low
     * quotas for anonymous requests. Set the environment variables {@code GITHUB_TOKEN_NAME}
     * and {@code GITHUB_TOKEN_SECRET} to use your personal token for these download requests.
     * This sets the appropriate system properties (see https://github.com/bonigarcia/webdrivermanager)
     * only if the environment variables are set and the system properties are not yet defined.
     */
    @Before
    public void setGithubTokenSystemProperties() {
        if (System.getProperty("wdm.gitHubTokenName") == null) {
            String tokenName = System.getenv("GITHUB_TOKEN_NAME");
            if (tokenName != null) {
                System.setProperty("wdm.gitHubTokenName", tokenName);
            }
        }
        if (System.getProperty("wdm.gitHubTokenSecret") == null) {
            String tokenSecret = System.getenv("GITHUB_TOKEN_SECRET");
            if (tokenSecret != null) {
                System.setProperty("wdm.gitHubTokenSecret", tokenSecret);
            }
        }


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
        File screenshotFile = tmp.newFile("screenshot.png");
        XvfbManagerExample.browseAndCaptureScreenshot(browserKey, screenshotFile);
        BufferedImage image = ImageIO.read(screenshotFile);
        System.out.format("%dx%d screenshot saved to %s%n", image.getWidth(), image.getHeight(), screenshotFile);
    }

}