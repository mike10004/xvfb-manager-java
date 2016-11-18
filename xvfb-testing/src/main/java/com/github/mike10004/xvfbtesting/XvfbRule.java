/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.DisabledXvfbController;
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

    /**
     * Creates a default rule instance that auto-selects the display number.
     * Use a {@link #builder() builder} if you want to customize the
     * {@link XvfbManager manager} instance, specify the display number, or
     * customize other aspects of operation.
     */
    public XvfbRule() {
        this(createDefaultXvfbManager(), null, false);
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
        return new XvfbManager();
    }

    /**
     * Returns a new builder of rule instances.
     * @return a new builder instance
     * @see Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for rule instances.
     * @see XvfbRule
     */
    public static class Builder {
        private XvfbManager xvfbManager;
        private boolean disabled;
        private @Nullable Integer displayNumber;

        /**
         * Builds a rule instance.
         * @return a new rule instance
         */
        public XvfbRule build() {
            return new XvfbRule(xvfbManager == null ? createDefaultXvfbManager() : xvfbManager, displayNumber, disabled);
        }

        /**
         * Sets the manager instance to be used in building a rule.
         * @param xvfbManager the manager to build the rule with
         * @return this instance
         */
        public Builder manager(XvfbManager xvfbManager) {
            this.xvfbManager = checkNotNull(xvfbManager);
            return this;
        }

        /**
         * Sets the disabled flag to true.
         * @return this instance
         */
        public Builder disabled() {
            disabled = true;
            return this;
        }

        /**
         * Sets the disabledness
         * @return this instance
         */
        public Builder disabledOnWindows() {
            if (Platforms.getPlatform().isWindows()) {
                disabled = true;
            }
            return this;
        }

        /**
         * Sets the display number to be automatically selected.
         * This uses the {@code -displayfd} option to {@code Xvfb}.
         * @return this instance
         */
        public Builder autoDisplay() {
            displayNumber = null;
            return this;
        }

        /**
         * Sets the display number of the rule instance being built.
         * Use {@link #autoDisplay()} to automatically select an unused
         * display number.
         * @param displayNumber the display number
         * @return this instance
         */
        public Builder onDisplay(int displayNumber) {
            this.displayNumber = checkDisplayNumber(displayNumber);
            return this;
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

    protected void prepare() throws IOException {
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

    protected void cleanUp() {
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
    public XvfbController getController() {
        if (disabled) {
            return DisabledXvfbController.getInstance();
        } else {
            XvfbController xvfbController_ = xvfbController;
            checkState(xvfbController_ != null, "xvfbController not created yet; this rule is disabled or prepare()/before() method has not yet been invoked");
            return xvfbController_;
        }
    }

}
