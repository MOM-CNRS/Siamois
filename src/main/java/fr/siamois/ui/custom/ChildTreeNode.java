package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.LazyDefaultTreeNode;
import org.primefaces.util.Callbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is used by the {@link org.primefaces.model.LazyDataModel#load(int, int, Map, Map)} function to return
 * the children and grandchildren of a node
 * @param <T> The type of DTOs contained
 */
public class ChildTreeNode<T extends AbstractEntityDTO> extends LazyDefaultTreeNode<T> implements Cloneable {

    private final Callbacks.SerializableFunction<T, List<T>> catchedLoadFunction;
    private final Callbacks.SerializableFunction<T, Boolean> catchedIsLeafFunction;

    public  ChildTreeNode(T data,
                          Callbacks.SerializableFunction<T, List<T>> loadFunction,
                          Callbacks.SerializableFunction<T, Boolean> isLeafFunction) {
        super(data, loadFunction, isLeafFunction);
        catchedLoadFunction = loadFunction;
        catchedIsLeafFunction = isLeafFunction;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ChildTreeNode<T> clone() {
        ChildTreeNode<T> copy;
        try {
            copy = (ChildTreeNode<T>) super.clone();
        } catch (CloneNotSupportedException e) {
            copy = new ChildTreeNode<>(getData(), catchedLoadFunction, catchedIsLeafFunction);
        }
        if (!isLeaf()) {
            if (copy.getChildren() == null)
                setChildren(new ArrayList<>());
            copy.getChildren().addAll(this.getChildren());
        }
        return copy;
    }
}
