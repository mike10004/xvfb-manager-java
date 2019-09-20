package com.github.mike10004.xvfbunittesthelp;

import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ProcessResult;
import io.github.mike10004.subprocess.ScopedProcessTracker;
import io.github.mike10004.subprocess.StreamInput;
import io.github.mike10004.subprocess.Subprocess;
import com.google.common.io.ByteSource;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

public class Processes {

    private Processes() {}

    public static ProcessResult<String, String> runOrDie(Subprocess subprocess) {
        return runOrDie(subprocess, Charset.defaultCharset(), null);
    }

    public static ProcessResult<String, String> runOrDie(Subprocess subprocess, Charset programOutputCharset, @Nullable ByteSource stdin) {
        StreamInput stdinSource = null;
        if (stdin != null) {
            stdinSource = stdin::openStream;
        }
        try (ScopedProcessTracker tracker = new ScopedProcessTracker()) {
            ProcessMonitor<String, String> monitor = subprocess.launcher(tracker)
                    .outputStrings(programOutputCharset, stdinSource)
                    .launch();
            return monitor.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
