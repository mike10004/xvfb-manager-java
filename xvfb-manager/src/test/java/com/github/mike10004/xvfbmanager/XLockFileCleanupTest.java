package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Platforms;
import com.github.mike10004.nativehelper.Whicher;
import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class XLockFileCleanupTest {

    private String constructLockFilename(int displayNum) {
        return ".X" + displayNum + "-lock";
    }

    private static final Iterable<File> dirs = ImmutableSet.of(new File("/tmp"), FileUtils.getTempDirectory());

    @Nullable
    private File findLockFileByName(String lockFilename) {
        System.out.format("findLockFileByName: '%s' (%s)%n", lockFilename, dirs);
        return Whicher.builder().inAny(dirs).in(FileUtils.getTempDirectory()).build()
                .which(lockFilename).orElse(null);
    }

    @Test
    public void checkLockFileDeleted_specifiedDisplay() throws Exception {
        final int displayNum = 57;
        checkLockFileDeleted(m -> {
            try {
                return m.start(displayNum);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, c -> constructLockFilename(displayNum));
    }

    private void checkLockFileDeleted(Function<XvfbManager, XvfbController> starter, Function<XvfbController, String> lockFileNamer) throws Exception {
        Assume.assumeTrue(Platforms.getPlatform().isLinux());
        XvfbManager manager = new XvfbManager();
        String lockFilename = lockFileNamer.apply(null);
        if (lockFilename != null) {
            File f = findLockFileByName(lockFilename);
            assertNull("lock file already exists: " + f, f);
        }
        @Nullable
        final File lockFile;
        try (XvfbController ctrl = starter.apply(manager);
        ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
            ctrl.waitUntilReady(Tests.getReadinessPollIntervalMs(), Tests.getMaxReadinessPolls());
            System.out.format("DISPLAY=%s%n", ctrl.getDisplay());
            Subprocess subprocess = Subprocess.running("xmessage")
                    .args("-timeout", "10", "hello, world")
                    .build();
            ProcessMonitor<?, ?> pm = subprocess.launcher(processTracker).launch();
            lockFile = findLockFileByName(lockFileNamer.apply(ctrl));
            System.out.format("display ready on %s; lock file = %s%n", ctrl.getDisplay(), lockFile);
            assertNotNull("lock file exists", lockFile);
            ProcessKilling.termOrKill(pm.destructor(), 1, TimeUnit.SECONDS);
        }
        assertFalse("lock file exists (after controller stop): " + lockFile, lockFile.exists());
    }
}
