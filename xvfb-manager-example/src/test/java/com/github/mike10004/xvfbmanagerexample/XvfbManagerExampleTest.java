package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbunittesthelp.Assumptions;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

public class XvfbManagerExampleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

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

    private static final String host = "localhost";

    private void runMainWithArgs(String browserKey) throws IOException, InterruptedException {
        int port = mockServerRule.getPort().intValue();
        MockServerClient server = new MockServerClient(host, port);
        String path = "/";
        String html = "<html><head><title>Example</title></head><body><img src=\"cat.jpg\"></body></html>";
        byte[] imageBytes = Resources.toByteArray(XvfbManagerExample.class.getResource("/smiling-cat-in-public-domain.jpg"));
        server.when(HttpRequest.request(path)).respond(HttpResponse.response(html).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.HTML_UTF_8.toString()));
        server.when(HttpRequest.request(path + "cat.jpg")).respond(HttpResponse.response().withBody(imageBytes).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.JPEG.toString()));
        URL url = new URL("http", host, port, path);
        File screenshotFile = tmp.newFile("screenshot.png");
        XvfbManagerExample.browseAndCaptureScreenshot(browserKey, url, screenshotFile);
        BufferedImage image = ImageIO.read(screenshotFile);
        System.out.format("%dx%d screenshot saved to %s%n", image.getWidth(), image.getHeight(), screenshotFile);
    }

}