package com.github.mike10004.xvfbmanagerexample;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.bonigarcia.wdm.FirefoxDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.remote.RemoteWebDriver;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class XvfbManagerExample {

    public static final String ENV_FIREFOX_BIN = "FIREFOX_BIN";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) {
            System.err.format("Syntax:%n" +
                    "    chrome http://example.com screenshot.png%n" +
                    "    firefox http://example.com screenshot.png%n" +
                    "Exactly 3 arguments required.%n");
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
     */
    private static BufferedImage browse(String browserKey, URL url, String display) throws IOException {
        WebDriverAsset asset = WebDriverAsset.getAsset(browserKey);
        System.out.format("browsing %s on display %s with %s%n", url, display, browserKey);
        asset.setupDriver();
        WebDriver webDriver = asset.createDriver(display);
        checkState(((RemoteWebDriver)webDriver).getSessionId() != null, "no session id in %s", webDriver);
        try {
            webDriver.get(url.toString());
            byte[] screenshotBytes = ((TakesScreenshot)webDriver).getScreenshotAs(OutputType.BYTES);
            BufferedImage screenshotImage = ImageIO.read(new ByteArrayInputStream(screenshotBytes));
            System.out.format("returning screenshot %s%n", screenshotImage);
            return screenshotImage;
        } finally {
            webDriver.quit();
        }
    }

    private static FirefoxDriver create(Map<String, String> environment, @Nullable FirefoxBinary binary, FirefoxProfile profile, FirefoxOptions options) {
        options.setProfile(profile);
        GeckoDriverService.Builder serviceBuilder = new GeckoDriverService.Builder()
                .usingAnyFreePort()
                .withEnvironment(environment);
        if (binary != null) {
            serviceBuilder.usingFirefoxBinary(binary);
        }
        FirefoxDriver driver = new FirefoxDriver(serviceBuilder.build(), options);
        return driver;
    }

    private interface WebDriverAsset {
        String FIREFOX_DRIVER_VERSION = "0.16.1";
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
                            WebDriverManager mgr = FirefoxDriverManager.getInstance();
                            mgr.version(FIREFOX_DRIVER_VERSION).setup();
                        }

                        @Override
                        public WebDriver createDriver(String display) {
                            Map<String, String> env = ImmutableMap.of("DISPLAY", display);
                            if (firefoxBin != null) {
                                System.out.format("using firefox binary path from environment variable %s: %s%n", ENV_FIREFOX_BIN, firefoxBin);
                                return create(env, new FirefoxBinary(new File(firefoxBin)), new FirefoxProfile(), new FirefoxOptions());
                            } else {
                                return create(env, null, new FirefoxProfile(), new FirefoxOptions());
                            }
                        }
                    };
                case "chrome":
                    return new WebDriverAsset() {
                        @Override
                        public void setupDriver() {
                            ChromeDriverManager.getInstance().version(CHROME_DRIVER_VERSION).setup();
                        }

                        @Override
                        public WebDriver createDriver(String display) {
                            ChromeDriverService.Builder builder = new ChromeDriverService.Builder().usingAnyFreePort();
                            builder.withEnvironment(ImmutableMap.of("DISPLAY", display));
                            ChromeOptions options = new ChromeOptions();
                            return new ChromeDriver(builder.build(), options);
                        }
                    };
                default:
                    throw new IllegalArgumentException("browser not supported: " + browserKey);
            }
        }
    }

}
