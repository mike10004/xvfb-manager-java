package com.github.mike10004.xvfbmanager;

import com.google.common.base.Function;
import com.google.common.collect.TreeTraverser;

/**
 * Interface that represents a node in a tree data structure.
 * Nodes of this type are aware of their parents and children.
 * @param <T> node label type
 */
public interface TreeNode<T> extends Iterable<T> {

    /**
     * Gets an iterable of this node's children
     * @return an iterable of this node's children; never null
     */
    Iterable<TreeNode<T>> children();

    /**
     * Gets the level, which is the distance to the root. The level of the
     * root node is zero.
     * @return the level
     */
    int getLevel();

    /**
     * Gets the count of this node's children.
     * @return the child count
     */
    int getChildCount();

    /**
     * Gets this node's parent.
     * @return this node's parent node, or null if this is the root node
     */
    TreeNode<T> getParent();

    /**
     * Sets the parent of a given node.
     * @param parent the parent, or null to remove existing parent
     * @return the old parent, or null if no parent had been set
     */
    TreeNode<T> setParent(TreeNode<T> parent);

    /**
     * Checks whether this node is the root node of a tree.
     * @return true iff this node is the root node, meaning it has no parent
     */
    boolean isRoot();

    /**
     * Gets this node's label.
     * @return the label; allowing null depends on the implementation
     */
    T getLabel();

    /**
     * Adds a child to this node's children. Returns this instance (to facilitate chaining).
     * @param child the child to add
     * @return this instance
     */
    TreeNode<T> addChild(TreeNode<T> child);

    /**
     * Checks whether this node is a leaf node. Leaf nodes have no children.
     * @return true iff this node is a leaf node
     */
    boolean isLeaf();

    /**
     * Static utility methods relating to tree nodes.
     * @see TreeNode
     */
    class Utils {

        private Utils() {}

        /**
         * Creates and returns a new traverser instance.
         * @param <E> the tree node label type
         * @return a traverser
         */
        public static <E> TreeTraverser<TreeNode<E>> traverser() {
            return new TreeTraverser<TreeNode<E>>() {
                @Override
                public Iterable<TreeNode<E>> children(TreeNode root) {
                    return root.children();
                }
            };
        }

        /**
         * Creates and returns a new function that gets a node's children.
         * @param <E> the tree node label type
         * @return a new function
         */
        public static <E> Function<TreeNode<E>, Iterable<TreeNode<E>>> childrenFunction() {
            return new Function<TreeNode<E>, Iterable<TreeNode<E>>>() {
                @Override
                public Iterable<TreeNode<E>> apply(TreeNode<E> input) {
                    return input.children();
                }
            };
        }

        /**
         * Creates and returns a new function that returns the label of a tree node
         * @param <E> the tree node label type
         * @return a new function
         */
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