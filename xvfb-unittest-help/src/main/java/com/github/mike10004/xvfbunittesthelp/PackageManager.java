/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbunittesthelp;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.novetta.ibg.common.sys.Whicher;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class PackageManager {

    private static final Logger log = Logger.getLogger(PackageManager.class.getName());

    PackageManager() {
    }

    public abstract boolean queryPackageInstalled(String packageName) throws IOException;

    public abstract boolean checkImageMagickInstalled() throws IOException;

    public abstract boolean checkAutoDisplaySupport() throws IOException;

    public abstract boolean checkPackageVersion(String packageName, int minimumMajor, int minimumMinor) throws IOException;

    public abstract boolean checkPackageVersion(String packageName, Predicate<String> versionPredicate) throws IOException;

    public abstract String queryPackageVersion(String packageName) throws IOException;

    protected static int[] parseMajorMinor(String version) throws IllegalArgumentException {
        Matcher m = Pattern.compile("^(?:\\d+:)?(\\d+)\\.(\\d+)(?:\\D.*)?$").matcher(version);
        if (m.find()) {
            String majorStr = m.group(1);
            String minorStr = m.group(2);
            checkArgument(!Strings.isNullOrEmpty(majorStr), "pattern matched but major is null or empty in '%s'", version);
            checkArgument(!Strings.isNullOrEmpty(minorStr), "pattern matched but minor is null or empty in '%s'", version);
            int major = Integer.parseInt(majorStr), minor = Integer.parseInt(minorStr);
            log.finer(String.format("version \"%s\" parsed to (major, minor) = (%d, %d)%n", version, major, minor));
            return new int[]{major, minor};
        } else {
            throw new IllegalArgumentException("version does not match expected pattern: " + version);
        }
    }

    protected static Predicate<String> minimumMajorMinorPredicate(final int minimumMajor, int minimumMinor) {
        return new Predicate<String>() {
            @Override
            public boolean apply(String version) {
                int[] majorMinor;
                try {
                    majorMinor = parseMajorMinor(version);
                } catch (IllegalArgumentException e) {
                    log.warning("could not parse major/minor from version due to " + e);
                    return false;
                }
                int major = majorMinor[0], minor = majorMinor[1];
                return major > minimumMajor || (major >= minimumMajor && minor >= minimumMinor);
            }
        };
    }

    private static final Supplier<PackageManager> instanceSupplier = Suppliers.memoize(new Supplier<PackageManager>() {

        @Override
        public PackageManager get() {
            Whicher whicher = Whicher.gnu();
            if (whicher.which("dpkg").isPresent()) {
                return new DebianPackageManager();
            } else {
                Logger.getLogger(PackageManager.class.getName()).warning("operating system not fully supported; " +
                        "will not be able to determine installation status of packages");
                return new FalseyPackageManager();
            }
        }
    });

    public static PackageManager getInstance() {
        return instanceSupplier.get();
    }

    private static class FalseyPackageManager extends PackageManager {

        @Override
        public boolean queryPackageInstalled(String packageName) throws IOException {
            return false;
        }

        @Override
        public boolean checkImageMagickInstalled() throws IOException {
            return false;
        }

        @Override
        public boolean checkAutoDisplaySupport() throws IOException {
            return false;
        }

        @Override
        public boolean checkPackageVersion(String packageName, int minimumMajor, int minimumMinor) throws IOException {
            return false;
        }

        @Override
        public boolean checkPackageVersion(String packageName, Predicate<String> versionPredicate) throws IOException {
            return false;
        }

        @Override
        public String queryPackageVersion(String packageName) throws IOException {
            throw new IOException("unable to determine package version of " + StringUtils.abbreviate(packageName, 64));
        }
    }

}
