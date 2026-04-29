package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.DefaultTreeNodeChildren;
import org.primefaces.model.TreeNode;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class RootChildList<T extends AbstractEntityDTO> extends DefaultTreeNodeChildren<T> {

    private int totalEntityCount;
    private final transient List<TreeNode<T>> actualChildren;
    private final transient TreeNode<T> rootParent;
    private final int first;

    public RootChildList(int totalEntityCount, TreeNode<T> rootParent, int first) {
        this.totalEntityCount = totalEntityCount;
        this.rootParent = rootParent;
        this.first = first;
        this.actualChildren = new ArrayList<>();
    }

    @Override
    public int size() {
        return totalEntityCount;
    }

    public void incrementTotalEntityCount(int delta) {
        this.totalEntityCount = Math.max(0, this.totalEntityCount + delta);
    }

    @Override
    public boolean add(TreeNode<T> node) {
        if (node == null) {
            throw new IllegalArgumentException("node");
        }
        node.setParent(rootParent);
        return actualChildren.add(node);
    }

    @Override
    public void add(int index, TreeNode<T> node) {
        if (node == null) {
            throw new IllegalArgumentException("node");
        }
        node.setParent(rootParent);
        // Indices we receive from mutation calls are page-relative.
        int safeIndex = Math.max(0, Math.min(index, actualChildren.size()));
        actualChildren.add(safeIndex, node);
    }

    @Override
    public boolean remove(Object node) {
        return actualChildren.remove(node);
    }

    @Override
    public TreeNode<T> remove(int index) {
        return actualChildren.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return actualChildren.indexOf(o);
    }

    @Override
    public TreeNode<T> get(int index) {
        if (actualChildren.isEmpty()) {
            return createVirtualNode(index, null);
        }

        if (index >= first && index < first + actualChildren.size()) {
            TreeNode<T> realNode = actualChildren.get(index - first);
            ensureRowKey(realNode, index);
            return realNode;
        }

        TreeNode<T> safeRef = actualChildren.get(index % actualChildren.size());
        return createVirtualNode(index, safeRef);
    }

    private void ensureRowKey(TreeNode<T> node, int index) {
        if (node.getRowKey() == null) {
            String parentKey = rootParent.getRowKey();
            if (parentKey == null || "root".equals(parentKey)) {
                node.setRowKey(String.valueOf(index));
            } else {
                node.setRowKey(parentKey + "_" + index);
            }
        }
    }

    private TreeNode<T> createVirtualNode(int index, TreeNode<T> reference) {
        TreeNode<T> virtualNode;

        if (reference instanceof ChildTreeNode<T> c) {
            virtualNode = new ChildTreeNode<>(c);
        } else if (reference != null) {
            virtualNode = new DefaultTreeNode<>(reference.getType(), reference.getData(), null);
        } else {
            virtualNode = new DefaultTreeNode<>("dummy", null, null);
        }

        virtualNode.setParent(rootParent);

        ensureRowKey(virtualNode, index);

        return virtualNode;
    }

    @Override
    @NonNull
    public Stream<TreeNode<T>> stream() {
        // Only stream the real page nodes. size() still returns totalEntityCount
        // so the paginator sees the full count, but we never materialize millions
        // of virtual TreeNodes when JSF/PrimeFaces traverses children.
        return actualChildren.stream();
    }

    @Override
    @NonNull
    public Iterator<TreeNode<T>> iterator() {
        return actualChildren.iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RootChildList<?> that)) return false;
        if (!super.equals(o)) return false;
        return totalEntityCount == that.totalEntityCount && first == that.first && Objects.equals(actualChildren, that.actualChildren) && Objects.equals(rootParent, that.rootParent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), totalEntityCount, actualChildren, rootParent, first);
    }
}
