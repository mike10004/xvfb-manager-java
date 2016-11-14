package com.github.mike10004.xvfbunittesthelp;

import org.junit.Assume;
import org.junit.BeforeClass;

import java.io.IOException;

import static org.junit.Assert.*;

public class DebianPackageManagerTest {

    private static PackageManager instance;

    @BeforeClass
    public static void checkPrerequisites() {
        instance = PackageManager.getInstance();
        Assume.assumeTrue("test can only run on debian", instance instanceof DebianPackageManager);
    }

    @org.junit.Test
    public void queryPackageInstalled_true() throws Exception {
        String installedPackageName = "coreutils";
        boolean installed = instance.queryPackageInstalled(installedPackageName);
        assertEquals("installed status", true, installed);
    }

    @org.junit.Test
    public void queryPackageInstalled_false() throws Exception {
        String absentPackageName = "xvfb-manager-unittest-unicorn";
        boolean installed = instance.queryPackageInstalled(absentPackageName);
        assertEquals("installed status", false, installed);
    }

    @org.junit.Test
    public void queryPackageVersion() throws Exception {
        String version = instance.queryPackageVersion("coreutils");
        System.out.format("coreutils: %s%n", version);
        assertNotNull("returns non-null", version);
        assertTrue("coreutils version starts with digit", version.matches("^\\d+\\b.*$"));
    }

    @org.junit.Test(expected = IOException.class)
    public void queryPackageVersion_notInstalled() throws Exception {
        instance.queryPackageVersion("xvfb-manager-unittest-unicorn");
    }


}