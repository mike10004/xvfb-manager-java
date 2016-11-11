/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.novetta.ibg.common.sys.Platforms;
import com.novetta.ibg.common.sys.Whicher;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class PackageManager {

    private PackageManager() {}

    public static boolean queryPackageInstalled(String packageName) {
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
    public static boolean checkImageMagickInstalled() {
        String[] progs = {"mogrify", "convert", "identify"};
        Whicher whicher = Whicher.gnu();
        for (String prog : progs) {
            if (!whicher.which(prog).isPresent()) {
                return false;
            }
        }
        return true;
    }

    public static String queryPackageVersion(String packageName) throws IOException {
        ProgramWithOutputStringsResult result = Program.running("dpkg-query")
                .args("--status", packageName)
                .outputToStrings()
                .execute();
        String stdout = result.getStdoutString();
        String version = CharSource.wrap(stdout).readLines(new LineProcessor<String>(){

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
