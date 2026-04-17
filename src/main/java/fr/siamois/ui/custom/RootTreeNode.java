package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.jspecify.annotations.NonNull;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;
import org.primefaces.model.TreeNodeChildren;

public class RootTreeNode<T extends AbstractEntityDTO> extends DefaultTreeNode<T> {

    private final int totalEntityCount;
    private final RootChildList<T> childrens;

    public RootTreeNode(int totalEntityCount, int pageSize, int first) {
        super("root", null, null);
        this.setRowKey("root");
        this.totalEntityCount = totalEntityCount;
        this.childrens = new RootChildList<>(totalEntityCount, this, pageSize, first);
    }

    /**
     * Copy constructor used when filter is triggered
     * @param other The Root to copy
     */
    @SuppressWarnings("unused")
    public RootTreeNode(@NonNull RootTreeNode<T> other) {
        super(other.type, other.data, other.parent);
        this.totalEntityCount = 0; // Valeur par défaut pour le clone
        this.childrens = new RootChildList<>(0, this, 10, 0); // Liste vide pour le clone
    }

    @Override
    public int getChildCount() {
        return totalEntityCount;
    }

    @Override
    public TreeNodeChildren<T> getChildren() {
        return childrens;
    }
}