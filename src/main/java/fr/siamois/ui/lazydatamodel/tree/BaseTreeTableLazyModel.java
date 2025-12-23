package fr.siamois.ui.lazydatamodel.tree;


import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * "Lazy" TreeTable model (for now loads the entire tree).
 * Later we can switch to real lazy node loading.
 */
@Getter
@Setter
public abstract class BaseTreeTableLazyModel<T, ID> implements Serializable {

    /** Filters (for later) */
    private String globalFilter;

    /** Cached root node */
    private transient TreeNode<T> root;

    /** Whether the cached tree is valid */
    private boolean initialized = false;

    /** Extract ID from entity */
    private final Function<T, ID> idExtractor;

    private transient Map<ID, TreeNode<T>> nodeById = new HashMap<>();

    protected BaseTreeTableLazyModel(Function<T, ID> idExtractor) {
        this.idExtractor = idExtractor;
    }

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

    /** Lookup node for a given entity id */
    public TreeNode<T> findNodeById(ID id) {
        if (id == null) return null;
        // ensure initialized
        getRoot();
        return nodeById.get(id);
    }

    /** Register node in the index (call during build) */
    protected void registerNode(T entity, TreeNode<T> node) {
        if (entity == null || node == null) return;
        ID id = idExtractor.apply(entity);
        if (id != null) {
            nodeById.put(id, node);
        }
    }

    /** Insert new node as FIRST child of clicked/parent node */
    public void insertChildFirst(ID parentId, T created) {
        TreeNode<T> parent = (parentId == null) ? getRoot() : findNodeById(parentId);

        if (parent == null) {
            // fallback: add under root
            TreeNode<T> n = new DefaultTreeNode<>(created, getRoot());
            registerNode(created, n);
            return;
        }

        TreeNode<T> newNode = new DefaultTreeNode<>(created, null);
        newNode.setParent(parent);
        parent.getChildren().add(0, newNode);
        parent.setExpanded(true);

        registerNode(created, newNode);
    }

    /**
     * Insert new parent at ROOT and move clicked node as ONLY child of new parent.
     * (your chosen simplified parent-insertion policy)
     */
    public void insertParentAtRoot(ID clickedId, T createdParent) {
        TreeNode<T> clicked = findNodeById(clickedId);

        // 1) create new parent under root
        TreeNode<T> newParentNode = new DefaultTreeNode<>(createdParent, getRoot());
        registerNode(createdParent, newParentNode);

        if (clicked == null) {
            // nothing to reattach
            return;
        }

        // 2) detach clicked from old parent
        TreeNode<T> oldParent = clicked.getParent();
        if (oldParent != null) {
            oldParent.getChildren().remove(clicked);
        }

        // 3) attach clicked as only child
        clicked.setParent(newParentNode);
        newParentNode.getChildren().clear();
        newParentNode.getChildren().add(clicked);
        newParentNode.setExpanded(true);
    }
}

