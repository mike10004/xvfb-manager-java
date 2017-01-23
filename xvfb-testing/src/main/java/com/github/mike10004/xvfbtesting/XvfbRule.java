/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Platforms;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbException;
import com.github.mike10004.xvfbmanager.XvfbManager;
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
    private transient volatile XvfbController xvfbController;
    private final boolean disabled;
    private final StartMode startMode;

    /**
     * Creates a default rule instance that auto-selects the display number.
     * Use a {@link #builder() builder} if you want to customize the
     * {@link XvfbManager manager} instance, specify the display number, or
     * customize other aspects of operation.
     */
    public XvfbRule() {
        this(builder());
    }

    private XvfbRule(Builder builder) {
        this.xvfbManager = checkNotNull(builder.xvfbManager);
        temporaryFolder = builder.temporaryFolder;
        initialDisplayNumber = builder.displayNumber;
        if (initialDisplayNumber != null) {
            checkDisplayNumber(initialDisplayNumber);
        }
        this.disabled = builder.disabled;
        checkArgument(disabled || !Platforms.getPlatform().isWindows(), "rule must be disabled on Windows platforms");
        this.startMode = checkNotNull(builder.startMode, "startMode");
    }

    enum StartMode {
        EAGER, LAZY
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
        private XvfbManager xvfbManager = new XvfbManager();
        private boolean disabled;
        private @Nullable Integer displayNumber;
        private TemporaryFolder temporaryFolder = new TemporaryFolder();
        private StartMode startMode = StartMode.EAGER;

        /**
         * Builds a rule instance.
         * @return a new rule instance
         */
        public XvfbRule build() {
            return new XvfbRule(this);
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
            return disabled(true);
        }

        /**
         * Sets the disabled flag.
         * @return this instance
         */
        @SuppressWarnings("BooleanParameter")
        public Builder disabled(boolean disabled) {
            this.disabled = disabled;
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

        /**
         * Delays starting the framebuffer daemon until the controller is requested.
         * This sets a flag that causes the rule to delay {@link XvfbManager#start()}
         * invocation until {@link #getController()} is invoked. Otherwise, {@code start()}
         * will be invoked, and the controller created, in the "before" phase of the
         * test lifecycle.
         * @return this instance
         */
        public Builder lazy() {
            startMode = StartMode.LAZY;
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
            if (startMode == StartMode.EAGER) {
                reallyPrepare();
            }
        }
    }

    private void reallyPrepare() throws IOException {
        temporaryFolder.create();
        if (initialDisplayNumber != null) {
            xvfbController = xvfbManager.start(initialDisplayNumber);
        } else {
            xvfbController = xvfbManager.start();
        }
    }

    @Override
    protected void after() {
        cleanUp();
    }

    protected void cleanUp() {
        XvfbController xvfbController_ = xvfbController;
        xvfbController = null;
        if (xvfbController_ != null) {
            xvfbController_.stop();
        }
        temporaryFolder.delete();
    }

    /**
     * Gets the controller instance.
     * @return the controller; never null
     * @throws IllegalStateException if controller has not been created yet
     * @throws XvfbException if start mode is lazy but {@link XvfbManager#start()} threw an exception
     */
    public XvfbController getController() throws XvfbException {
        if (disabled) {
            return DisabledXvfbController.getInstance();
        } else {
            XvfbController xvfbController_ = xvfbController;
            if (xvfbController_ == null) {
                checkState(startMode == StartMode.LAZY, "must invoke before()/prepare() before getController() unless start mode is lazy");
                try {
                    reallyPrepare();
                    xvfbController_ = xvfbController;
                    assert xvfbController_ != null : "controller should be initialized here";
                } catch (IOException e) {
                    throw new LazyPreparationException(e);
                }
            }
            return xvfbController_;
        }
    }

    private static class LazyPreparationException extends XvfbException {
        public LazyPreparationException(Throwable cause) {
            super("lazy preparation failed", cause);
        }
    }

}
