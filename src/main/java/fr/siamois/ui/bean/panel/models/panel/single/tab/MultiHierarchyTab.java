package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.ui.table.EntityTableViewModel;
import lombok.Getter;

public class MultiHierarchyTab extends PanelTab {

    // lazy model for children
    @Getter
    private final EntityTableViewModel<?,?> parentTableModel;
    // lazy model for parents
    @Getter
    private final EntityTableViewModel<?,?> childTableModel;

    public MultiHierarchyTab(String titleCode, String icon, String id,
                             EntityTableViewModel<?,?> parentTableModel, EntityTableViewModel<?,?> childTableModel) {
        super(titleCode, icon, id);
        this.parentTableModel = parentTableModel;
        this.childTableModel = childTableModel;
    }

    @Override
    public String getViewName() {
        return "/panel/tab/hierarchyTab.xhtml";
    }




}
