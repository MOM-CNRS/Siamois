package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import org.primefaces.model.DefaultTreeNode;
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

    @Override
    public int getChildCount() {
        return totalEntityCount;
    }

    @Override
    public TreeNodeChildren<T> getChildren() {
        return childrens;
    }
}