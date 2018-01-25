/*
 * (c) 2016 Mike Chaberski
 *
 * Created by mike
 */
package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class FedoraPackageManager extends PackageManager {

    private final ImmutableMap<String, String> debianToFedoraPackageMapper = ImmutableMap.<String, String>builder()
            .put("xvfb", "xorg-x11-server-Xvfb")
            .put("x11-utils", "xorg-x11-utils")
            .build();

    protected Optional<String> checkInstalledPackageVersion(String packageName) throws IOException {
        checkArgument(!Strings.isNullOrEmpty(packageName));
        ProcessResult<String, String> result = Processes.runOrDie(Subprocess.running("dnf")
                .args("--cacheonly", "list", "installed", packageName)
                .build());
        if (result.exitCode() == 0) {
            return Optional.of(parseVersion(packageName, result.content().stdout()));
        } else {
            return Optional.empty();
        }
    }

    protected String parseVersion(String packageName, String dnfOutput) throws IOException {
        String version = CharSource.wrap(dnfOutput).readLines(new LineProcessor<String>(){

            private String version_;

            @Override
            public boolean processLine(String line) throws IOException {
                if (line.startsWith(packageName)) {
                    Splitter sp = Splitter.on(CharMatcher.whitespace()).trimResults().omitEmptyStrings();
                    List<String> parts = sp.splitToList(line);
                    if (parts.size() < 3) { // name     version     @updates
                        throw new IOException("unexpected format for line " + line);
                    }
                    version_ = parts.get(1);
                    return false;
                } else {
                    return true;
                }
            }

            @Override
            public String getResult() {
                return version_;
            }

        });
        return checkNotNull(version, "no line matched package name at start");
    }

    @Override
    public boolean queryPackageInstalled(String packageName) throws IOException {
        String fedoraName = mapToFedoraPackageName(packageName);
        return checkInstalledPackageVersion(fedoraName).isPresent();
    }

    protected String mapToFedoraPackageName(String packageName) {
        String fedoraName = debianToFedoraPackageMapper.get(packageName);
        if (fedoraName == null) {
            return packageName;
        } else {
            return fedoraName;
        }
    }

    @Override
    public String queryPackageVersion(String packageName) throws IOException {
        String fedoraName = mapToFedoraPackageName(packageName);
        Optional<String> version = checkInstalledPackageVersion(fedoraName);
        if (version.isPresent()) {
            return version.get();
        } else {
            throw new IOException("not installed, probably: " + fedoraName + " mapped from " + packageName);
        }
    }
}
