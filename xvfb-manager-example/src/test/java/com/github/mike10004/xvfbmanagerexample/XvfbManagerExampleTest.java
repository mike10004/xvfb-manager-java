package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbunittesthelp.Assumptions;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.nanochamp.testing.NanoRule;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import static org.apache.http.HttpStatus.SC_OK;

public class XvfbManagerExampleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public NanoRule nanoRule = new NanoRule(buildServer());

    private static final String PAGE_PATH = "/";
    private static final String CAT_PATH = PAGE_PATH + "cat.jpg";
    private static final String PAGE_HTML = "<html><head><title>Example</title></head><body><img src=\"cat.jpg\"></body></html>";
    private static NanoServer buildServer() {
        byte[] imageBytes;
        try {
            imageBytes = Resources.toByteArray(XvfbManagerExample.class.getResource("/smiling-cat-in-public-domain.jpg"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NanoServer.builder()
                .getPath(PAGE_PATH, NanoResponse.status(SC_OK).htmlUtf8(PAGE_HTML))
                .getPath(CAT_PATH, NanoResponse.status(SC_OK).jpeg(imageBytes))
                .build();
    }

    @BeforeClass
    public static void checkPrequisites() throws IOException {
        Assumptions.assumeTrue("Xvfb must be executable for these tests", PackageManager.getInstance().queryCommandExecutable("Xvfb"));
        Assumptions.assumeTrue("xvfb version not high enough to test auto-display support", PackageManager.getInstance().queryAutoDisplaySupport());
        String ghTokenName = System.getProperty("wdm.gitHubTokenName", "");
        String ghTokenSecret = System.getProperty("wdm.gitHubTokenSecret", "");
        Preconditions.checkState(!ghTokenName.startsWith("$")); // indicates env var usage error
        Preconditions.checkState(!ghTokenSecret.startsWith("$"));
        System.out.format("github token name/secret have lengths %d and %d%n", ghTokenName.length(), ghTokenSecret.length());
    }

    @org.junit.Test
    public void main_firefox() throws Exception {
        Assumptions.assumeTrue("firefox must be installed", PackageManager.getInstance().queryCommandExecutable("firefox"));
        Assumptions.assumeTrue("xvfb version not high enough to test firefox browsing", PackageManager.getInstance().checkPackageVersion("xvfb", 1, 18));
        runMainWithArgs("firefox");
    }

    @org.junit.Test
    public void main_chrome() throws Exception {
        Assumptions.assumeTrue("chrome or chromium must be installed", PackageManager.getInstance().queryAnyCommandExecutable(Arrays.asList("chromium-browser", "google-chrome")));
        runMainWithArgs("chrome");
    }

    private void runMainWithArgs(String browserKey) throws IOException, InterruptedException {
        URL url = nanoRule.getControl().baseUri().toURL();
        File screenshotFile = tmp.newFile("screenshot.png");
        XvfbManagerExample.browseAndCaptureScreenshot(browserKey, url, screenshotFile);
        BufferedImage image = ImageIO.read(screenshotFile);
        System.out.format("%dx%d screenshot saved to %s%n", image.getWidth(), image.getHeight(), screenshotFile);
    }

}