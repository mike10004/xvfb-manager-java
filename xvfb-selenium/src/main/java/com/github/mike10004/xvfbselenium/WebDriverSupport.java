/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbselenium;

import com.google.common.collect.ImmutableMap;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.GeckoDriverService;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class WebDriverSupport {

    private WebDriverSupport(){}

    public static ChromeInCustomEnvironment chromeOnDisplay(String display) {
        return chromeInEnvironment(createEnvironmentWithDisplay(display));
    }

    public static ChromeInCustomEnvironment chromeInEnvironment(Map<String, String> environment) {
        return new ChromeInCustomEnvironment(environment);
    }

    public static FirefoxInCustomEnvironment firefoxInEnvironment(Map<String, String> environment) {
        return new FirefoxInCustomEnvironment(environment);
    }

    private static ImmutableMap<String, String> createEnvironmentWithDisplay(String display) {
        return ImmutableMap.of("DISPLAY", checkNotNull(display, "display must be non-null, e.g. \":1\""));
    }

    public static FirefoxInCustomEnvironment firefoxOnDisplay(String display) {
        return firefoxInEnvironment(createEnvironmentWithDisplay(display));
    }

    public static class FirefoxInCustomEnvironment {

        private final Map<String, String> environment;

        public FirefoxInCustomEnvironment(Map<String, String> environment) {
            this.environment = checkNotNull(environment);
        }

        public FirefoxDriver create() {
            return create(new FirefoxBinary(), new FirefoxProfile());
        }

        public FirefoxDriver create(FirefoxBinary binary, FirefoxProfile profile) {
            return create(binary, profile, new FirefoxOptions());
        }

        public FirefoxDriver create(FirefoxOptions options) {
            return create(new FirefoxBinary(), new FirefoxProfile(), options);
        }

        public FirefoxDriver create(FirefoxBinary binary, FirefoxProfile profile, FirefoxOptions options) {
            options.setProfile(profile);
            GeckoDriverService service = new GeckoDriverService.Builder()
                    .usingAnyFreePort()
                    .withEnvironment(environment)
                    .build();
            FirefoxDriver driver = new FirefoxDriver(service, options);
            return driver;
        }

    }

    @SuppressWarnings("deprecation")
    public static class ChromeInCustomEnvironment {

        private final Map<String, String> environment;

        private ChromeInCustomEnvironment(Map<String, String> environment) {
            this.environment = ImmutableMap.copyOf(environment);
        }

        public ChromeDriver create() {
            return create(createEnvironmentlessDriverServiceBuilder());
        }

        public ChromeDriver create(ChromeDriverService.Builder builder) {
            return new ChromeDriver(buildService(builder));
        }

        public ChromeDriver create(ChromeOptions options) {
            return create(createEnvironmentlessDriverServiceBuilder(), options);
        }

        public ChromeDriver create(ChromeDriverService.Builder builder, ChromeOptions options) {
            return new ChromeDriver(buildService(builder), options);
        }

        protected ChromeDriverService buildService(ChromeDriverService.Builder builder) {
            builder.withEnvironment(environment);
            return builder.build();
        }

        protected ChromeDriverService.Builder createEnvironmentlessDriverServiceBuilder() {
            ChromeDriverService.Builder builder = new ChromeDriverService.Builder().usingAnyFreePort();
            return builder;
        }
    }

}
