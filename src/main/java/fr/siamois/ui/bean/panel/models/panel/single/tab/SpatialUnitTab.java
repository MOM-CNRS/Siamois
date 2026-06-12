package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.table.viewmodel.SpatialUnitTableViewModel;

public class SpatialUnitTab extends EntityListTab<SpatialUnitDTO> {

    public SpatialUnitTab(String titleCode, String icon, String id,
                          SpatialUnitTableViewModel tableViewModel, Integer count) {
        super(titleCode, icon, id, null, count, tableViewModel);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/spatialContextTab.xhtml";
    }
}
