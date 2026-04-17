package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.DefaultTreeNodeChildren;
import org.primefaces.model.TreeNode;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class RootChildList<T extends AbstractEntityDTO> extends DefaultTreeNodeChildren<T> {

    private final int totalEntityCount;
    private final List<TreeNode<T>> actualChildren;
    private final TreeNode<T> parent;
    private final int first;

    public RootChildList(int totalEntityCount, TreeNode<T> parent, int first) {
        this.totalEntityCount = totalEntityCount;
        this.parent = parent;
        this.first = first;
        this.actualChildren = new ArrayList<>();
    }

    @Override
    public int size() {
        return totalEntityCount;
    }

    @Override
    public boolean add(TreeNode<T> node) {
        if (node == null) {
            throw new IllegalArgumentException("node");
        }
        node.setParent(parent);
        return actualChildren.add(node);
    }

    @Override
    public TreeNode<T> get(int index) {
        if (actualChildren.isEmpty()) {
            return createVirtualNode(index, null);
        }

        if (index >= first && index < first + actualChildren.size()) {
            // On renvoie l'objet persistant. S'il n'a pas de rowKey, on le sécurise.
            TreeNode<T> realNode = actualChildren.get(index - first);
            ensureRowKey(realNode, index);
            return realNode;
        }

        TreeNode<T> safeRef = actualChildren.get(index % actualChildren.size());
        return createVirtualNode(index, safeRef);
    }

    private void ensureRowKey(TreeNode<T> node, int index) {
        if (node.getRowKey() == null) {
            String parentKey = parent.getRowKey();
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

        virtualNode.setParent(parent);

        ensureRowKey(virtualNode, index);

        return virtualNode;
    }

    @Override
    @NonNull
    public Stream<TreeNode<T>> stream() {
        List<TreeNode<T>> list = new ArrayList<>(totalEntityCount);
        for (int i = 0; i < totalEntityCount; i++) {
            list.add(get(i));
        }
        return list.stream();
    }

    @Override
    @NonNull
    public Iterator<TreeNode<T>> iterator() {
        return new Iterator<>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < totalEntityCount;
            }

            @Override
            public TreeNode<T> next() {
                return get(cursor++);
            }
        };
    }

}
