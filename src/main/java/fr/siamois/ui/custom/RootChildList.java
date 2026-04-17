package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.DefaultTreeNodeChildren;
import org.primefaces.model.TreeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RootChildList<T extends AbstractEntityDTO> extends DefaultTreeNodeChildren<T> {

    private final int totalEntityCount;
    private final List<TreeNode<T>> actualChildren;
    private final TreeNode<T> parent;
    private final int pageSize;
    private final int first;

    public RootChildList(int totalEntityCount, TreeNode<T> parent, int pageSize, int first) {
        this.totalEntityCount = totalEntityCount;
        this.parent = parent;
        this.pageSize = pageSize;
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

        // 1. C'est la page active (en mémoire)
        if (index >= first && index < first + actualChildren.size()) {
            // On renvoie l'objet persistant. S'il n'a pas de rowKey, on le sécurise.
            TreeNode<T> realNode = actualChildren.get(index - first);
            ensureRowKey(realNode, index);
            return realNode;
        }

        // 2. C'est une autre page (virtuelle)
        TreeNode<T> safeRef = actualChildren.get(index % actualChildren.size());
        return createVirtualNode(index, safeRef);
    }

    // --- Méthodes utilitaires pour la gestion parfaite des RowKeys ---

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

        // LA MAGIE EST ICI : On grave le rowKey dans le marbre dès la création
        ensureRowKey(virtualNode, index);

        return virtualNode;
    }

    @Override
    public Stream<TreeNode<T>> stream() {
        List<TreeNode<T>> list = new ArrayList<>(totalEntityCount);
        for (int i = 0; i < totalEntityCount; i++) {
            list.add(get(i));
        }
        return list.stream();
    }

}
