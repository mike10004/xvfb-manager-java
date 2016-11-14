/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.novetta.ibg.common.sys.Platforms;
import com.novetta.ibg.common.sys.Whicher;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class PackageManager {

    private PackageManager() {
    }

    public abstract boolean queryPackageInstalled(String packageName) throws IOException;

    public abstract boolean checkImageMagickInstalled() throws IOException;

    public abstract boolean checkAutoDisplaySupport() throws IOException;

    public abstract boolean checkPackageVersion(String packageName, int minimumMajor, int minimumMinor) throws IOException;

    public abstract boolean checkPackageVersion(String packageName, Predicate<String> versionPredicate) throws IOException;

    public abstract String queryPackageVersion(String packageName) throws IOException;

    protected static Predicate<String> minimumMajorMinorPredicate(final int minimumMajor, int minimumMinor) {
        return new Predicate<String>() {
            @Override
            public boolean apply(String version) {
                Matcher m = Pattern.compile("(?:\\d+:)?(\\d+)\\.(\\d+)\\b.*").matcher(version);
                if (m.find()) {
                    String majorStr = m.group(1);
                    String minorStr = m.group(2);
                    int major = Integer.parseInt(majorStr), minor = Integer.parseInt(minorStr);
                    System.out.format("version \"%s\" parsed to (major, minor) = (%d, %d)%n", version, major, minor);
                    return major > minimumMajor || (major >= minimumMajor && minor >= minimumMinor);
                }
                System.err.format("version does not match expected format: %s%n", version);
                return false;
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

    private static class DebianPackageManager extends PackageManager {

        @Override
        public boolean queryPackageInstalled(String packageName) {
            try {
                ProgramWithOutputStringsResult result = Program.running("dpkg-query")
                        .args("-l", packageName)
                        .outputToStrings()
                        .execute();
                System.out.format("dpkg-query: %d%n", result.getExitCode());
                if (result.getExitCode() == 0) {
                    System.out.println(result.getStdoutString());
                } else {
                    System.out.println(result.getStderrString());
                }
                return result.getExitCode() == 0;
            } catch (Exception e) {
                System.err.println("failed to query package status");
                e.printStackTrace(System.err);
                return false;
            }
        }

        @Override
        public boolean checkImageMagickInstalled() {
            String[] progs = {"mogrify", "convert", "identify"};
            Whicher whicher = Whicher.gnu();
            for (String prog : progs) {
                if (!whicher.which(prog).isPresent()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean checkAutoDisplaySupport() throws IOException {
            // https://stackoverflow.com/questions/2520704/find-a-free-x11-display-number
            return checkPackageVersion("xvfb", 1, 13);
        }

        @Override
        public boolean checkPackageVersion(String packageName, int minimumMajor, int minimumMinor) throws IOException {
            return checkPackageVersion(packageName, minimumMajorMinorPredicate(minimumMajor, minimumMinor));
        }

        @Override
        public boolean checkPackageVersion(String packageName, Predicate<String> versionPredicate) throws IOException {
            String version = queryPackageVersion(packageName);
            return versionPredicate.apply(version);
        }

        @Override
        public String queryPackageVersion(String packageName) throws IOException {
            ProgramWithOutputStringsResult result = Program.running("dpkg-query")
                    .args("--status", packageName)
                    .outputToStrings()
                    .execute();
            String stdout = result.getStdoutString();
            String version = CharSource.wrap(stdout).readLines(new LineProcessor<String>() {

                private String version;

                @Override
                public boolean processLine(String line) throws IOException {
                    if (line.startsWith("Version: ")) {
                        version = StringUtils.removeStart(line, "Version: ").trim();
                        return false;
                    }
                    return true;
                }

                @Override
                public String getResult() {
                    return version;
                }
            });
            return version;
        }
    }
}
