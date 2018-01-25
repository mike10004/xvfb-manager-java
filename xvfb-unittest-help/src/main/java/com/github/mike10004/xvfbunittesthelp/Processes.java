package com.github.mike10004.xvfbunittesthelp;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.io.ByteSource;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

public class Processes {

    private Processes() {}

    public static ProcessResult<String, String> runOrDie(Subprocess subprocess) {
        return runOrDie(subprocess, Charset.defaultCharset(), null);
    }

    public static ProcessResult<String, String> runOrDie(Subprocess subprocess, Charset programOutputCharset, @Nullable ByteSource stdin) {
        try (ScopedProcessTracker tracker = new ScopedProcessTracker()) {
            ProcessMonitor<String, String> monitor = subprocess.launcher(tracker)
                    .outputStrings(programOutputCharset, stdin)
                    .launch();
            return monitor.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
