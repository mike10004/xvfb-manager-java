package com.github.mike10004.xvfbmanager;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;

public class XLockFileResource implements java.io.Closeable {

    private final int displayNum;
    private final File lockFile;

    public XLockFileResource(int displayNum) throws IOException {
        this.displayNum = displayNum;
        lockFile = XLockFileUtility.getInstance().constructLockFilePathname(":" + displayNum);
        checkState(!lockFile.exists(), "lock file already exists: %s", lockFile);
    }

    /**
     * Throws an exception if the lock file has not already been deleted, and tries to delete it.
     * @throws LockFileStillExistsException
     */
    @Override
    public void close() throws LockFileStillExistsException {
        boolean existed = lockFile.exists();
        if (existed) {
            boolean deleted = lockFile.delete();
            if (!deleted) {
                LoggerFactory.getLogger(getClass()).warn("failed to delete lock file: {}", lockFile);
            }
            throw new LockFileStillExistsException();
        }
    }

    public class LockFileStillExistsException extends IOException {
        public LockFileStillExistsException() {
            super("lock file existed when close() was called: " + lockFile);
        }
    }

    public int getDisplayNum() {
        return displayNum;
    }
}
