package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStrings;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@VisibleForTesting
class BrewPackageManager extends PackageManager {

    private static final Logger log = Logger.getLogger(BrewPackageManager.class.getName());

    private static final ImmutableMap<String, String> packageNameMap = ImmutableMap.of("xvfb", "xquartz", "Xvfb", "xquartz");

    @Override
    public boolean queryPackageInstalled(String packageName) throws IOException {
        packageName = mapPackageName(packageName);
        ProgramWithOutputStrings program = brewBuilder(packageName)
                .arg("list")
                .arg(packageName)
                .outputToStrings();
        ProgramWithOutputStringsResult result = program.execute();
        return result.getExitCode() == 0;
    }

    protected String mapPackageName(String packageName) {
        String mapped = packageNameMap.get(packageName);
        if (mapped != null) {
            return mapped;
        } else {
            return packageName;
        }
    }

    private static final ImmutableSet<String> caskFormulas = ImmutableSet.of("xquartz");

    private Program.Builder brewBuilder(String packageName) {
        Program.Builder pb = Program.running("brew");
        if (caskFormulas.contains(packageName.toLowerCase())) {
            pb.arg("cask");
        }
        return pb;
    }

    @Override
    public String queryPackageVersion(String packageName) throws IOException {
        packageName = mapPackageName(packageName);
        ProgramWithOutputStrings program = brewBuilder(packageName)
                .arg("info")
                .arg(packageName)
                .outputToStrings();
        ProgramWithOutputStringsResult result = program.execute();
        if (result.getExitCode() != 0) {
            throw new IndeterminateVersionException("brew list returned " + result.getExitCode());
        }
        Pattern patt = Pattern.compile("^" + Pattern.quote(packageName) + ":\\s+\\S+\\s+(\\S+)\\s+.*$");
        String stdout = result.getStdoutString();
        String version = CharSource.wrap(stdout).readLines(new LineProcessor<String>() {

            private String versionGroup;

            @Override
            public boolean processLine(String s) throws IOException {
                Matcher m = patt.matcher(s);
                if (m.find()) {
                    versionGroup = m.group(1);
                    return false;
                }
                return true;
            }

            @Override
            public String getResult() {
                return versionGroup;
            }
        });
        // wget: stable 1.19.1 (bottled), HEAD
        if (version != null) {
            return version;
        }
        throw new IndeterminateVersionException("failed to parse " + StringUtils.abbreviate(stdout, 64));
    }

}
