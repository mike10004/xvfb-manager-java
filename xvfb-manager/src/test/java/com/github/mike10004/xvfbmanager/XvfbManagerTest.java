/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.galenframework.rainbow4j.Rainbow4J;
import com.galenframework.rainbow4j.Spectrum;
import com.galenframework.rainbow4j.colorscheme.ColorDistribution;
import com.github.mike10004.common.image.ImageInfo;
import com.github.mike10004.common.image.ImageInfos;
import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.github.mike10004.xvfbmanager.XvfbController.XWindow;
import com.github.mike10004.xvfbmanager.XvfbManager.LoggingCallback;
import com.github.mike10004.xvfbunittesthelp.Assumptions;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.github.mike10004.xvfbunittesthelp.XDiagnosticRule;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mike10004.nativehelper.Program.running;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XvfbManagerTest {

    private static final int PRESUMABLY_VACANT_DISPLAY_NUM = 88;

    @Rule
    public XDiagnosticRule diagnostic = Tests.isDiagnosticEnabled() ? new XDiagnosticRule() : XDiagnosticRule.getDisabledInstance();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void toDisplayValue() {
        System.out.println("\ntoDisplayValue\n");
        assertEquals("display", ":123", XvfbManager.toDisplayValue(123));
    }

    @Test(expected=IllegalArgumentException.class)
    public void toDisplayValue_negative() {
        System.out.println("\ntoDisplayValue_negative\n");
        XvfbManager.toDisplayValue(-1);
    }

    @BeforeClass
    public static void checkPrerequisities() throws IOException {
        PackageManager packageManager = PackageManager.getInstance();
        Iterable<String> requiredExecutables = Iterables.concat(Collections.singletonList("Xvfb"),
                DefaultXvfbController.getRequiredPrograms());
        for (String program : requiredExecutables) {
            boolean installed = packageManager.queryCommandExecutable(program);
            Assumptions.assumeTrue(program + " must be installed for these tests to be executed", installed);
        }
    }

    @BeforeClass
    public static void checkPnmSupport() throws Exception {
        Set<String> readerlessFormats = new HashSet<>();
        for (String formatName : new String[]{"PNM", "PPM", "PGM"}) {
            List<ImageReader> readers = ImmutableList.copyOf(ImageIO.getImageReadersByFormatName(formatName));
            if (readers.isEmpty()) {
                readerlessFormats.add(formatName);
            }
        }
        checkState(readerlessFormats.isEmpty(), "empty readers list for %s", readerlessFormats);
    }

    @AfterClass
    public static void checkLockFileAfterTests() throws Exception {
        File lockFile = XLockFileUtility.getInstance().constructLockFilePathname(":" + PRESUMABLY_VACANT_DISPLAY_NUM);
        if (lockFile.exists()) {
            System.out.format("WARNING: lock file exists after tests finished: %s%n", lockFile);
        }
    }

    @Test
    public void start_autoDisplay_trueColor() throws Exception {
        System.out.println("\nstart_autoDisplay_trueColor\n");
        Assumptions.assumeTrue("xvfb version not high enough to test auto-display support", PackageManager.getInstance().queryAutoDisplaySupport());
        testWithConfigAndDisplay(new XvfbConfig("640x480x24"), null);
    }

    @Test
    public void start_specifiedDisplay_trueColor() throws Exception {
        System.out.println("\nstart_specifiedDisplay_trueColor\n");
        File lockFile = XLockFileUtility.getInstance().constructLockFilePathname(":" + PRESUMABLY_VACANT_DISPLAY_NUM);
        checkState(!lockFile.exists(), "lock file already exists: %s", lockFile);
        testWithConfigAndDisplay(new XvfbConfig("640x480x24"), PRESUMABLY_VACANT_DISPLAY_NUM);
    }

    private void testWithConfigAndDisplay(XvfbConfig config, @Nullable Integer displayNumber) throws Exception {
        Assumptions.assumeTrue("imagemagick must be installed", PackageManager.getInstance().checkImageMagickInstalled());
        XvfbManager instance = new XvfbManager(config) {
            @Override
            protected DisplayReadinessChecker createDisplayReadinessChecker(String display, File framebufferDir) {
                return new DefaultDisplayReadinessChecker() {
                    @Override
                    protected void executedCheckProgram(ProgramWithOutputStringsResult result) {
                        System.out.format("readiness check: %s%n", result);
                    }
                };
            }
        };
        Path scratchDir = tmp.newFolder().toPath();
        try (XvfbController ctrl = (displayNumber == null ? instance.start(scratchDir) : instance.start(displayNumber, scratchDir))) {
            testUsingController(ctrl, config, true);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void testUsingController(XvfbController ctrl, XvfbConfig config, boolean takeScreenshots) throws InterruptedException, IOException, URISyntaxException {
        File imageFile = new File(XvfbManager.class.getResource("/example.jpg").toURI());
        ctrl.waitUntilReady(Tests.getReadinessPollIntervalMs(), Tests.getMaxReadinessPolls());
        if (takeScreenshots) {
            System.out.println("capturing screenshot before launching x program");
            Screenshot beforeScreenshot = ctrl.getScreenshooter().capture();
            checkScreenshot(beforeScreenshot, true, config);
        }
        String display = ctrl.getDisplay();
        System.out.format("xvfb is on display %s%n", display);
        ListenableFuture<ProgramWithOutputStringsResult> graphicalProgramFuture = launchProgramOnDisplay(display, imageFile);
        String filename = imageFile.getName();
        final String expectedWindowName = "ImageMagick: " + filename;
        Optional<TreeNode<XWindow>> window = ctrl.pollForWindow(input -> input != null && expectedWindowName.equals(input.title), 250, 4);
        checkState(window.isPresent(), "never saw image magick window");
        if (takeScreenshots) {
            System.out.println("capturing screenshot after launching x program");
            Screenshot afterScreenshot = ctrl.getScreenshooter().capture();
            checkScreenshot(afterScreenshot, false, config);
        }
        graphicalProgramFuture.cancel(true);
    }

    private ListenableFuture<ProgramWithOutputStringsResult> launchProgramOnDisplay(String display, File imageFile) throws URISyntaxException {
        Program<ProgramWithOutputStringsResult> imageMagickDisplay = running("/usr/bin/display")
                .env("DISPLAY", display)
                .arg(imageFile.getAbsolutePath())
                .outputToStrings();
        System.out.format("executing %s%n", imageMagickDisplay);
        ListenableFuture<ProgramWithOutputStringsResult> displayFuture = imageMagickDisplay.executeAsync(Executors.newSingleThreadExecutor());
        Futures.addCallback(displayFuture, new LoggingCallback("imagemagick-display", Charset.defaultCharset()), MoreExecutors.directExecutor());
        return displayFuture;
    }

    private static class ScreenSpace {
        public final int width;
        public final int height;
        public final int depth;

        public ScreenSpace(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public static ScreenSpace parse(String arg) {
            Matcher m = Pattern.compile("^(\\d+)x(\\d+)x(\\d+)(\\+\\d+)?$").matcher(arg);
            checkArgument(m.find(), "argument does not match pattern: %s", arg);
            int width = Integer.parseInt(m.group(1));
            int height = Integer.parseInt(m.group(2));
            int depth = Integer.parseInt(m.group(3));
            return new ScreenSpace(width, height, depth);
        }

        @Override
        public String toString() {
            return String.format("ScreenSpace{%dx%dx%d}", width, height, depth);
        }
    }

    private static String describe(ImageInfo ii) {
        return MoreObjects.toStringHelper(ii)
                .add("mimeType", ii.getMimeType())
                .add("width", ii.getWidth())
                .add("height", ii.getHeight())
                .add("format", ii.getFormat())
                .add("bpp", ii.getBitsPerPixel())
                .toString();
    }

    private void checkScreenshot(Screenshot screenshot, boolean allBlackExpected, XvfbConfig config) throws IOException {
        checkState(screenshot instanceof XwdFileScreenshot, "not an ImageIO-readable screenshot: %s", screenshot);
        ImageioReadableScreenshot pngScreenshot;
        try {
            pngScreenshot = new XwdFileToPngConverter(tmp.newFolder().toPath()).convert(screenshot);
        } catch (IOException e) {
            Throwable cause = e;
            while (cause != null) {
                cause.printStackTrace(System.out);
                cause = cause.getCause();
            }
            throw e;
        }
        File pngFile = File.createTempFile("screenshot", ".png", tmp.getRoot());
        pngScreenshot.asByteSource().copyTo(Files.asByteSink(pngFile));
        ImageInfo imageInfo = ImageInfos.read(pngScreenshot.asByteSource());
        System.out.format("%s%n", describe(imageInfo));
        BufferedImage image = ImageIO.read(pngFile);
        checkState(image != null, "image unreadable: %s", pngFile);
        ScreenSpace expectedScreen = ScreenSpace.parse(config.geometry);
        System.out.format("expecting %s%n", expectedScreen);
        assertEquals("width", expectedScreen.width, image.getWidth());
        assertEquals("height", expectedScreen.height, image.getHeight());
        Spectrum spectrum = Rainbow4J.readSpectrum(image);
        List<ColorDistribution> colorDistributions = spectrum.getColorDistribution(1);
        System.out.format("%d color distribution elements%n", colorDistributions.size());
        ColorDistribution blackDist = colorDistributions.stream().filter(forColor(Color.black)).findFirst().orElse(null);
        checkState(blackDist != null, "not enough black in image to evaluate blackness; must have at least 1% black");
        System.out.format("color distribution: %s %s%n", blackDist.getColor(), blackDist.getPercentage());
        checkState(blackDist.getColor().equals(Color.black), "only element in distribution list is not black: %s", blackDist.getColor());
        if (allBlackExpected) {
            assertEquals("percentage of black", 100d, blackDist.getPercentage(), 1e-3);
        } else {
            assertTrue("percentage of black", blackDist.getPercentage() < 100);
        }

    }

    private static Predicate<ColorDistribution> forColor(final Color color) {
        checkNotNull(color);
        return input -> input != null && color.equals(input.getColor());
    }

}
