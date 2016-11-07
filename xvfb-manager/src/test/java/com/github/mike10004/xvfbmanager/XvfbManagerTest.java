/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import com.galenframework.rainbow4j.Rainbow4J;
import com.galenframework.rainbow4j.Spectrum;
import com.galenframework.rainbow4j.colorscheme.ColorDistribution;
import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.google.common.base.Converter;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.novetta.ibg.common.sys.Platforms;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static com.github.mike10004.nativehelper.Program.running;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XvfbManagerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @org.junit.Test
    public void toDisplayValue() throws Exception {
        assertEquals("display", ":123", XvfbManager.toDisplayValue(123));
    }

    @org.junit.Test
    public void start() throws Exception {
        Assume.assumeFalse("supported platforms are Linux and MacOS", Platforms.getPlatform().isWindows());
        Assume.assumeTrue("imagemagick must be installed", Images.isImageMagickInstalled());
        XvfbManager instance = new XvfbManager() {
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
        int displayNum = 23;
        File imageFile = new File(XvfbManager.class.getResource("/example.jpg").toURI());
        try (XvfbController ctrl = instance.start(displayNum, tmp.newFolder())) {
            ctrl.waitUntilReady();
            XvfbManager.Screenshot beforeScreenshot = ctrl.captureScreenshot();
            assertScreenshotAllBlack(beforeScreenshot, true);
            ListenableFuture<ProgramWithOutputStringsResult> graphicalProgramFuture = launchProgramOnDisplay(displayNum, imageFile);
            String filename = imageFile.getName();
            String expectedWindowName = "ImageMagick: " + filename;
            boolean imageMagickWindowOpened = new XWindowPoller(":" + displayNum, expectedWindowName, Predicates.containsPattern("\\bImageMagick\\b")).poll(100, 20);
            checkState(imageMagickWindowOpened, "never saw image magick window");
            XvfbManager.Screenshot afterScreenshot = ctrl.captureScreenshot();
            assertScreenshotAllBlack(afterScreenshot, false);
            graphicalProgramFuture.cancel(true);
        }
    }

    private static class XWindowPoller extends Poller {

        private final String display;
        private final Predicate<? super String> stdoutPredicate;
        private final String expectedWindowName;

        public XWindowPoller(String display, String expectedWindowName, Predicate<? super String> stdoutPredicate) {
            super(Sleeper.DEFAULT);
            this.expectedWindowName = expectedWindowName;
            this.display = display;
            this.stdoutPredicate = stdoutPredicate;
        }

        @Override
        protected boolean check(int pollAttemptsSoFar) {
            ProgramWithOutputStringsResult result = Program.running("xwininfo")
                    .env("DISPLAY", display)
                    .args("-name", expectedWindowName)
                    .outputToStrings()
                    .execute();

            if (result.getExitCode() == 0) {
                String stdout = result.getStdoutString();
                boolean found = stdoutPredicate.apply(stdout);
                if (found) {
                    System.out.println(stdout);
                }
                return found;
            } else {
                System.out.format("xwininfo: %s%n", result);
            }
            return false;
        }
    }

    private ListenableFuture<ProgramWithOutputStringsResult> launchProgramOnDisplay(int displayNum, File imageFile) throws URISyntaxException {
        String display = XvfbManager.toDisplayValue(displayNum);
        Program<ProgramWithOutputStringsResult> imageMagickDisplay = running("/usr/bin/display")
                .env("DISPLAY", display)
                .arg(imageFile.getAbsolutePath())
                .outputToStrings();
        System.out.format("executing %s%n", imageMagickDisplay);
        ListenableFuture<ProgramWithOutputStringsResult> displayFuture = imageMagickDisplay.executeAsync(Executors.newSingleThreadExecutor());
        Futures.addCallback(displayFuture, new XvfbManager.LoggingCallback("imagemagick-display"));
        return displayFuture;
    }

    private void assertScreenshotAllBlack(XvfbManager.Screenshot screenshot, boolean expected) throws IOException {
        Converter<File, File> xwdToPnm = new Images.XwdToPnmConverter(tmp.newFile(), tmp.getRoot().toPath());
        File pnmFile = checkNotNull(xwdToPnm.convert(screenshot.getRawFile()));
        BufferedImage image = ImageIO.read(pnmFile);
        checkState(image != null, "image unreadable: %s", pnmFile);
        Spectrum spectrum = Rainbow4J.readSpectrum(image);
        List<ColorDistribution> colorDistributions = spectrum.getColorDistribution(1);
        System.out.format("%d color distribution elements%n", colorDistributions.size());
        ColorDistribution blackDist = Iterables.find(colorDistributions, forColor(Color.black), null);
        checkState(blackDist != null, "not enough black in image to evaluate blackness; must have at least 1% black");
        System.out.format("color distribution: %s %s%n", blackDist.getColor(), blackDist.getPercentage());
        checkState(blackDist.getColor().equals(Color.black), "only element in distribution list is not black: %s", blackDist.getColor());
        if (expected) {
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