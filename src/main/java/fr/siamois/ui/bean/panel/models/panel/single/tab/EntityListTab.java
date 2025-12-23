package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.EntityTableViewModel;
import lombok.Data;

@Data
public abstract class EntityListTab<T extends TraceableEntity> extends PanelTab {


    private BaseLazyDataModel<T> lazyDataModel ;
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

}
