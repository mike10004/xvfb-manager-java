package com.github.mike10004.xvfbunittesthelp;

import com.google.common.base.Optional;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class FedoraPackageManagerTest {

    private static FedoraPackageManager instance;

    @BeforeClass
    public static void checkPrerequisites() {
        PackageManager instance_ = PackageManager.getInstance();
        Assume.assumeTrue("test can only run on fedora", instance_ instanceof FedoraPackageManager);
        instance = (FedoraPackageManager) instance_;
    }

    @Test
    public void checkInstalledPackageVersion_knownInstalled() throws Exception {
        Optional<String> result = instance.checkInstalledPackageVersion("coreutils");
        System.out.println("result = " + result);
        assertTrue("present", result.isPresent());
    }

    @Test
    public void checkInstalledPackageVersion_knownNotInstalled() throws Exception {
        Optional<String> result = instance.checkInstalledPackageVersion("xvfb-manager-unittest-help-unicorn");
        System.out.println("result = " + result);
        assertFalse("present", result.isPresent());
    }


}