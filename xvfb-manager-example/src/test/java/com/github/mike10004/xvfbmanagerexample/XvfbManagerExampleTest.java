package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbunittesthelp.Assumptions;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.junit.Assume;
import org.junit.Before;
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
        Assumptions.assumeTrue("firefox must be installed", PackageManager.getInstance().queryCommandExecutable("firefox"));
        runMainWithArgs("firefox");
    }

    @org.junit.Test
    public void main_chrome() throws IOException {
        Assumptions.assumeTrue("chrome or chromium must be installed", PackageManager.getInstance().queryAnyCommandExecutable(Arrays.asList("chromium", "google-chrome")));
        runMainWithArgs("chrome");
    }

    private static final String host = "localhost";

    private void runMainWithArgs(String browserKey) throws IOException {
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