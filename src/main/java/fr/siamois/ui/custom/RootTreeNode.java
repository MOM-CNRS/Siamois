package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.DefaultTreeNode;

import java.util.ArrayList;

/**
 * This class is used by {@link LazyTreeTable} to implement pagination for the root nodes
 * @param <T> The type of DTOs contained
 */
public class RootTreeNode<T extends AbstractEntityDTO> extends DefaultTreeNode<T> implements Cloneable {

    public RootTreeNode() {
        super(null, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public RootTreeNode<T> clone() {
        RootTreeNode<T> copy;
        try {
            copy = (RootTreeNode<T>) super.clone();
        } catch (CloneNotSupportedException e) {
            copy = new RootTreeNode<>();
        }
        if (!isLeaf()) {
            if (copy.getChildren() == null)
                setChildren(new ArrayList<>());
            copy.getChildren().addAll(this.getChildren());
        }
        return copy;
    }
}
