package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.lazydatamodel.LazyModel;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.*;
import org.primefaces.util.Callbacks;

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
public abstract class BaseTreeTableLazyModel<T extends AbstractEntityDTO, ID> implements Serializable, LazyModel {

    /** Filters (for later) */
    private String globalFilter;

    /** Cached root node */
    private transient LazyDefaultTreeNode<T> root;

    protected int first = 0;
    protected int pageSizeState = 10;
    protected transient Set<SortMeta> sortBy = new HashSet<>();

    /** Whether the cached tree is valid */
    private boolean initialized = false;

    /** Extract ID from entity */
    private final transient Function<T, ID> idExtractor;

    /** Multi-hierarchy index: entity id -> all nodes representing it */
    private transient Map<ID, List<LazyDefaultTreeNode<T>>> nodesById = new HashMap<>();

    protected BaseTreeTableLazyModel(Function<T, ID> idExtractor) {
        this.idExtractor = idExtractor;
    }

    /**
     * PrimeFaces <p:treeTable value="..."> expects a TreeNode root.
     */
    public LazyDefaultTreeNode<T> getRoot() {
        if (!initialized || root == null) {
            // rebuild index along with the tree
            nodesById = new HashMap<>();
            root = buildTree();

            if (root == null) {
                // never return null to the component
                root = new LazyDefaultTreeNode<>(null, null, null);
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


    /** Lookup ALL nodes for a given entity id (multi-hierarchy) */
    public List<LazyDefaultTreeNode<T>> findNodesById(ID id) {
        if (id == null) return List.of();
        // ensure initialized
        getRoot();
        List<LazyDefaultTreeNode<T>> nodes = nodesById.get(id);
        return (nodes == null) ? List.of() : Collections.unmodifiableList(nodes);
    }

    /**
     * Convenience: returns the first node if you don't care which occurrence you get.
     * Prefer using findNodesById(...) when ambiguity matters.
     */
    public LazyDefaultTreeNode<T> findAnyNodeById(ID id) {
        List<LazyDefaultTreeNode<T>> nodes = findNodesById(id);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    /** Register node in the index (call during build and during inserts) */
    protected void registerNode(T entity, LazyDefaultTreeNode<T> node) {
        if (entity == null || node == null) return;
        ID id = idExtractor.apply(entity);
        if (id == null) return;

        nodesById.computeIfAbsent(id, k -> new ArrayList<>(1)).add(node);
    }

    /** Insert new node as FIRST child of ALL matching parent nodes (multi-hierarchy) */
    public void insertChildFirst(ID parentId, T created) {

        // Determine all parent occurrences
        final List<LazyDefaultTreeNode<T>> parents;
        if (parentId == null) {
            parents = List.of(getRoot());
        } else {
            parents = findNodesById(parentId);
        }

        if (parents == null || parents.isEmpty()) {
            // fallback: add under root
            LazyDefaultTreeNode<T> newNode = new LazyDefaultTreeNode<>(created,
                    (Callbacks.SerializableFunction<T, List<T>>) this::loadFunction,
                    (Callbacks.SerializableFunction<T, Boolean>) this::isLeaf
            );
            newNode.setParent(getRoot());
            registerNode(created, newNode);
            return;
        }

        // Insert under every occurrence of the parent entity
        for (LazyDefaultTreeNode<T> parent : parents) {
            if (parent == null) continue;

            LazyDefaultTreeNode<T> newNode = new LazyDefaultTreeNode<>(created,
                    (Callbacks.SerializableFunction<T, List<T>>) this::loadFunction,
                    (Callbacks.SerializableFunction<T, Boolean>) this::isLeaf
            );
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
        LazyDefaultTreeNode<T> newParentNode = new LazyDefaultTreeNode<>(createdParent,  (Callbacks.SerializableFunction<T, List<T>>) this::loadFunction,
                (Callbacks.SerializableFunction<T, Boolean>) this::isLeaf);
        newParentNode.setParent(root);
        root.getChildren().add(0, newParentNode);
        registerNode(createdParent, newParentNode);

        // 2) pick a source occurrence to duplicate (any)
        LazyDefaultTreeNode<T> source = findAnyNodeById(clickedId);
        if (source == null) {
            newParentNode.setExpanded(true);
            return;
        }

        // 3) duplicate: create a NEW node pointing to the SAME entity data
        T clickedEntity = source.getData();
        LazyDefaultTreeNode<T> duplicate = new LazyDefaultTreeNode<>(clickedEntity, (Callbacks.SerializableFunction<T, List<T>>) this::loadFunction,
                (Callbacks.SerializableFunction<T, Boolean>) this::isLeaf);

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

    public int getFirstIndexOnPage() {
        return first + 1; // Adding 1 because indexes are zero-based
    }

    public int getLastIndexOnPage() {
        int last = first + pageSizeState;
        int total = getRowCount();
        return Math.min(last, total); // Ensure it doesn’t exceed total records
    }

    public int getRowCount() {
        return getRoot().getChildren().size();
    }

    protected abstract List<T> fetchRoots();
    protected abstract List<T> fetchChildren(T parentUnit);
    protected void initializeAssociations(T child) {
        // empty default
    }

    @ExecutionTimeLogger
    protected LazyDefaultTreeNode<T> buildTree() {

        return new LazyDefaultTreeNode<>(null,
                (Callbacks.SerializableFunction<T, List<T>>) this::loadFunction,
                (Callbacks.SerializableFunction<T, Boolean>) this::isLeaf
        );
    }

    @ExecutionTimeLogger
    protected abstract Boolean isLeaf( T node) ;

    @ExecutionTimeLogger
    protected List<T> loadFunction(T parentUnit) {
        List<T> children = fetchChildren(parentUnit);

        for (T child : children) {
            initializeAssociations(child);
        }

        return children;
    }




}
