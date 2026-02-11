package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.ui.table.EntityTableViewModel;
import lombok.Getter;

public class MultiHierarchyTab extends PanelTab {


    @Getter
    private final EntityTableViewModel<?,?> childTableModel;

    public MultiHierarchyTab(String titleCode, String icon, String id,
                             EntityTableViewModel<?,?> childTableModel) {
        super(titleCode, icon, id);
        this.childTableModel = childTableModel;
    }

    @Override
    public String getViewName() {
        return "/panel/tab/hierarchyTab.xhtml";
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
