package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.Platforms;
import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.github.mike10004.nativehelper.Whicher;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XLockFileCleanupTest {

    private String constructLockFilename(int displayNum) {
        return ".X" + displayNum + "-lock";
    }

    private static final Iterable<File> dirs = ImmutableSet.of(new File("/tmp"), FileUtils.getTempDirectory());

    private Optional<File> findLockFileByName(String lockFilename) {
        System.out.format("findLockFileByName: '%s' (%s)%n", lockFilename, dirs);
        return Whicher.builder().inAny(dirs).in(FileUtils.getTempDirectory()).build()
                .which(lockFilename);
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
            assertFalse("lock file already exists", findLockFileByName(lockFilename).isPresent());
        }
        final File lockFile;
        try (XvfbController ctrl = starter.apply(manager)) {
            ctrl.waitUntilReady(Tests.getReadinessPollIntervalMs(), Tests.getMaxReadinessPolls());
            ListenableFuture<ProgramWithOutputStringsResult> future = Program.running("xmessage").args("-timeout", "10", "hello, world").outputToStrings().executeAsync(Executors.newSingleThreadExecutor());
            Optional<File> lockFileOpt = findLockFileByName(lockFileNamer.apply(ctrl));
            System.out.format("display ready on %s; lock file = %s (exists? %s)%n", ctrl.getDisplay(), lockFileOpt, lockFileOpt.isPresent());
            assertTrue("no lock file at " + lockFileOpt, lockFileOpt.isPresent());
            lockFile = lockFileOpt.get();
            future.cancel(true);
        }
        assertFalse("lock file exists (after controller stop): " + lockFile, lockFile.exists());
    }
}
