/*
 * (c) 2016 mike chaberski
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbManager;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.*;

public class XvfbRule extends ExternalResource {

    private final TemporaryFolder temporaryFolder;
    private final int displayNumber;
    private final XvfbManager xvfbManager;
    private transient XvfbController xvfbController;
    private final boolean disabled;

    public XvfbRule(int displayNum) {
        this(displayNum, false);
    }

    public XvfbRule(int displayNum, boolean disabled) {
        this(createDefaultXvfbManager(), displayNum, disabled);
    }

    public XvfbRule(XvfbManager xvfbManager, int displayNumber) {
        this(xvfbManager, displayNumber, false);
    }

    public XvfbRule(XvfbManager xvfbManager, int displayNumber, boolean disabled) {
        temporaryFolder = new TemporaryFolder();
        this.displayNumber = checkDisplayNumber(displayNumber);
        this.xvfbManager = checkNotNull(xvfbManager);
        this.disabled = disabled;
    }

    static XvfbManager createDefaultXvfbManager() {
        try {
            return new XvfbManager();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static int checkDisplayNumber(int displayNum) {
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
            File framebufferDir = temporaryFolder.getRoot();
            xvfbController = xvfbManager.start(displayNumber, framebufferDir);
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

    protected static String toDisplayValue(int displayNumber) {
        return String.format(":%d", checkDisplayNumber(displayNumber));
    }

    public String getDisplay() {
        return toDisplayValue(displayNumber);
    }
}
