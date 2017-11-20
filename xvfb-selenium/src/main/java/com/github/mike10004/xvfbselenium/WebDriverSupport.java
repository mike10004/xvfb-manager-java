/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbselenium;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

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

        public FirefoxDriver create(FirefoxOptions desiredCapabilities) {
            return create(new FirefoxBinary(), new FirefoxProfile(), desiredCapabilities);
        }

        public FirefoxDriver create(FirefoxBinary binary, FirefoxProfile profile, FirefoxOptions desiredCapabilities) {
            return createWithGeckodriverWrapper(binary, profile, desiredCapabilities);
        }

        private static final String PROPNAME_GECKO = "webdriver.gecko.driver";

        private static boolean hasNoWhitespace(String str) {
            return CharMatcher.whitespace().matchesNoneOf(str);
        }

        /*
         * This is hacky af, but as of selenium-java 3.4.0, we can't provide
         * the FirefoxDriver constructor our own GeckoDriverService with the
         * appropriate environment.
         */
        @SuppressWarnings("UnusedReturnValue")
        private static File swapGeckodriverForWrapperScript(Map<String, String> environment) {
            try {
                String geckodriverPath = System.getProperty(PROPNAME_GECKO);
                checkState(geckodriverPath != null, "must have system property " + PROPNAME_GECKO + " defined");
                checkState(hasNoWhitespace(geckodriverPath), "can't handle whitespace in geckodriver executable path '%s'", geckodriverPath);
                File tmpDir = new File(System.getProperty("java.io.tmpdir"));
                File scriptFile = File.createTempFile("geckodriver-wrapper", ".sh", tmpDir);
                scriptFile.deleteOnExit();
                environment.forEach((key, value) -> {
                    checkState(hasNoWhitespace(key), "variable name must be whitespace-free: %s", key);
                    checkState(hasNoWhitespace(value), "variable value must be whitespace-free: %s=%s", key, value);
                });
                String scriptContent = "#!/bin/bash\n" +
                        "exec /usr/bin/env " + Joiner.on(' ').withKeyValueSeparator('=').join(environment) + " " + geckodriverPath + " $@\n";
                CharSource.wrap(scriptContent).copyTo(Files.asCharSink(scriptFile, UTF_8));
                checkState(scriptFile.setExecutable(true), "setExecutable(true) returned false on %s", scriptFile);
                System.setProperty(PROPNAME_GECKO, scriptFile.getAbsolutePath());
                return scriptFile;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private FirefoxDriver createWithGeckodriverWrapper(FirefoxBinary binary, FirefoxProfile profile, FirefoxOptions desiredCapabilities) {
            if (!environment.isEmpty()) {
                swapGeckodriverForWrapperScript(environment);
            }
            FirefoxOptions options = new FirefoxOptions();
            options.setBinary(binary);
            options.setProfile(profile);
            FirefoxOptions mergedCaps = desiredCapabilities.merge(options);
            FirefoxDriver driver = new FirefoxDriver(mergedCaps);
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
