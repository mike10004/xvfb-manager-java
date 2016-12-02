package com.github.mike10004.xvfbunittesthelp;

import com.google.common.collect.Ordering;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XDiagnosticRuleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void listDotXFiles() throws Exception {
        File root = tmp.newFolder();
        for (char ch = 'A'; ch <= 'C'; ch++) {
            com.google.common.io.Files.touch(new File(root, String.valueOf(ch)));
        }
        int numXFiles = 3;
        Random random = new Random(XDiagnosticRule.class.hashCode());
        Set<Path> xFiles = new LinkedHashSet<>();
        for (int i = 0; i < numXFiles; i++) {
            String name = ".X" + (i * random.nextInt(50));
            File f = new File(root, name);
            xFiles.add(f.toPath());
            com.google.common.io.Files.touch(f);
        }
        List<Path> actualXFiles = new XDiagnosticRule().listDotXFiles(root.toPath());
        actualXFiles = Ordering.natural().immutableSortedCopy(actualXFiles);
        assertEquals("lists", Ordering.natural().immutableSortedCopy(xFiles), actualXFiles);
    }

    @Test
    public void findTempDirectories() throws Exception {
        Set<Path> paths = new XDiagnosticRule().findTempDirectories();
        System.out.format("findTempDirectories() -> %s%n", paths);
        assertTrue("has directories", paths.stream().allMatch(p -> p.toFile().isDirectory()));
    }

}