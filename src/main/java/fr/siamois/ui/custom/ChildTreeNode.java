package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.DefaultTreeNodeChildren;
import org.primefaces.model.LazyTreeNode;
import org.primefaces.model.TreeNodeChildren;
import org.primefaces.util.Callbacks;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lazy tree node that:
 *  - lazily loads its children via a configured load function the first time
 *    {@link #getChildren()} or {@link #getChildCount()} is accessed,
 *  - wraps each child as another {@link ChildTreeNode} (so the entire subtree
 *    is composed of nodes with a copy constructor — required by PrimeFaces'
 *    filter cloning),
 *  - exposes {@link #markChildrenLoaded()} to skip the lazy fetch when we have
 *    already populated the children manually (used when inserting a new entity
 *    in the visible tree without re-querying the database).
 */
@EqualsAndHashCode(callSuper=true)
public class ChildTreeNode<T extends AbstractEntityDTO> extends DefaultTreeNode<T> implements LazyTreeNode {

    @Getter
    private final Callbacks.SerializableFunction<T, List<T>> catchedLoad;
    @Getter
    private final Callbacks.SerializableFunction<T, Boolean> catchedIsLeaf;
    /**
     * -- SETTER --
     *  Set the ancestor closure shared with the whole filtered subtree. A
     *  lazy-loaded child is auto-expanded when its id is in
     *  AND it is NOT itself a match (i.e. it's an ancestor on the path leading
     *  to a match). Match nodes stay collapsed so the user sees them as a
     *  single row in the result list and can opt into expanding them.
     */
    @Setter
    private Set<Long> ancestorClosure;
    /**
     * -- SETTER --
     *  Set the match-id set propagated down the filtered subtree. Used to
     *  decide whether a closure member is "just an ancestor" (auto-expanded)
     *  or "the match itself" (left collapsed by default).
     */
    @Setter
    private Set<Long> matchIds;
    private boolean loaded = false;

    public ChildTreeNode(T data,
                         Callbacks.SerializableFunction<T, List<T>> catchedLoad,
                         Callbacks.SerializableFunction<T, Boolean> catchedIsLeaf) {
        super(data);
        this.catchedLoad = catchedLoad;
        this.catchedIsLeaf = catchedIsLeaf;
    }

    /** Copy constructor — used by PrimeFaces' filter cloning. */
    public ChildTreeNode(ChildTreeNode<T> other) {
        super(other.getData());
        this.catchedLoad = other.catchedLoad;
        this.catchedIsLeaf = other.catchedIsLeaf;
        this.ancestorClosure = other.ancestorClosure;
        this.matchIds = other.matchIds;
        this.loaded = false;
    }

    /**
     * Mark this node's children as already populated, so {@link #getChildren()}
     * will return them as-is instead of fetching from the database.
     */
    public void markChildrenLoaded() {
        this.loaded = true;
    }

    @Override
    public boolean isLeaf() {
        // Once children have been (lazily or manually) loaded, the in-memory
        // list is the source of truth — a node we just inserted a child into
        // is no longer a leaf, even if the original isLeaf callback says so.
        if (loaded) {
            return super.getChildCount() == 0;
        }
        if (catchedIsLeaf == null) {
            return false;
        }
        return Boolean.TRUE.equals(catchedIsLeaf.apply(getData()));
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public TreeNodeChildren<T> getChildren() {
        if (loaded) {
            return super.getChildren();
        }
        if (isLeaf()) {
            return new DefaultTreeNodeChildren<>();
        }
        lazyLoad();
        return super.getChildren();
    }

    @Override
    public int getChildCount() {
        if (loaded) {
            return super.getChildCount();
        }
        if (isLeaf()) {
            return 0;
        }
        lazyLoad();
        return super.getChildCount();
    }

    /**
     * Force the lazy children fetch even when {@link #isLeaf()} reports {@code true}.
     * Used by the tree mutator just before inserting a manually-created child:
     * a node that had no children up to that point is still a valid parent for
     * the new entity, but the regular {@link #getChildren()} would return
     * {@code null} and prevent insertion. Returns the (now non-null) children
     * list, fetching any existing DB siblings on the way.
     */
    public TreeNodeChildren<T> getChildrenForceLoad() {
        lazyLoad();
        return super.getChildren();
    }

    private void lazyLoad() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (catchedLoad == null) {
            return;
        }
        List<T> children = catchedLoad.apply(getData());
        if (children == null) {
            return;
        }
        TreeNodeChildren<T> childList = super.getChildren();
        for (T c : children) {
            ChildTreeNode<T> node = new ChildTreeNode<>(c, catchedLoad, catchedIsLeaf);
            node.setParent(this);
            node.setAncestorClosure(ancestorClosure);
            node.setMatchIds(matchIds);
            // Auto-expand only pure ancestors (in closure but not matches), so
            // the path to a deeper match opens up while the match itself stays
            // collapsed.
            if (ancestorClosure != null && c != null && ancestorClosure.contains(c.getId())
                    && (matchIds == null || !matchIds.contains(c.getId()))) {
                node.setExpanded(true);
            }
            childList.add(node);
        }
    }

}
