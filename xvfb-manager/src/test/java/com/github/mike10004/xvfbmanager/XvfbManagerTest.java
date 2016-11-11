/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.galenframework.rainbow4j.Rainbow4J;
import com.galenframework.rainbow4j.Spectrum;
import com.galenframework.rainbow4j.colorscheme.ColorDistribution;
import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.github.mike10004.xvfbmanager.XvfbController.XWindow;
import com.github.mike10004.xvfbmanager.XvfbManager.Screenshot;
import com.github.mike10004.xvfbunittesthelp.PackageManager;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.novetta.ibg.common.image.ImageInfo;
import com.novetta.ibg.common.image.ImageInfos;
import com.novetta.ibg.common.sys.Platforms;
import com.novetta.ibg.common.sys.Whicher;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mike10004.nativehelper.Program.running;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XvfbManagerTest {

    private static final int PRESUMABLY_VACANT_DISPLAY_NUM = 99;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @org.junit.Test
    public void toDisplayValue() throws Exception {
        assertEquals("display", ":123", XvfbManager.toDisplayValue(123));
    }

    @org.junit.BeforeClass
    public static void checkPrerequisities() {
        for (String packageName : new String[]{"xvfb", "x11-utils", "xdotool"}) {
            boolean installed = PackageManager.queryPackageInstalled(packageName);
            Assume.assumeTrue(packageName + " must be installed for these tests to be executed", installed);
        }
    }
    @org.junit.Test
    public void start_autoDisplay_trueColor() throws Exception {
        Assume.assumeTrue("xvfb version not high enough to test auto-display support", checkAutoDisplaySupport());
        testWithConfigAndDisplay(new XvfbConfig("640x480x24"), null);
    }

    private static boolean checkAutoDisplaySupport() throws IOException {
        String version = PackageManager.queryPackageVersion("xvfb");
        Matcher m = Pattern.compile("\\d+:(\\d+)\\.(\\d+)\\b.*").matcher(version);
        if (m.find()) {
            String majorStr = m.group(1);
            String minorStr = m.group(2);
            int major = Integer.parseInt(majorStr), minor = Integer.parseInt(minorStr);
            System.out.format("xvfb version \"%s\" parsed to (major, minor) = (%d, %d)%n", version, major, minor);
            return major > 1 || (major >= 1 && minor >= 13);
        }
        throw new IllegalStateException("unexpected xvfb version string: " + version);
    }

    @org.junit.Test
    public void start_specifiedDisplay_trueColor() throws Exception {
        testWithConfigAndDisplay(new XvfbConfig("640x480x24"), PRESUMABLY_VACANT_DISPLAY_NUM);
    }

    private void testWithConfigAndDisplay(XvfbConfig config, @Nullable Integer displayNumber) throws Exception {
        Assume.assumeFalse("supported platforms are Linux and MacOS", Platforms.getPlatform().isWindows());
        Assume.assumeTrue("imagemagick must be installed", PackageManager.checkImageMagickInstalled());
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

    private void testUsingController(XvfbController ctrl, XvfbConfig config, boolean takeScreenshots) throws InterruptedException, IOException, URISyntaxException {
        File imageFile = new File(XvfbManager.class.getResource("/example.jpg").toURI());
        ctrl.waitUntilReady();
        if (takeScreenshots) {
            System.out.println("capturing screenshot before launching x program");
            XvfbManager.Screenshot beforeScreenshot = ctrl.captureScreenshot();
            checkScreenshot(beforeScreenshot, true, config);
        }
        String display = ctrl.getDisplay();
        System.out.format("xvfb is on display %s%n", display);
        ListenableFuture<ProgramWithOutputStringsResult> graphicalProgramFuture = launchProgramOnDisplay(display, imageFile);
        String filename = imageFile.getName();
        final String expectedWindowName = "ImageMagick: " + filename;
        Optional<TreeNode<XWindow>> window = ctrl.pollForWindow(new Predicate<XWindow>() {
            @Override
            public boolean apply(XWindow input) {
                return expectedWindowName.equals(input.title);
            }
        }, 250, 4);
        checkState(window.isPresent(), "never saw image magick window");
        if (takeScreenshots) {
            System.out.println("capturing screenshot after launching x program");
            XvfbManager.Screenshot afterScreenshot = ctrl.captureScreenshot();
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
        Futures.addCallback(displayFuture, new XvfbManager.LoggingCallback("imagemagick-display", Charset.defaultCharset()));
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
    private void checkScreenshot(XvfbManager.Screenshot screenshot, boolean allBlackExpected, XvfbConfig config) throws IOException {
        File pnmFile = File.createTempFile("screenshot", ".pnm", tmp.getRoot());
        screenshot.convertToPnmFile(pnmFile);
        ImageInfo imageInfo = ImageInfos.read(Files.asByteSource(pnmFile));
        System.out.format("%s%n", describe(imageInfo));
        BufferedImage image = ImageIO.read(pnmFile);
        checkState(image != null, "image unreadable: %s", pnmFile);
        ScreenSpace expectedScreen = ScreenSpace.parse(config.geometry);
        System.out.format("expecting %s%n", expectedScreen);
        assertEquals("width", expectedScreen.width, image.getWidth());
        assertEquals("height", expectedScreen.height, image.getHeight());
        Spectrum spectrum = Rainbow4J.readSpectrum(image);
        List<ColorDistribution> colorDistributions = spectrum.getColorDistribution(1);
        System.out.format("%d color distribution elements%n", colorDistributions.size());
        ColorDistribution blackDist = Iterables.find(colorDistributions, forColor(Color.black), null);
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
        return new Predicate<ColorDistribution>() {
            @Override
            public boolean apply(ColorDistribution input) {
                return color.equals(input.getColor());
            }
        };
    }

    @org.junit.BeforeClass
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

}
