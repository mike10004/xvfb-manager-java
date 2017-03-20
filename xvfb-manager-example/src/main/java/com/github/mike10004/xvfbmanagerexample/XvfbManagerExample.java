/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.github.mike10004.xvfbselenium.WebDriverSupport;
import com.google.common.base.Joiner;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.bonigarcia.wdm.MarionetteDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class XvfbManagerExample {

    public static final String ENV_FIREFOX_BIN = "FIREFOX_BIN";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) {
            System.err.format("Syntax:%n    chrome http://example.com screenshot.png%n    firefox http://example.com screenshot.png%nExactly 3 arguments required.%n");
            System.exit(1);
        }
        String browserKey = args[0];
        if (!WebDriverAsset.SUPPORTED.contains(browserKey)) {
            System.err.format("first argument must be one of {%s}%n", Joiner.on(", ").join(WebDriverAsset.SUPPORTED));
            System.exit(1);
        }
        URL url = new URL(args[1]);
        File screenshotFile = new File(args[2]);
        browseAndCaptureScreenshot(browserKey, url, screenshotFile);
    }

    public static void browseAndCaptureScreenshot(String browserKey, URL url, File screenshotFile) throws IOException, InterruptedException {
        BufferedImage screenshotImage;
        XvfbManager xvfb = new XvfbManager();
        try (XvfbController ctrl = xvfb.start()) {
            ctrl.waitUntilReady();
            screenshotImage = browse(browserKey, url, ctrl.getDisplay());
        }
        System.out.format("captured %dx%d screenshot%n", screenshotImage.getWidth(), screenshotImage.getHeight());
        ImageIO.write(screenshotImage, "png", screenshotFile);
    }

    /**
     * Visit a given URL and return a screenshot.
     * @param url the url
     * @return an image captured by the browser
     * @throws IOException
     */
    private static BufferedImage browse(String browserKey, URL url, String display) throws IOException {
        WebDriverAsset asset = WebDriverAsset.getAsset(browserKey);
        System.out.format("browsing %s on display %s with %s%n", url, display, browserKey);
        asset.setupDriver();
        WebDriver webDriver = asset.createDriver(display);
        try {
            webDriver.get(url.toString());
            byte[] screenshotBytes = ((TakesScreenshot)webDriver).getScreenshotAs(OutputType.BYTES);
            BufferedImage screenshotImage = ImageIO.read(new ByteArrayInputStream(screenshotBytes));
            return screenshotImage;
        } finally {
            webDriver.quit();
        }
    }

    private interface WebDriverAsset {
        String FIREFOX_DRIVER_VERSION = "0.14.0";
        String CHROME_DRIVER_VERSION = "2.27";
        Collection<String> SUPPORTED = Collections.unmodifiableList(Arrays.asList("chrome", "firefox"));
        void setupDriver();
        WebDriver createDriver(String display);
        static WebDriverAsset getAsset(String browserKey) {
            switch (browserKey) {
                case "firefox":
                    String firefoxBin = System.getenv(ENV_FIREFOX_BIN);
                    return new WebDriverAsset() {
                        @Override
                        public void setupDriver() {
                            MarionetteDriverManager.getInstance().setup(FIREFOX_DRIVER_VERSION);
                        }

                        @Override
                        public WebDriver createDriver(String display) {
                            if (firefoxBin != null) {
                                System.out.format("using firefox binary path from environment variable %s: %s%n", ENV_FIREFOX_BIN, firefoxBin);
                                return WebDriverSupport.firefoxOnDisplay(display)
                                        .create(new FirefoxBinary(new File(firefoxBin)), new FirefoxProfile());
                            } else {
                                return WebDriverSupport.firefoxOnDisplay(display).create();
                            }
                        }
                    };
                case "chrome":
                    return new WebDriverAsset() {
                        @Override
                        public void setupDriver() {
                            ChromeDriverManager.getInstance().setup(CHROME_DRIVER_VERSION);
                        }

                        @Override
                        public WebDriver createDriver(String display) {
                            return WebDriverSupport.chromeOnDisplay(display).create();
                        }
                    };
                default:
                    throw new IllegalArgumentException("browser not supported: " + browserKey);
            }
        }
    }

}
