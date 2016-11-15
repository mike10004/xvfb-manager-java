/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStrings;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import com.novetta.ibg.common.sys.Whicher;
import org.apache.commons.lang3.StringUtils;

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
    private static final Splitter STATUS_VERSION_SPLIITER = Splitter.on(STATUS_VERSION_DELIM).trimResults().omitEmptyStrings();

    protected ProgramWithOutputStrings buildDpkgShowProgram(String packageName) {
        return Program.running("dpkg-query")
                .arg("--show")
                .args("--showformat", "${db:Status-Abbrev}" + STATUS_VERSION_DELIM + "${Version}")
                .arg(packageName)
                .outputToStrings();
    }

    @Override
    public boolean queryPackageInstalled(String packageName) {
        try {
            ProgramWithOutputStringsResult result = buildDpkgShowProgram(packageName)
                    .execute();
            boolean clean = result.getExitCode() == 0;
            log.log(clean ? Level.FINER : Level.INFO, "{0} {1}", new Object[]{result.getExitCode(), clean ? result.getStdoutString() : result.getStderrString()});
            if (clean) {
                String status = STATUS_VERSION_SPLIITER.split(result.getStdoutString()).iterator().next();
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
        ProgramWithOutputStringsResult result = buildDpkgShowProgram(packageName)
                .execute();
        if (result.getExitCode() == 0) {
            String stdout = result.getStdoutString();
            List<String> stdoutParts = STATUS_VERSION_SPLIITER.splitToList(stdout);
            String status = stdoutParts.get(0), version = stdoutParts.get(1);
            if ("ii".equals(status)) {
                return version;
            }
        }
        throw new IOException("package not installed or error occurred while querying version");
    }
}
