package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.TreeNode;
import org.primefaces.model.TreeNodeChildren;
import org.primefaces.util.Callbacks;

import java.util.List;

/**
 * Mutates the tree currently displayed by the {@link LazyTreeTable} (the
 * {@code lazyRoot} owned by {@code BaseLazyDataModel}) to reflect newly
 * created/duplicated entities — without throwing the whole tree away.
 *
 * The displayed tree is built on demand from {@link RootTreeNode} (paginated
 * top-level rows) and {@link ChildTreeNode} (lazy-loaded sub-trees). All
 * insertions here mutate that tree directly so the user keeps their current
 * page, expanded nodes, scroll position, etc.
 */
public final class LazyTreeMutator {

    private LazyTreeMutator() {}

    /**
     * Insert a new entity as the first child of the root (top-level row,
     * position 0).
     */
    public static <T extends AbstractEntityDTO> ChildTreeNode<T> insertAtRoot(
            TreeNode<T> root,
            T created,
            Callbacks.SerializableFunction<T, List<T>> loadFn,
            Callbacks.SerializableFunction<T, Boolean> isLeafFn) {
        if (root == null || created == null) return null;

        ChildTreeNode<T> newNode = new ChildTreeNode<>(created, loadFn, isLeafFn);
        newNode.setParent(root);
        root.getChildren().add(0, newNode);
        bumpRootCount(root, 1);
        return newNode;
    }

    private static void bumpRootCount(TreeNode<?> root, int delta) {
        if (root instanceof RootTreeNode<?> rtn
                && rtn.getChildren() instanceof RootChildList<?> list) {
            list.incrementTotalEntityCount(delta);
        }
    }

    /**
     * Insert a new entity as the FIRST child of the node identified by
     * {@code parentId}. The parent is set as expanded so the new child is
     * visible immediately, even if the parent had not been expanded before.
     *
     * If the parent's children had not been loaded yet, we trigger the load
     * here (via {@link ChildTreeNode#getChildrenForceLoad()}) so the user
     * also sees the existing DB siblings, then insert the new node at the top.
     * If the just-saved entity is already among the loaded children — which
     * happens when the create operation committed the parent/child link
     * before we got here — we replace it instead of duplicating it.
     */
    public static <T extends AbstractEntityDTO> ChildTreeNode<T> insertChildFirst(
            TreeNode<T> root,
            Object parentId,
            T created,
            Callbacks.SerializableFunction<T, List<T>> loadFn,
            Callbacks.SerializableFunction<T, Boolean> isLeafFn) {
        if (root == null || parentId == null || created == null) return null;

        TreeNode<T> parent = findNodeByEntityId(root, parentId);
        if (parent == null) {
            // Fallback: parent not in current view (different page) — drop at root.
            return insertAtRoot(root, created, loadFn, isLeafFn);
        }

        TreeNodeChildren<T> children = childrenFor(parent);
        if (children == null) {
            // Parent doesn't expose a children list (true leaf with no
            // catched-load); fall back to insertion at root.
            return insertAtRoot(root, created, loadFn, isLeafFn);
        }

        // De-dupe: if the create operation already linked the new entity in
        // the database, the lazy fetch above will have brought it back.
        Long createdId = created.getId();
        if (createdId != null) {
            children.removeIf(child -> child != null
                    && child.getData() != null
                    && createdId.equals(child.getData().getId()));
        }

        ChildTreeNode<T> newNode = new ChildTreeNode<>(created, loadFn, isLeafFn);
        newNode.setParent(parent);
        children.add(0, newNode);
        parent.setExpanded(true);

        return newNode;
    }

    /**
     * Returns the children list of {@code parent}, lazy-loading it if needed
     * even if the node previously reported itself as a leaf. The mutator needs
     * to insert into this list, so {@link ChildTreeNode#getChildren()} returning
     * {@code null} (as it normally does for leaves) would be a blocker.
     */
    private static <T extends AbstractEntityDTO> TreeNodeChildren<T> childrenFor(TreeNode<T> parent) {
        if (parent instanceof ChildTreeNode<T> ctn) {
            return ctn.getChildrenForceLoad();
        }
        return parent.getChildren();
    }

    /**
     * Insert a freshly created parent at the root, and reparent the clicked
     * entity underneath it. The clicked node is removed from its previous
     * location in the tree and becomes the only child of the new parent.
     */
    public static <T extends AbstractEntityDTO> ChildTreeNode<T> insertParentAndReparent(
            TreeNode<T> root,
            Object clickedId,
            T createdParent,
            Callbacks.SerializableFunction<T, List<T>> loadFn,
            Callbacks.SerializableFunction<T, Boolean> isLeafFn) {
        if (root == null || createdParent == null) return null;

        ChildTreeNode<T> newParentNode = new ChildTreeNode<>(createdParent, loadFn, isLeafFn);
        newParentNode.setParent(root);

        TreeNode<T> clicked = (clickedId != null) ? findNodeByEntityId(root, clickedId) : null;
        boolean clickedWasAtRoot = clicked != null && clicked.getParent() == root;

        if (clicked != null && clicked.getParent() != null) {
            clicked.getParent().getChildren().remove(clicked);
            clicked.setParent(newParentNode);
            // newParent's children are populated by us — no DB fetch.
            newParentNode.markChildrenLoaded();
            newParentNode.getChildren().add(clicked);
        } else {
            newParentNode.markChildrenLoaded();
        }

        root.getChildren().add(0, newParentNode);
        newParentNode.setExpanded(true);
        // +1 for the new parent at root, -1 if the clicked entity used to be
        // at root and is no longer.
        bumpRootCount(root, clickedWasAtRoot ? 0 : 1);
        return newParentNode;
    }

    /**
     * Insert a new entity right below the clicked one, as its sibling.
     * Handles both a top-level source and a deeper source (child of some node).
     */
    public static <T extends AbstractEntityDTO> ChildTreeNode<T> insertSiblingBelow(
            TreeNode<T> root,
            Object sourceId,
            T created,
            Callbacks.SerializableFunction<T, List<T>> loadFn,
            Callbacks.SerializableFunction<T, Boolean> isLeafFn) {
        if (root == null || sourceId == null || created == null) return null;

        TreeNode<T> source = findNodeByEntityId(root, sourceId);
        if (source == null || source.getParent() == null) {
            return insertAtRoot(root, created, loadFn, isLeafFn);
        }

        TreeNode<T> parent = source.getParent();
        List<TreeNode<T>> siblings = parent.getChildren();
        int idx = siblings.indexOf(source);
        int insertAt = (idx < 0) ? siblings.size() : idx + 1;

        ChildTreeNode<T> newNode = new ChildTreeNode<>(created, loadFn, isLeafFn);
        newNode.setParent(parent);
        siblings.add(insertAt, newNode);
        if (parent == root) {
            bumpRootCount(root, 1);
        }
        return newNode;
    }

    /** Depth-first lookup of the first node whose entity id equals {@code id}. */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractEntityDTO> TreeNode<T> findNodeByEntityId(
            TreeNode<T> root, Object id) {
        if (root == null || id == null) return null;
        for (TreeNode<T> child : root.getChildren()) {
            T data = child.getData();
            if (data != null && id.equals(data.getId())) {
                return child;
            }
            // Only descend into already-loaded subtrees: PrimeFaces' lazy
            // children should not be force-loaded just to find a match.
            if (child instanceof ChildTreeNode<?> ctn && !ctn.isLoaded()) {
                continue;
            }
            TreeNode<T> deeper = findNodeByEntityId(child, id);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }
}
