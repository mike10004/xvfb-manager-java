/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbException;
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
    private final XvfbManager xvfbManager;
    private transient XvfbController xvfbController;
    private final boolean disabled;

    public XvfbRule() {
        this(createDefaultXvfbManager(), null, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private XvfbManager xvfbManager;
        private boolean disabled;
        private @Nullable Integer displayNumber;

        public XvfbRule build() {
            return new XvfbRule(xvfbManager == null ? createDefaultXvfbManager() : xvfbManager, displayNumber, disabled);
        }

        public Builder manager(XvfbManager xvfbManager) {
            this.xvfbManager = checkNotNull(xvfbManager);
            return this;
        }

        public Builder disabled() {
            disabled = true;
            return this;
        }

        public Builder disabledOnWindows() {
            if (Platforms.getPlatform().isWindows()) {
                disabled = true;
            }
            return this;
        }

        public Builder autoDisplay() {
            displayNumber = null;
            return this;
        }

        public Builder onDisplay(int displayNumber) {
            this.displayNumber = checkDisplayNumber(displayNumber);
            return this;
        }
    }

    private XvfbRule(XvfbManager xvfbManager, @Nullable Integer displayNumber, boolean disabled) {
        this.xvfbManager = checkNotNull(xvfbManager);
        temporaryFolder = new TemporaryFolder();
        initialDisplayNumber = displayNumber;
        if (initialDisplayNumber != null) {
            checkDisplayNumber(initialDisplayNumber);
        }
        this.disabled = disabled;
        checkArgument(disabled || !Platforms.getPlatform().isWindows(), "rule must be disabled on Windows platforms");
    }

    static XvfbManager createDefaultXvfbManager() {
        try {
            return new XvfbManager();
        } catch (IOException e) {
            throw new XvfbException("XvfbManager construction failed", e);
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
