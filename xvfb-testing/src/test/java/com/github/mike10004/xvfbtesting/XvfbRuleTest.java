/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramResult;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.github.mike10004.xvfbmanager.TreeNode;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbController.XWindow;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assume;
import org.junit.BeforeClass;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.*;

public class XvfbRuleTest {

    private static final int FIRST_DISPLAY_NUMBER = 99; // if host has more displays than this number already active, problems will ensue
    private static final AtomicInteger displayNumbers = new AtomicInteger(FIRST_DISPLAY_NUMBER);

    @BeforeClass
    public static void checkPrerequisities() {
        for (String packageName : new String[]{"xvfb", "x11-utils", "xdotool"}) {
            boolean installed = PackageManager.queryPackageInstalled(packageName);
            Assume.assumeTrue(packageName + " must be installed for these tests to be executed", installed);
        }
    }

    private static abstract class RuleUser {

        private final XvfbRule xvfb;

        public RuleUser(@Nullable Integer displayNumber) {
            if (displayNumber == null) {
                xvfb = new XvfbRule();
            } else {
                xvfb = XvfbRule.builder().onDisplay(displayNumber.intValue()).build();
            }
        }

        protected abstract void use(XvfbController ctrl) throws Exception;

        protected void before() throws Exception {
            // no op; override if needed
        }

        protected void after() throws Exception {
            // no op; override if needed
        }

        public void test() throws Exception {
            // some comically deep nesting
            try {
                xvfb.before();
                before();
                try {
                    use(xvfb.getXvfbController());
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

    private static final boolean takeScreenshot = false;

    private static class XMessageTester extends RuleUser {
        public XMessageTester() {
            super(null);
        }

        public XMessageTester(int displayNumber) {
            super(displayNumber);
        }
        @Override
        protected void use(XvfbController ctrl) throws Exception {
            ctrl.waitUntilReady();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                ListenableFuture<ProgramWithOutputStringsResult> xmessageFuture = Program.running("/usr/bin/xmessage")
                        .env("DISPLAY", ctrl.getDisplay())
                        .args("-nearmouse", "-print", "hello, world")
                        .outputToStrings()
                        .executeAsync(executor);
                new PrintingCallback("xmessage").addTo(xmessageFuture);
                TreeNode<XWindow> xmessageWindow = ctrl.pollForWindow(window -> window != null && "xmessage".equals(window.title), 250, 8).orNull();
                System.out.format("xmessage window: %s%n", xmessageWindow);
                assertNotNull("xmessage window", xmessageWindow);
                System.out.format("%s%n", xmessageWindow.getLabel().line);
                Rectangle position = parsePosition(xmessageWindow.getLabel());
                assertFalse(position.isEmpty());
                assertTrue(position.x >= 0);
                assertTrue(position.y >= 0);
                int buttonX = position.x + 25;
                int buttonY = position.y + 40;
                System.out.println("executing xdotool to click 'okay' button");
                ProgramWithOutputStringsResult xdotoolResult = Program.running("xdotool")
                        .env("DISPLAY", ctrl.getDisplay())
                        .args("mousemove", String.valueOf(buttonX), String.valueOf(buttonY), "click", "1")
                        .outputToStrings()
                        .execute();
                System.out.format("xdotool: %s%n", xdotoolResult);
                if (takeScreenshot) {
                    System.out.println("capturing screenshot");
                    File pnmFile = new File("target", "post-xdotool-" + System.currentTimeMillis() + ".ppm");
                    ctrl.captureScreenshot().convertToPnmFile(pnmFile);
                }
                long getStart = System.currentTimeMillis();
                ProgramWithOutputStringsResult xmessageResult = xmessageFuture.get(500, TimeUnit.MILLISECONDS);
                long getEnd = System.currentTimeMillis();
                System.out.format("waited %d milliseconds for xmessage to quit%n", getEnd - getStart);
                assertEquals("xmessage exit code", 0, xmessageResult.getExitCode());
                assertEquals("xmessage stdout", "okay", xmessageResult.getStdoutString().trim());
            } finally {
                List<Runnable> tasks = executor.shutdownNow();
                checkState(tasks.isEmpty(), "did not expect any tasks to be awaiting execution");
            }
        }

    }

    @org.junit.Test
    public void definedDisplayNumber() throws Exception {
        new XMessageTester(displayNumbers.incrementAndGet()).test();
    }

    @org.junit.Test
    public void autoDisplayNumber() throws Exception {
        new XMessageTester().test();
    }

    private static Rectangle parsePosition(XWindow window) {
        // 100x52+589+485
        Matcher m = Pattern.compile("\\s+(\\d+)x(\\d+)([-+]\\d+)([-+]\\d+)").matcher(window.line);
        checkArgument(m.find(), "geometry pattern not found in window line: %s", window.line);
        String w = m.group(1);
        String h = m.group(2);
        String x = StringUtils.removeStart(m.group(3), "+");
        String y = StringUtils.removeStart(m.group(4), "+");
        return new Rectangle(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(w), Integer.parseInt(h));
    }

    private static class PrintingCallback implements FutureCallback<ProgramResult> {

        private final String name;

        private PrintingCallback(String name) {
            this.name = name;
        }

        public void addTo(ListenableFuture<? extends ProgramResult> future) {
            Futures.addCallback(future, this);
        }

        @Override
        public void onSuccess(@Nullable ProgramResult result) {
            System.out.format("%s: %s%n", name, result);
        }

        @Override
        public void onFailure(Throwable t) {
            if (t instanceof CancellationException) {
                System.out.format("%s: cancelled%n", name);
            } else {
                System.out.format("%s: %s%n", name, t);
            }
        }
    }

}
