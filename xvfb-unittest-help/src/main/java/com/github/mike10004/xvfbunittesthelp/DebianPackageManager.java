package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@VisibleForTesting
class DebianPackageManager extends PackageManager {

    private static final Logger log = Logger.getLogger(DebianPackageManager.class.getName());

    DebianPackageManager() {
        super();
    }

    private static final String STATUS_VERSION_DELIM = " ";
    private static final Splitter STATUS_VERSION_SPLITTER = Splitter.on(STATUS_VERSION_DELIM).trimResults().omitEmptyStrings();

    protected Subprocess buildDpkgShowProgram(String packageName) {
        return Subprocess.running("dpkg-query")
                .arg("--show")
                .args("--showformat", "${db:Status-Abbrev}" + STATUS_VERSION_DELIM + "${Version}")
                .arg(packageName)
                .build();
    }

    @Override
    public boolean queryPackageInstalled(String packageName) {
        try {
            ProcessResult<String, String> result = Processes.runOrDie(buildDpkgShowProgram(packageName));
            boolean clean = result.exitCode() == 0;
            log.log(clean ? Level.FINER : Level.INFO, "{0} {1}", new Object[]{result.exitCode(), clean ? result.content().stdout() : result.content().stderr()});
            if (clean) {
                String status = STATUS_VERSION_SPLITTER.split(result.content().stdout()).iterator().next();
                return "ii".equals(status);
            } else {
                return false;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "failed to query package status of " + packageName, e);
            return false;
        }
    }

    @Override
    public String queryPackageVersion(String packageName) throws IOException {
        ProcessResult<String, String> result = Processes.runOrDie(buildDpkgShowProgram(packageName));
        if (result.exitCode() == 0) {
            String stdout = result.content().stdout();
            List<String> stdoutParts = STATUS_VERSION_SPLITTER.splitToList(stdout);
            String status = stdoutParts.get(0), version = stdoutParts.get(1);
            if ("ii".equals(status)) {
                return version;
            }
        }
        throw new IOException("package not installed or error occurred while querying version");
    }
}
