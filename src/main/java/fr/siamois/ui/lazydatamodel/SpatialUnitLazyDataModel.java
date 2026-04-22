package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpatialUnitLazyDataModel extends BaseSpatialUnitLazyDataModel {

    private final transient SpatialUnitService spatialUnitService;
    private final transient SessionSettingsBean sessionSettings;
    private final transient LangBean langBean;

    public SpatialUnitLazyDataModel(SpatialUnitService spatialUnitService, SessionSettingsBean sessionSettings, LangBean langBean) {
        this.spatialUnitService = spatialUnitService;
        this.sessionSettings = sessionSettings;
        this.langBean = langBean;
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return spatialUnitService.countSearchResults(sessionSettings.getSelectedInstitution(), filters);
    }


    @Override
    protected Page<SpatialUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return spatialUnitService.searchSpatialUnits(sessionSettings.getSelectedInstitution(), filter, pageable);
    }
}
