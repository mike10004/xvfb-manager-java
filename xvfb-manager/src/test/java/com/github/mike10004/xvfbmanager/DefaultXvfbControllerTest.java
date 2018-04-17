package com.github.mike10004.xvfbmanager;

import com.github.mike10004.nativehelper.subprocess.ProcessMonitor;
import com.github.mike10004.xvfbmanager.DefaultXvfbController.XLockFileChecker;
import com.github.mike10004.xvfbmanager.DefaultXvfbController.XwininfoParser;
import com.github.mike10004.xvfbmanager.DefaultXvfbController.XwininfoXwindowParser;
import com.github.mike10004.xvfbmanager.XvfbController.XWindow;
import com.github.mike10004.xvfbmanager.XvfbManager.DisplayReadinessChecker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DefaultXvfbControllerTest {

    @Test
    public void XwininfoParser_parseWindow() throws Exception {
        XwininfoXwindowParser parser = new XwininfoXwindowParser();
        List<Pair<String, XWindow>> testCases = Arrays.asList(
                Pair.of("     0x1c00008 \"jetbrains-idea\": (\"jetbrains-idea\" \"jetbrains-idea\")  1x1+1+1  +1+1",
                        new XWindow("0x1c00008", "jetbrains-idea", "")),
                Pair.of("     0x200012 (has no name): ()  1x1+0+0  +0+0", new XWindow("0x200012", null, "")),
                Pair.of("     0x1400002 \"Build failed: common-helper 1.0.81 - cbacon@example.com - Example, LLC Mail - Google Chrome\": (\"google-chrome\" \"Google-chrome\")  1050x1087+0+56  +0+56",
                        new XWindow("0x1400002", "Build failed: common-helper 1.0.81 - cbacon@example.com - Example, LLC Mail - Google Chrome", "")),
                Pair.of("     0xe00015 \"mutter guard window\": ()  2970x1680+0+0  +0+0", new XWindow("0xe00015", "mutter guard window", ""))
        );
        for (Pair<String, XWindow> testCase : testCases) {
            XWindow expected = testCase.getRight();
            String line = testCase.getLeft();
            XWindow actual = parser.parseWindow(line, false);
            assertEquals("id", expected.id, actual.id);
            assertEquals("title", expected.title, actual.title);
        }
    }

    @Test
    public void XwininfoParser_parse_dummy() throws Exception {
        String stdout =
                "  root\n" +
                        "     A\n" +
                        "     B\n" +
                        "        C\n" +
                        "        D\n" +
                        "     E\n" +
                        "        F\n" +
                        "     G\n" +
                        "     H\n" +
                        "     I\n" +
                        "        J\n" +
                        "        K\n" +
                        "           L\n" +
                        "              M\n" +
                        "        N\n" +
                        "        O\n";
        testXwininfoParser_parse(new VerboseParser<String>(){
            @Override
            protected String parseWindow(String line, boolean root) {
                return line.trim();
            }
        }, stdout, 6);
    }

    @Test
    public void xwininfoParser_parse_simple() throws Exception {
        String stdout = "xwininfo: Window id: 0x25c (the root window) (has no name)\n" +
                "\n" +
                "  Root window id: 0x25c (the root window) (has no name)\n" +
                "  Parent window id: 0x0 (none)\n" +
                "     0 children.\n";
        testXwininfoParser_parse(new XwininfoXwindowParser(), stdout, 0);
    }

    @Test
    public void XwininfoParser_parse_real() throws Exception {
        String stdout = Resources.toString(getClass().getResource("/xwininfo-output.txt"), StandardCharsets.UTF_8);
        testXwininfoParser_parse(new XwininfoXwindowParser(), stdout, 60);
    }

    private static boolean verbose = Boolean.parseBoolean(System.getProperty("verbose", "false"));

    private static abstract class VerboseParser<E> extends XwininfoParser<E> {
        private PrintStream out = verbose ? System.out : new PrintStream(ByteStreams.nullOutputStream());

        private String summarize(String line) {
            return StringEscapeUtils.escapeJava(StringUtils.abbreviate(line, 64));
        }

        private String summarize(E w) {
            return StringEscapeUtils.escapeJava(StringUtils.abbreviate(String.valueOf(w), 64));
        }

        @Override
        protected boolean skip(String line, String explanation) {
            out.format("skipped line: %s: %s%n", explanation, summarize(line));
            return super.skip(line, explanation);
        }

        @Override
        protected void foundAncestor(int indent, TreeNode<E> node) {
            out.format("found ancestor: %s%n", summarize(node.getLabel()));
        }

        @Override
        protected void foundSibling(int indent, TreeNode<E> node) {
            out.format("found sibling: %s%n", summarize(node.getLabel()));
        }

        @Override
        protected void foundChild(int indent, TreeNode<E> node) {
            out.format("found child: %s%n", summarize(node.getLabel()));
        }

        @Override
        protected boolean foundRoot(TreeNode<E> root) {
            out.format("found root: %s%n", summarize(root.getLabel()));
            return super.foundRoot(root);
        }

    }

    private <T> void testXwininfoParser_parse(XwininfoParser<T> parser, String stdout, int expectedRootChildCount) throws IOException {
        TreeNode<T> root = parser.parse(CharSource.wrap(stdout));
        for (T label : root.breadthFirstTraversal().labels()) {
            System.out.println(label);
        }
        assertEquals("root child count", expectedRootChildCount, root.getChildCount());
        String reconstructed = reconstruct(root, XwininfoParser.INDENT_PER_LEVEL);
        System.out.println(reconstructed);
    }

    @SuppressWarnings("SameParameterValue")
    private static void repeat(String s, int count, PrintWriter out) {
        for (int i = 0; i < count; i++) {
            out.print(s);
        }
    }

    private <E> String reconstruct(TreeNode<E> root, int indentPerLevel) {
        StringWriter sw = new StringWriter(1024);
        PrintWriter out = new PrintWriter(sw);
        List<? extends TreeNode<E>> enumeration = ImmutableList.copyOf(TreeNode.Utils.<E>traverser().depthFirstPreOrder(root));
        Iterator<? extends TreeNode<?>> poe = enumeration.iterator();
        int prevDepth = 0;
        while (poe.hasNext()) {
            TreeNode<?> node = poe.next();
            int level = node.getLevel();
            repeat(" ", XwininfoParser.COMMON_INDENT + level * indentPerLevel, out);
            assert level <= prevDepth || !node.isRoot() : "expected depth " + level + " <= prevDepth " + prevDepth + " or node not to be root: " + node;
            if (level > prevDepth && node.getParent().getChildCount() > 0) {
                int count = node.getParent().getChildCount();
                out.format("%d %s:%n", count, count > 1 ? "children" : "child");
                repeat(" ", XwininfoParser.COMMON_INDENT + level * indentPerLevel, out);
            }
            out.println(node.getLabel());
            prevDepth = level;
        }
        out.flush();
        return sw.toString();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void configureEnvironment() {
        XvfbController ctrl = new DefaultXvfbController(EasyMock.createMock(ProcessMonitor.class), ":123",
                EasyMock.createMock(DisplayReadinessChecker.class),
                EasyMock.createMock(Screenshooter.class),
                EasyMock.createMock(Sleeper.class),
                EasyMock.createMock(XLockFileChecker.class));
        Map<String, String> env = ctrl.configureEnvironment(new HashMap<>());
        assertEquals(ImmutableMap.of(XvfbController.ENV_DISPLAY, ":123"), env);
    }

}