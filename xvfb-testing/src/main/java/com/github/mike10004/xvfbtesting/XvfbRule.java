/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.novetta.ibg.common.sys.Platforms;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class XvfbRule extends ExternalResource {

    private final TemporaryFolder temporaryFolder;
    private final @Nullable Integer initialDisplayNumber;
    private transient XvfbController xvfbController;
    private final boolean disabled;

    public XvfbRule() {
        this(false);
    }

    public XvfbRule(boolean disabled) {
        this(null, disabled, null);
    }

    public XvfbRule(int displayNumber) {
        this(displayNumber, false);
    }

    public XvfbRule(int displayNumber, boolean disabled) {
        this(displayNumber, disabled, (Void) null);
    }

    private XvfbRule(@Nullable Integer displayNumber, boolean disabled, @Nullable Void unused) {
        temporaryFolder = new TemporaryFolder();
        initialDisplayNumber = displayNumber;
        if (initialDisplayNumber != null) {
            checkDisplayNumber(initialDisplayNumber);
        }
        this.disabled = disabled;
        checkArgument(disabled || !Platforms.getPlatform().isWindows(), "rule must be disabled on Windows platforms");
    }

    protected XvfbManager createXvfbManager(TemporaryFolder temporaryFolder) {
        return createDefaultXvfbManager(temporaryFolder);
    }

    static XvfbManager createDefaultXvfbManager(TemporaryFolder temporaryFolder) {
        try {
            return new XvfbManager(temporaryFolder.getRoot().toPath());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static int checkDisplayNumber(Integer displayNum) {
        checkNotNull(displayNum, "displayNum must be non-null");
        checkArgument(displayNum >= 0, "displayNum >= 0 is required");
        return displayNum;
    }

    @Override
    protected void before() throws Throwable {
        prepare();
    }

    public void prepare() throws IOException {
        checkState(xvfbController == null, "xvfbController already created");
        if (!disabled) {
            temporaryFolder.create();
            XvfbManager xvfbManager = createXvfbManager(temporaryFolder);
            if (initialDisplayNumber != null) {
                xvfbController = xvfbManager.start(initialDisplayNumber);
            } else {
                xvfbController = xvfbManager.start();
            }
        }
    }

    @Override
    protected void after() {
        cleanUp();
    }

    public void cleanUp() {
        XvfbController xvfbController_ = xvfbController;
        if (xvfbController_ != null) {
            xvfbController_.stop();
        }
        temporaryFolder.delete();
    }

    /**
     * Gets the controller instance.
     * @return the controller; never null
     * @throws IllegalStateException if controller has not been created yet
     */
    public XvfbController getXvfbController() {
        XvfbController xvfbController_ = xvfbController;
        checkState(xvfbController_ != null, "xvfbController not created yet; this rule is disabled or prepare()/before() method has not yet been invoked");
        return xvfbController_;
    }

}
