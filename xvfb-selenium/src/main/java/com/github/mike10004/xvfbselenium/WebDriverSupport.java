/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbselenium;

import com.google.common.collect.ImmutableMap;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.openqa.selenium.firefox.FirefoxOptions.FIREFOX_OPTIONS;
import static org.openqa.selenium.firefox.FirefoxOptions.OLD_FIREFOX_OPTIONS;
import static org.openqa.selenium.remote.CapabilityType.ACCEPT_SSL_CERTS;
import static org.openqa.selenium.remote.CapabilityType.HAS_NATIVE_EVENTS;
import static org.openqa.selenium.remote.CapabilityType.LOGGING_PREFS;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_WEB_STORAGE;

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
            return create(binary, profile, new DesiredCapabilities());
        }

        public FirefoxDriver create(Capabilities desiredCapabilities) {
            return create(new FirefoxBinary(), new FirefoxProfile(), desiredCapabilities);
        }

        public FirefoxDriver create(FirefoxBinary binary, FirefoxProfile profile, Capabilities desiredCapabilities) {
            for (String variableName : environment.keySet()) {
                binary.setEnvironmentProperty(variableName, environment.get(variableName));
            }
            // What we want:
            //     WebDriver driver = new FirefoxDriver(binary, profile, capabilities);
            // What we're forced to do because the FirefoxBinary's environment is not passed to GeckoDriverService:
            GeckoDriverService driverService = buildDriverService(binary);
            Capabilities desiredCapabilitiesWithProfileProperties = FirefoxProfiles.populateProfile(profile, desiredCapabilities);
            Capabilities requiredCapabilities = null;
            FirefoxDriver driver = new FirefoxDriver(driverService, desiredCapabilitiesWithProfileProperties, requiredCapabilities);
            return driver;
        }

        private static GeckoDriverService buildDriverService(FirefoxBinary binary) {
            checkNotNull(binary, "binary");
            GeckoDriverService.Builder builder = new GeckoDriverService.Builder(binary);
            builder.withEnvironment(binary.getExtraEnv());
            builder.usingPort(0);
            return builder.build();
        }

        private static class FirefoxProfiles {

            private FirefoxProfiles () {}

            public static Capabilities populateProfile(FirefoxProfile profile, Capabilities capabilities) {
                checkNotNull(profile, "profile");
                checkNotNull(capabilities, "capabilities");
                if (capabilities.getCapability(SUPPORTS_WEB_STORAGE) != null) {
                    Boolean supportsWebStorage = (Boolean) capabilities.getCapability(SUPPORTS_WEB_STORAGE);
                    profile.setPreference("dom.storage.enabled", supportsWebStorage.booleanValue());
                }
                if (capabilities.getCapability(ACCEPT_SSL_CERTS) != null) {
                    Boolean acceptCerts = (Boolean) capabilities.getCapability(ACCEPT_SSL_CERTS);
                    profile.setAcceptUntrustedCertificates(acceptCerts);
                }
                if (capabilities.getCapability(LOGGING_PREFS) != null) {
                    LoggingPreferences logsPrefs =
                            (LoggingPreferences) capabilities.getCapability(LOGGING_PREFS);
                    for (String logtype : logsPrefs.getEnabledLogTypes()) {
                        profile.setPreference("webdriver.log." + logtype,
                                logsPrefs.getLevel(logtype).intValue());
                    }
                }

                if (capabilities.getCapability(HAS_NATIVE_EVENTS) != null) {
                    Boolean nativeEventsEnabled = (Boolean) capabilities.getCapability(HAS_NATIVE_EVENTS);
                    profile.setEnableNativeEvents(nativeEventsEnabled);
                }

                Object rawOptions = capabilities.getCapability(FIREFOX_OPTIONS);
                if (rawOptions == null) {
                    rawOptions = capabilities.getCapability(OLD_FIREFOX_OPTIONS);
                }
                if (rawOptions != null && !(rawOptions instanceof FirefoxOptions)) {
                    throw new WebDriverException("Firefox option was set, but is not a FirefoxOption: " + rawOptions);
                }
                FirefoxOptions options = (FirefoxOptions) rawOptions;
                if (options == null) {
                    options = new FirefoxOptions();
                }
                // options.setProfileSafely(profile); // package-private, but we can use setProfile because we know existing profile is null
                options.setProfile(profile);

                DesiredCapabilities toReturn = capabilities instanceof DesiredCapabilities ?
                        (DesiredCapabilities) capabilities :
                        new DesiredCapabilities(capabilities);
                toReturn.setCapability(OLD_FIREFOX_OPTIONS, options);
                toReturn.setCapability(FIREFOX_OPTIONS, options);
                return toReturn;
            }

        }
    }

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

        public ChromeDriver create(Capabilities capabilities) {
            return create(createEnvironmentlessDriverServiceBuilder(), capabilities);
        }

        public ChromeDriver create(ChromeOptions options) {
            return create(createEnvironmentlessDriverServiceBuilder(), options);
        }

        public ChromeDriver create(ChromeDriverService.Builder builder, ChromeOptions options) {
            return new ChromeDriver(buildService(builder), options);
        }

        public ChromeDriver create(ChromeDriverService.Builder builder, Capabilities capabilities) {
            return new ChromeDriver(buildService(builder), capabilities);
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
