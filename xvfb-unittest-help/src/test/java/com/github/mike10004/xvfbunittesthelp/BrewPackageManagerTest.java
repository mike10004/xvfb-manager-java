package com.github.mike10004.xvfbunittesthelp;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrewPackageManagerTest {

    @BeforeClass
    public static void checkPrereqs() {
        PackageManager instance = PackageManager.getInstance();
        Assume.assumeTrue(instance.toString() + " is not " + BrewPackageManager.class, instance instanceof BrewPackageManager);
    }

    @Test
    public void queryPackageInstalled_knownInstalled() throws Exception {
        PackageManager instance = PackageManager.getInstance();
        String pkgName = getKnownInstalledPackageName();
        boolean installed = instance.queryPackageInstalled(pkgName);
        assertTrue("query should indicate " + pkgName + " is installed", installed);
    }

    @Test
    public void queryPackageVersion_knownInstalled() throws Exception {
        PackageManager instance = PackageManager.getInstance();
        String pkgName = getKnownInstalledPackageName();
        String version = instance.queryPackageVersion(pkgName);
        assertTrue("unexpected version string", version != null && version.matches("^\\d+\\.\\d+(\\.\\S+)?$"));
    }

    @Test
    public void queryPackageInstalled_cask() throws Exception {
        boolean installed = PackageManager.getInstance().queryPackageInstalled("xquartz");
        assertTrue("known cask installedness check failed", installed);
    }

    @Test
    public void queryPackageInstalled_cask_mapped() throws Exception {
        boolean installed = PackageManager.getInstance().queryPackageInstalled("xvfb");
        assertTrue("known cask installedness check failed", installed);
    }

    @Test
    public void queryCommandExecutable_Xvfb() throws Exception {
        boolean available = PackageManager.getInstance().queryCommandExecutable("Xvfb");
        if (!available) {
            System.out.format("%s not found on %s%n", "Xvfb", System.getenv("PATH"));
        }
        assertTrue("Xvfb not on system path", available);
    }

    private static String getKnownInstalledPackageName() {
        return "wget";
    }

    private static String getKnownNotInstalledPackageName() {
        return "antlr";
    }

    private static String getKnownUnknownFormulaePackageName() {
        return "xvfb-manager-unittest-unicorn";
    }
}