package fr.siamois.ui.lazydatamodel.tree;


import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.TreeNode;
import org.primefaces.model.DefaultTreeNode;

import java.io.Serializable;

/**
 * "Lazy" TreeTable model (for now loads the entire tree).
 * Later we can switch to real lazy node loading.
 */
@Getter
@Setter
public abstract class BaseTreeTableLazyModel<T> implements Serializable {

    /** Filters (for later) */
    private String globalFilter;

    /** Cached root node */
    private transient TreeNode<T> root;

    /** Whether the cached tree is valid */
    private boolean initialized = false;

    /**
     * PrimeFaces <p:treeTable value="..."> expects a TreeNode root.
     */
    public TreeNode<T> getRoot() {
        if (!initialized || root == null) {
            root = buildTree();
            if (root == null) {
                // never return null to the component
                root = new DefaultTreeNode<>(null, null);
            }
            initialized = true;
        }
        return root;
    }

    /**
     * Force rebuilding the tree (call after big changes).
     */
    public void reset() {
        this.root = null;
        this.initialized = false;
    }

    /**
     * Subclasses build the full tree (services call, mapping, etc.).
     */
    protected abstract TreeNode<T> buildTree();
}

