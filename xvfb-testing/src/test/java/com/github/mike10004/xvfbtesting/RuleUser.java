package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbmanager.XvfbController;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class RuleUser {

    private final XvfbRule xvfb;

    public RuleUser(@Nullable Integer displayNumber) {
        this(buildDefaultRule(displayNumber));
    }

    private static XvfbRule buildDefaultRule(@Nullable Integer displayNumber) {
        if (displayNumber == null) {
            return new XvfbRule();
        } else {
            return XvfbRule.builder().onDisplay(displayNumber.intValue()).build();
        }
    }

    public RuleUser(XvfbRule xvfb) {
        this.xvfb = checkNotNull(xvfb);
    }

    protected abstract void use(XvfbController ctrl) throws Exception;

    protected void before() throws Exception {
        // no op; override if needed
    }

    protected void after() throws Exception {
        // no op; override if needed
    }

    protected void getControllerAndUse(XvfbRule rule) throws Exception {
        XvfbController controller = xvfb.getController();
        use(controller);
    }

    public void test() throws Exception {
        // some comically deep nesting
        try {
            xvfb.before();
            before();
            try {
                getControllerAndUse(xvfb);
            } finally {
                try {
                    after();
                } finally {
                    xvfb.after();
                }
            }
        } catch (Exception e) {
            throw e;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

    }
}
