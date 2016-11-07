/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.*;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.mike10004.nativehelper.Program.running;

public class XvfbManager {

    private static class StandardCallback implements FutureCallback<ProgramResult> {

        private final String name;

        private StandardCallback(String name) {
            this.name = name;
        }

        @Override
        public void onSuccess(ProgramResult result) {
            System.out.format("%s: %s%n", name, result);
        }

        @Override
        public void onFailure(Throwable t) {
            if (t instanceof java.util.concurrent.CancellationException) {
                System.out.format("%s: cancelled%n", name);
            } else {
                System.out.format("%s: %s%n", name, t);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        try {
            File imageFile = new File(XvfbManager.class.getResource("/example.jpg").toURI());
            Path buildDir = new File(System.getProperty("user.dir"), "target").toPath();
            Path framebufferDir = java.nio.file.Files.createTempDirectory(buildDir, "framebuffer");
            Path outputDir = java.nio.file.Files.createTempDirectory(buildDir, "output");
            String display = ":71"; // some likely-unsed display number
            ProgramWithOutputStrings xvfb = Program.running("Xvfb")
                    .args(display)
                    .args("-screen", "0", "800x600x24")
                    .args("-fbdir", framebufferDir.toAbsolutePath().toString())
                    .outputToStrings();
            System.out.format("executing %s%n", xvfb);
            ListenableFuture<ProgramWithOutputStringsResult> xvfbFuture = xvfb.executeAsync(executorService);
            Futures.addCallback(xvfbFuture, new StandardCallback("xvfb"));
            System.out.format("%s ready? %s%n", display, isDisplayReady(display));
            Thread.sleep(501);
            System.out.format("%s ready? %s%n", display, isDisplayReady(display));
            try {
                Program<ProgramWithOutputStringsResult> imageMagickDisplay = running("/usr/bin/display")
                        .env("DISPLAY", display)
                        .arg(imageFile.getAbsolutePath())
                        .outputToStrings();
                System.out.format("executing %s%n", imageMagickDisplay);
                ListenableFuture<ProgramWithOutputStringsResult> displayFuture = imageMagickDisplay.executeAsync(executorService);
                Futures.addCallback(displayFuture, new StandardCallback("imagemagick-display"));
                try {
                    sleep(502);
                    takeScreenshot(display, outputDir);
                    System.out.println("...done sleeping");
                    Collection<File> fbFiles = FileUtils.listFiles(outputDir.toFile(), null, true);
                    System.out.format("framebuffer files: %s%n", fbFiles);
                } finally {
                    if (!displayFuture.isDone()) {
                        System.out.println("killing imagemagick display");
                        displayFuture.cancel(true);
                    }
                }
            } finally {
                if (!xvfbFuture.isDone()) {
                    System.out.println("killing xvfb");
                    xvfbFuture.cancel(true);
                }
            }
        } finally {
            System.out.println("shutting down thread executor service");
            executorService.shutdownNow();
        }

    }

    private static void sleep(long millis) throws InterruptedException {
        System.out.format("sleeping for %d milliseconds...", millis);
        Thread.sleep(millis);
        System.out.format("slept for %d milliseconds", millis);
    }

    private static void takeScreenshot(String display, Path outputDir) throws IOException {
        File stdoutFile = outputDir.resolve("output.xwd").toFile(), stderrFile = outputDir.resolve("xwd-stderr").toFile();
        ProgramWithOutputFilesResult xwdResult = running("xwd")
                .env("DISPLAY", display)
                .args("-root", "-silent")
                .outputToFiles(stdoutFile, stderrFile)
                .execute();
        System.out.format("xwd: %s%n", xwdResult);
        if (xwdResult.getExitCode() != 0) {
            java.nio.file.Files.copy(stderrFile.toPath(), System.out);
            return;
        }
        File pnmFile = outputDir.resolve("output.pnm").toFile(), pnmStderrFile = outputDir.resolve("xwdtopnm-stderr").toFile();
        ProgramWithOutputFilesResult xwdtopnmResult = running("xwdtopnm")
                .arg(stdoutFile.getAbsolutePath())
                .outputToFiles(pnmFile, pnmStderrFile)
                .execute();
        System.out.format("xwdtopnm: %s%n", xwdtopnmResult);
    }

    // https://askubuntu.com/questions/60586/how-to-check-if-xvfb-is-already-running-on-display-0
    private static boolean isDisplayReady(String display) throws IOException {
        ProgramWithOutputStringsResult result = running("xdpyinfo")
                .args("-display", display)
                .outputToStrings()
                .execute();
        System.out.format("xdpyinfo: %s%n", result);
        return result.getExitCode() == 0;
    }
}
