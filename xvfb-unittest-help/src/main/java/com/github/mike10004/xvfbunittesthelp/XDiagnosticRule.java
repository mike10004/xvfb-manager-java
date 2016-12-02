package com.github.mike10004.xvfbunittesthelp;

import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class XDiagnosticRule extends ExternalResource {

    private final PrintStream out;
    private final boolean disabled;
    private volatile Set<Path> directories = null;

    public XDiagnosticRule() {
        this(System.out);
    }

    public XDiagnosticRule(PrintStream out) {
        this(out, false);
    }

    protected XDiagnosticRule(PrintStream out, boolean disabled) {
        this.out = checkNotNull(out);
        this.disabled = disabled;
    }

    private static final XDiagnosticRule disabledInstance = new XDiagnosticRule(System.out, true);

    public static XDiagnosticRule getDisabledInstance() {
        return disabledInstance;
    }

    @Override
    protected void before() throws Throwable {
        if (disabled) {
            return;
        }
        printDotXFiles();
    }

    @Override
    protected void after() {
        if (disabled) {
            return;
        }
        printDotXFiles();
    }

    protected Set<Path> getDirectories() {
        if (directories == null) {
            directories = findTempDirectories();
        }
        return directories;
    }

    protected void printDotXFiles() {
        Set<Path> directories_ = getDirectories();
        out.format("XDiagnosticRule: printing .X files from %d temp-like directories%n", directories_.size());
        int n = 0;
        for (Path directory : directories_) {
            List<Path> files;
            try {
                files = listDotXFiles(directory);
            } catch (IOException | RuntimeException e) {
                out.format("XDiagnosticRule: failed to gather file list from %s due to %s%n", directory, e);
                e.printStackTrace(out);
                continue;
            }
            for (Path file : files) {
                n++;
                out.format("  %s%n", file);
            }
        }
        out.format("XDiagnosticRule: finished printing %d files from %d directories%n", n, directories_.size());
    }


    protected List<Path> listDotXFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>(){
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.toFile().getName().toLowerCase().startsWith(".x");
            }
        };
        try (DirectoryStream<Path> listing = Files.newDirectoryStream(directory, filter)) {
            Iterables.addAll(files, listing);
        }
        return files;
    }

    protected Set<Path> findTempDirectories() {
        Set<String> paths = new HashSet<>(3);
        paths.add("/tmp");
        paths.add(FileUtils.getTempDirectory().getAbsolutePath());
        paths.add(System.getProperty("java.io.tmpdir"));
        for (String envVarName : new String[]{"TMP", "TEMP", "TEMPDIR", "TMPDIR"}) {
            String envVarValue = System.getenv(envVarName);
            if (envVarValue != null) {
                paths.add(envVarValue);
            }
        }
        return paths.stream()
                .map(p -> new File(FilenameUtils.normalizeNoEndSeparator(p)))
                .filter(f -> f.isDirectory())
                .map(f -> f.toPath()).collect(Collectors.toSet());
    }
}
