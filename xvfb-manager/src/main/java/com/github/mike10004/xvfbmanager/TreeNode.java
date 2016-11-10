package com.github.mike10004.xvfbmanager;

import com.google.common.base.Function;
import com.google.common.collect.TreeTraverser;

public interface TreeNode<T> extends Iterable<T> {

    Iterable<TreeNode<T>> children();

    /**
     * Gets the level, which is the distance to the root. The level of the
     * root node is zero.
     * @return the level
     */
    int getLevel();
    int getChildCount();
    TreeNode<T> getParent();

    /**
     * Sets the parent of a given node.
     * @param parent the parent
     * @return the old parent, or null if no parent had been set
     */
    TreeNode<T> setParent(TreeNode<T> parent);
    boolean isRoot();
    T getLabel();

    /**
     * Adds a child to this node's children. Returns this instance (to facilitate chaining).
     * @param child the child to add
     * @return this instance
     */
    TreeNode<T> addChild(TreeNode<T> child);
    boolean isLeaf();

    class Utils {

        private Utils() {}

        public static <E> TreeTraverser<TreeNode<E>> traverser() {
            return new TreeTraverser<TreeNode<E>>() {
                @Override
                public Iterable<TreeNode<E>> children(TreeNode root) {
                    return root.children();
                }
            };
        }

        public static <E> Function<TreeNode<E>, Iterable<TreeNode<E>>> childrenFunction() {
            return new Function<TreeNode<E>, Iterable<TreeNode<E>>>() {
                @Override
                public Iterable<TreeNode<E>> apply(TreeNode<E> input) {
                    return input.children();
                }
            };
        }

        public static <E> Function<TreeNode<E>, E> labelFunction() {
            return new Function<TreeNode<E>, E>() {
                @Override
                public E apply(TreeNode<E> input) {
                    return input.getLabel();
                }
            };
        }

    }
}