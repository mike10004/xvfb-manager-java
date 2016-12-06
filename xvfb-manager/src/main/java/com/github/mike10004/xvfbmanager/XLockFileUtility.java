package com.github.mike10004.xvfbmanager;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

class XLockFileUtility {

    private static final Function<String, String> systemEnvironment = new Function<String, String>() {
        @Override
        public @Nullable
        String apply(String name) {
            return System.getenv(name);
        }
    };

    private final Function<String, String> environment;

    private static final XLockFileUtility instance = new XLockFileUtility();

    private XLockFileUtility() {
        this(systemEnvironment());
    }

    public static XLockFileUtility getInstance() {
        return instance;
    }

    @VisibleForTesting
    protected XLockFileUtility(Function<String, String> environment) {
        this.environment = checkNotNull(environment);
    }

    private static final Function<String, String> systemEnvironment() {
        return systemEnvironment;
    }

    public File constructLockFilePathname(String display) throws IOException {
        checkArgument(display.matches(":\\d+"), "invalid display: '%s' (expected format ':N')", display);
        int displayNum = Integer.parseInt(display.substring(1));
        String filename = constructLockFilename(displayNum);
        File parent = getXLockFileParentPath();
        return new File(parent, filename);
    }

    private static final String[] tmpdirEnvironmentVariableNames = {"TMP", "TMPDIR", "TEMP"};

    /**
     * Tries to return the host temporary directory in which the X lock file would be stored.
     * This may be different from the value of system property {@code java.io.tmpdir}.
     * @return the directory pathname
     * @throws FileNotFoundException on unexpected state
     */
    protected File getXLockFileParentPath() throws FileNotFoundException {
        String path = "/tmp";
        for (String envVarName : tmpdirEnvironmentVariableNames) {
            String envVarValue = environment.apply(envVarName);
            if (envVarValue != null) {
                return new File(envVarValue);
            }
        }
        File dir = new File(path);
        return dir.isDirectory() ? dir : FileUtils.getTempDirectory();
    }

    protected String constructLockFilename(int displayNum) {
        return ".X" + displayNum + "-lock";
    }

}
