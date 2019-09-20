package com.github.mike10004.xvfbtesting;

import com.github.mike10004.xvfbunittesthelp.ProcessTrackerRule;
import com.google.common.util.concurrent.JdkFutureAdapters;
import io.github.mike10004.subprocess.ProcessMonitor;
import io.github.mike10004.subprocess.ProcessResult;
import io.github.mike10004.subprocess.ProcessTracker;
import io.github.mike10004.subprocess.Subprocess;
import com.github.mike10004.xvfbmanager.TreeNode;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbController.XWindow;
import com.github.mike10004.xvfbmanager.XwdFileScreenshot;
import com.github.mike10004.xvfbmanager.XwdFileToPngConverter;
import com.github.mike10004.xvfbunittesthelp.Assumptions;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XvfbRuleTest {

    private static final int FIRST_DISPLAY_NUMBER = 101; // if host has more displays than this number already active, problems will ensue
    private static final AtomicInteger displayNumbers = new AtomicInteger(FIRST_DISPLAY_NUMBER);
    private static final boolean takeScreenshot = false;

    @Rule
    public final ProcessTrackerRule processTrackerRule = new ProcessTrackerRule();

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void checkPrerequisities() throws IOException {
        //noinspection ConstantConditions
        for (String program : Iterables.concat(Arrays.asList("Xvfb", "xdotool", "xmessage"), takeScreenshot ? ImmutableList.of("xwdtopnm") : ImmutableList.of())) {
            boolean installed = PackageManager.getInstance().queryCommandExecutable(program);
            System.out.format("%s executable? %s%n", program, installed);
            Assumptions.assumeTrue(program + " must be an executable program for these tests to be executed", installed);
        }
    }

    private class XMessageTester extends RuleUser {

        private final File tempDir;

        public XMessageTester(File tempDir) {
            super((Integer)null);
            this.tempDir = tempDir;
        }

        public XMessageTester(int displayNumber, File tempDir) {
            super(displayNumber);
            this.tempDir = tempDir;
        }

        @Override
        protected void use(XvfbController ctrl) throws Exception {
            ProcessTracker GLOBAL_PROCESS_TRACKER = processTrackerRule.getTracker();
            ctrl.waitUntilReady(Tests.getReadinessPollIntervalMs(), Tests.getMaxReadinessPolls());
            ProcessMonitor<String, String> xmessageMonitor = Subprocess.running("/usr/bin/xmessage")
                    .env("DISPLAY", ctrl.getDisplay())
                    .args("-nearmouse", "-print", "hello, world")
                    .build()
                    .launcher(GLOBAL_PROCESS_TRACKER)
                    .outputStrings(Charset.defaultCharset())
                    .launch();
            Futures.addCallback(JdkFutureAdapters.listenInPoolThread(xmessageMonitor.future()), new PrintingCallback<>("xmessage"), MoreExecutors.directExecutor());
            TreeNode<XWindow> xmessageWindow = ctrl.pollForWindow(window -> window != null && "xmessage".equals(window.title), 250, 8).orElse(null);
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
            ProcessResult<String, String> xdotoolRslt = Subprocess.running("xdotool")
                    .env("DISPLAY", ctrl.getDisplay())
                    .args("mousemove", String.valueOf(buttonX), String.valueOf(buttonY), "click", "1")
                    .build()
                    .launcher(GLOBAL_PROCESS_TRACKER)
                    .outputStrings(Charset.defaultCharset())
                    .launch().await();
            System.out.format("xdotool: %s%n", xdotoolRslt);
            if (takeScreenshot) {
                System.out.println("capturing screenshot");
                File pngFile = new File("target", "post-xdotool-" + System.currentTimeMillis() + ".png");
                XwdFileScreenshot screenshot = (XwdFileScreenshot) ctrl.getScreenshooter().capture();
                new XwdFileToPngConverter(GLOBAL_PROCESS_TRACKER, tempDir.toPath()).convert(screenshot).asByteSource().copyTo(Files.asByteSink(pngFile));
            }
            long getStart = System.currentTimeMillis();
            ProcessResult<String, String> xmessageResult = xmessageMonitor.await(500, TimeUnit.MILLISECONDS);
            long getEnd = System.currentTimeMillis();
            System.out.format("waited %d milliseconds for xmessage to quit%n", getEnd - getStart);
            assertEquals("xmessage exit code", 0, xmessageResult.exitCode());
            assertEquals("xmessage stdout", "okay", xmessageResult.content().stdout().trim());
        }

    }

    @Test
    public void definedDisplayNumber() throws Exception {
        new XMessageTester(displayNumbers.incrementAndGet(), tmp.getRoot()).test();
    }

    @Test
    public void autoDisplayNumber() throws Exception {
        new XMessageTester(checkNotNull(tmp, "tmp is null here; very strange").getRoot()).test();
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

    private static class PrintingCallback<T> implements FutureCallback<T> {

        private final String name;

        private PrintingCallback(String name) {
            this.name = name;
        }

        @Override
        public void onSuccess(@Nullable T result) {
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
