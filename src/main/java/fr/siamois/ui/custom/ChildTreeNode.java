package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.LazyDefaultTreeNode;
import org.primefaces.util.Callbacks;

import java.util.List;

public class ChildTreeNode<T extends AbstractEntityDTO> extends LazyDefaultTreeNode<T> {

    private final Callbacks.SerializableFunction<T, List<T>> catchedLoad;
    private final Callbacks.SerializableFunction<T, Boolean> catchedIsLeaf;

    public ChildTreeNode(T data, Callbacks.SerializableFunction<T, List<T>> catchedLoad, Callbacks.SerializableFunction<T, Boolean> catchedIsLeaf) {
        super(data, catchedLoad, catchedIsLeaf);
        this.catchedLoad = catchedLoad;
        this.catchedIsLeaf = catchedIsLeaf;
    }

    public ChildTreeNode(ChildTreeNode<T> other) {
        super(other.data, other.catchedLoad, other.catchedIsLeaf);
        this.catchedLoad = other.catchedLoad;
        this.catchedIsLeaf = other.catchedIsLeaf;
    }
}
