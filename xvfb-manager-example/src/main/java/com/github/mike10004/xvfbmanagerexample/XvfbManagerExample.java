/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.github.mike10004.xvfbselenium.WebDriverSupport;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkArgument;

public class XvfbManagerExample {

    public static final String ENV_FIREFOX_BIN = "FIREFOX_BIN";

    public static void main(String[] args) throws IOException {
        if (args.length < 0 || !browserMap.containsKey(args[0])) {
            System.err.format("first argument must be one of {%s}%n", Joiner.on(", ").join(browserMap.keySet()));
            System.exit(1);
        }
        if (args.length < 1) {
            System.err.println("second argument must be screenshot output pathname");
            System.exit(1);
        }
        File screenshotFile = new File(args[1]);
        browseAndCaptureScreenshot(args[0], screenshotFile);
    }

    public static void browseAndCaptureScreenshot(String browserKey, File screenshotFile) throws IOException {
        ClientAndServer server = ClientAndServer.startClientAndServer(0);
        try {
            String host = "localhost", path = "/";
            int port = server.getPort();
            String html = "<html><head><title>Example</title></head><body><img src=\"cat.jpg\"></body></html>";
            byte[] imageBytes = Resources.toByteArray(XvfbManagerExample.class.getResource("/smiling-cat-in-public-domain.jpg"));
            server.when(HttpRequest.request(path)).respond(HttpResponse.response(html).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.HTML_UTF_8.toString()));
            server.when(HttpRequest.request(path + "cat.jpg")).respond(HttpResponse.response().withBody(imageBytes).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.JPEG.toString()));
            URL url = new URL("http", host, port, path);
            BufferedImage screenshotImage;
            XvfbManager xvfb = new XvfbManager();
            try (XvfbController ctrl = xvfb.start()) {
                screenshotImage = browse(browserKey, url, ctrl.getDisplay());
            }
            System.out.format("captured %dx%d screenshot%n", screenshotImage.getWidth(), screenshotImage.getHeight());
            ImageIO.write(screenshotImage, "png", screenshotFile);
        } finally {
            server.stop();
        }
    }

    /**
     * Visit a given URL and return a screenshot.
     * @param url the url
     * @return an image captured by the browser
     * @throws IOException
     */
    private static BufferedImage browse(String browserKey, URL url, String display) throws IOException {
        Pair<Class<? extends WebDriver>, Function<String, ? extends WebDriver>> driverStuff = browserMap.get(browserKey);
        checkArgument(driverStuff != null, "unsupported browser: %s", browserKey);
        System.out.format("browsing %s on display %s with %s%n", url, display, driverStuff.getLeft());
        Class<? extends WebDriver> webDriverClass = driverStuff.getLeft();
        WebDriverManager.getInstance(webDriverClass).setup();
        WebDriver webDriver = driverStuff.getRight().apply(display);
        try {
            webDriver.get(url.toString());
            byte[] screenshotBytes = ((TakesScreenshot)webDriver).getScreenshotAs(OutputType.BYTES);
            BufferedImage screenshotImage = ImageIO.read(new ByteArrayInputStream(screenshotBytes));
            return screenshotImage;
        } finally {
            webDriver.quit();
        }
    }

    private static final ImmutableMap<String, Pair<Class<? extends WebDriver>, Function<String, ? extends WebDriver>>> browserMap =
            ImmutableMap.<String, Pair<Class<? extends WebDriver>, Function<String, ? extends WebDriver>>>builder()
            .put("firefox", Pair.of(org.openqa.selenium.firefox.MarionetteDriver.class, new Function<String, FirefoxDriver>(){
                @Override
                public FirefoxDriver apply(String display) {
                    String firefoxBin = System.getenv(ENV_FIREFOX_BIN);
                    if (firefoxBin != null) {
                        System.out.format("using firefox binary path from environment variable %s: %s%n", ENV_FIREFOX_BIN, firefoxBin);
                        return WebDriverSupport.firefoxOnDisplay(display).create(new FirefoxBinary(new File(firefoxBin)), new FirefoxProfile());
                    } else {
                        return WebDriverSupport.firefoxOnDisplay(display).create();
                    }
                }
            }))
            .put("chrome", Pair.of(ChromeDriver.class, new Function<String, ChromeDriver>(){
                @Override
                public ChromeDriver apply(String display) {
                    return WebDriverSupport.chromeOnDisplay(display).create();
                }
            }))
            .build();
}
