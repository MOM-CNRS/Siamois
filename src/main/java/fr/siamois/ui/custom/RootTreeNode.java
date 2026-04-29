package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.jspecify.annotations.NonNull;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNodeChildren;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class RootTreeNode<T extends AbstractEntityDTO> extends DefaultTreeNode<T> {

    private final RootChildList<T> childrens;
    private final int first;

    public RootTreeNode(int totalEntityCount, int first) {
        super("root", null, null);
        this.setRowKey("root");
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
        this.first = other.first;
        this.childrens = new RootChildList<>(0, this, other.first);
    }

    @Override
    public int getChildCount() {
        return childrens.size();
    }

    @Override
    public TreeNodeChildren<T> getChildren() {
        return childrens;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RootTreeNode<?> that)) return false;
        if (!super.equals(o)) return false;
        return first == that.first && Objects.equals(childrens, that.childrens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), childrens, first);
    }
}