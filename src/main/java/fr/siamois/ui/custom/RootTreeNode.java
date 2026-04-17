package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.jspecify.annotations.NonNull;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNodeChildren;

public class RootTreeNode<T extends AbstractEntityDTO> extends DefaultTreeNode<T> {

    private final int totalEntityCount;
    private final RootChildList<T> childrens;
    private final int first;

    public RootTreeNode(int totalEntityCount, int first) {
        super("root", null, null);
        this.setRowKey("root");
        this.totalEntityCount = totalEntityCount;
        this.first = first;
        this.childrens = new RootChildList<>(totalEntityCount, this, first);
    }

    /**
     * Copy constructor used when filter is triggered
     * @param other The Root to copy
     */
    @SuppressWarnings("unused")
    public RootTreeNode(@NonNull RootTreeNode<T> other) {
        super(other.type, other.data, other.parent);
        this.totalEntityCount = 0;
        this.first = other.first;
        this.childrens = new RootChildList<>(0, this, other.first);
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