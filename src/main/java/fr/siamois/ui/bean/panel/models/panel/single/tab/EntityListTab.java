package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.RowAjaxUpdateResolver;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public abstract class EntityListTab<T extends AbstractEntityDTO> extends PanelTab {

    private final BaseLazyDataModel<T> lazyDataModel ;
    @Setter
    private Integer totalCount;
    private final EntityTableViewModel<T, Long> tableModel;

    protected EntityListTab(String titleCode, String icon, String id,
                            BaseLazyDataModel<T> lazyDataModel,
                            Integer totalCount, EntityTableViewModel<T, Long> tableModel) {
        super(titleCode, icon, id);
        this.lazyDataModel = lazyDataModel;
        this.totalCount = totalCount;
        this.tableModel = tableModel;
    }

    protected EntityListTab(String titleCode, String icon, String id,
                            BaseLazyDataModel<T> lazyDataModel,
                            Integer totalCount) {
        super(titleCode, icon, id);
        this.lazyDataModel = lazyDataModel;
        this.totalCount = totalCount;
        this.tableModel = null;
    }

    /**
     * Row-scoped AJAX update targets (one per cell) for this tab's table, or an empty list if
     * the entity isn't on the currently displayed page. Never falls back to updating the whole
     * tab table — if there's no row to target, there's nothing to refresh.
     */
    public List<String> getRowUpdateTargets(String panelIndex, Long entityId) {
        if (tableModel == null || entityId == null) return List.of();
        int rowIndex = tableModel.getRowIndexInCurrentPage(entityId);
        if (rowIndex < 0) return List.of();
        // UIData/LazyDataModel address rows by their absolute index across the whole dataset
        // (rowIndex - first), not by their index within the current page.
        int absoluteRowIndex = tableModel.getLazyDataModel().getFirst() + rowIndex;
        return RowAjaxUpdateResolver.resolveRowChildIds(getTableClientId(panelIndex), absoluteRowIndex);
    }

    private String getTableClientId(String panelIndex) {
        String prefix = "singlePanelUnitForm-" + panelIndex + ":singlePanelUnitTabs:" + getId();
        return tableModel != null && tableModel.isTreeMode() ? prefix + ":entityTreeTable" : prefix + ":entityDatatable";
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
