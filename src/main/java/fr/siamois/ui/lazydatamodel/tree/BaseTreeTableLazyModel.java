package fr.siamois.ui.lazydatamodel.tree;

import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;
import org.primefaces.model.TreeNodeChildren;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

/**
 * "Lazy" TreeTable model (for now loads the entire tree).
 * Later we can switch to real lazy node loading.
 *
 * Multi-hierarchy support:
 * - one entity ID can appear in multiple TreeNodes (DAG)
 * - therefore index is: ID -> Collection<TreeNode<T>>
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
    private final transient Function<T, ID> idExtractor;

    /** Multi-hierarchy index: entity id -> all nodes representing it */
    private transient Map<ID, List<TreeNode<T>>> nodesById = new HashMap<>();

    protected BaseTreeTableLazyModel(Function<T, ID> idExtractor) {
        this.idExtractor = idExtractor;
    }

    /**
     * PrimeFaces <p:treeTable value="..."> expects a TreeNode root.
     */
    public TreeNode<T> getRoot() {
        if (!initialized || root == null) {
            // rebuild index along with the tree
            nodesById = new HashMap<>();

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
        if (nodesById != null) {
            nodesById.clear();
        }
    }

    /**
     * Subclasses build the full tree (services call, mapping, etc.).
     * IMPORTANT: during build, call registerNode(entity, node) for every created node.
     */
    protected abstract TreeNode<T> buildTree();

    /** Lookup ALL nodes for a given entity id (multi-hierarchy) */
    public List<TreeNode<T>> findNodesById(ID id) {
        if (id == null) return List.of();
        // ensure initialized
        getRoot();
        List<TreeNode<T>> nodes = nodesById.get(id);
        return (nodes == null) ? List.of() : Collections.unmodifiableList(nodes);
    }

    /**
     * Convenience: returns the first node if you don't care which occurrence you get.
     * Prefer using findNodesById(...) when ambiguity matters.
     */
    public TreeNode<T> findAnyNodeById(ID id) {
        List<TreeNode<T>> nodes = findNodesById(id);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    /** Register node in the index (call during build and during inserts) */
    protected void registerNode(T entity, TreeNode<T> node) {
        if (entity == null || node == null) return;
        ID id = idExtractor.apply(entity);
        if (id == null) return;

        nodesById.computeIfAbsent(id, k -> new ArrayList<>(1)).add(node);
    }

    /** Insert new node as FIRST child of ALL matching parent nodes (multi-hierarchy) */
    public void insertChildFirst(ID parentId, T created) {

        // Determine all parent occurrences
        final List<TreeNode<T>> parents;
        if (parentId == null) {
            parents = List.of(getRoot());
        } else {
            parents = findNodesById(parentId);
        }

        if (parents == null || parents.isEmpty()) {
            // fallback: add under root
            TreeNode<T> n = new DefaultTreeNode<>(created, getRoot());
            registerNode(created, n);
            return;
        }

        // Insert under every occurrence of the parent entity
        for (TreeNode<T> parent : parents) {
            if (parent == null) continue;

            TreeNode<T> newNode = new DefaultTreeNode<>(created, null);
            newNode.setParent(parent);
            parent.getChildren().add(0, newNode);
            parent.setExpanded(true);

            registerNode(created, newNode);
        }
    }


    /**
     * Insert new parent at ROOT and move clicked node as ONLY child of new parent.
     * Note: if clickedId exists in multiple places, this method uses one arbitrary occurrence (first).
     * Prefer passing the clicked TreeNode directly in your UI flow when you need precision.
     */
    public void insertParentAtRoot(ID clickedId, T createdParent) {

        // 1) create new parent under root
        TreeNode<T> newParentNode = new DefaultTreeNode<>(createdParent, null);
        newParentNode.setParent(root);
        root.getChildren().add(0, newParentNode);
        registerNode(createdParent, newParentNode);

        // 2) pick a source occurrence to duplicate (any)
        TreeNode<T> source = findAnyNodeById(clickedId);
        if (source == null) {
            newParentNode.setExpanded(true);
            return;
        }

        // 3) duplicate: create a NEW node pointing to the SAME entity data
        T clickedEntity = source.getData();
        TreeNode<T> duplicate = new DefaultTreeNode<>(clickedEntity, newParentNode);

        // 4) index the duplicate occurrence
        registerNode(clickedEntity, duplicate);

        newParentNode.setExpanded(true);
    }

    // Get all the entities in the tree
    public Set<T> getAllEntitiesFromTree() {
        Set<T> allEntities = new HashSet<>();
        if (root != null) {
            collectEntitiesRecursively(root, allEntities);
        }
        return allEntities;
    }

    private void collectEntitiesRecursively(TreeNode<T> node, Set<T> entities) {

        if(node.getData() != null)  {
            entities.add(node.getData());
        }

        // Assuming treeLazyModel has a method to get children of a node
        TreeNodeChildren<T> children = node.getChildren();
        if (children != null) {
            for (TreeNode<T> child : children) {
                collectEntitiesRecursively(child, entities);
            }
        }
    }


}
