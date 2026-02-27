package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.EntityTableViewModel;
import lombok.Getter;
import lombok.Setter;

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

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
