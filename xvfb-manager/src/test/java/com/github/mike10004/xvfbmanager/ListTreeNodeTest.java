package com.github.mike10004.xvfbmanager;

import com.google.common.collect.Iterables;
import org.junit.Test;

import static org.junit.Assert.*;

public class ListTreeNodeTest {

    @Test
    public void getLevel() throws Exception {
        TreeNode<Integer> l1, l2, l3;
        TreeNode<Integer> root = node(0)
                .addChild(l1 = node(1))
                .addChild(node(1).addChild(l2 = node(2)).addChild(node(2).addChild(l3 = node(3))))
                .addChild(node(1));
        System.out.format("bfs: %s%n", Iterables.toString(root.breadthFirstTraversal().labels()));
        assertEquals("level", 0, root.getLevel());
        assertEquals("level", 1, l1.getLevel());
        assertEquals("level", 2, l2.getLevel());
        assertEquals("level", 3, l3.getLevel());
    }

    private static <E> ListTreeNode<E> node(E label) {
        return new ListTreeNode<>(label);
    }

    @Test
    public void isRoot() throws Exception {
        ListTreeNode<String> root = new ListTreeNode<>("root");
        assertTrue("isRoot", root.isRoot());
        ListTreeNode<String> child = new ListTreeNode<>("child");
        root.addChild(child);
        assertFalse("isRoot", child.isRoot());
    }

    @Test
    public void isLeaf() throws Exception {
        ListTreeNode<String> root = new ListTreeNode<>("root");
        assertTrue("isLeaf", root.isLeaf());
        ListTreeNode<String> child = new ListTreeNode<>("child");
        root.addChild(child);
        assertFalse("isLeaf", root.isLeaf());
        assertTrue("isLeaf", child.isLeaf());
    }

}