package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class SpatialContextLazyDataModel extends BaseSpatialUnitLazyDataModel {

    private final transient SpatialUnitService spatialUnitService;
    private final transient SessionSettingsBean sessionSettings;
    private final List<Long> spatialContextIds;

    public SpatialContextLazyDataModel(SpatialUnitService spatialUnitService,
                                       SessionSettingsBean sessionSettings,
                                       List<Long> spatialContextIds) {
        this.spatialUnitService = spatialUnitService;
        this.sessionSettings = sessionSettings;
        this.spatialContextIds = spatialContextIds;
    }

    @Override
    protected Page<SpatialUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        if (spatialContextIds.isEmpty()) return Page.empty();
        return spatialUnitService.searchSpatialUnitsByIds(sessionSettings.getSelectedInstitution(), spatialContextIds, filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        if (spatialContextIds.isEmpty()) return 0;
        return spatialUnitService.countSearchResultsByIds(sessionSettings.getSelectedInstitution(), spatialContextIds, filters);
    }
}
