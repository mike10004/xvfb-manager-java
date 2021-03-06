package com.github.mike10004.xvfbmanager;

import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ProcessResult;
import io.github.mike10004.subprocess.ScopedProcessTracker;
import io.github.mike10004.subprocess.Subprocess;
import com.google.common.io.ByteSource;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

class Tests {

    private Tests() {}

    public static final String SYSPROP_TEST_MAX_READINESS_POLLS = "XvfbManager.tests.maxReadinessPolls";
    public static final String SYSPROP_TEST_READINESS_POLL_INTERVAL = "XvfbManager.tests.readinessPollIntervalMs";
    public static final String SYSPROP_TEST_XDIAGNOSTIC_ENABLED = "XvfbManager.tests.diagnostic.enabled";

    public static int getMaxReadinessPolls() {
        int value = Integer.parseInt(System.getProperty(SYSPROP_TEST_MAX_READINESS_POLLS, String.valueOf(DefaultXvfbController.DEFAULT_MAX_NUM_POLLS)));
        return value;
    }

    public static long getReadinessPollIntervalMs() {
        long value = Long.parseLong(System.getProperty(SYSPROP_TEST_READINESS_POLL_INTERVAL, String.valueOf(DefaultXvfbController.DEFAULT_POLL_INTERVAL_MS)));
        return value;
    }

    public static boolean isDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(SYSPROP_TEST_XDIAGNOSTIC_ENABLED, "false"));
    }

}
