package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public class SpatialUnitChildrenLazyDataModel extends BaseSpatialUnitLazyDataModel {

    private final transient SpatialUnitService spatialUnitService;
    private final transient LangBean langBean;
    @Getter
    private final transient SpatialUnitDTO spatialUnit;
    private final transient SessionSettingsBean sessionSettings;


    public SpatialUnitChildrenLazyDataModel(SpatialUnitService spatialUnitService
            , LangBean langBean
            , SpatialUnitDTO spatialUnit, SessionSettingsBean sessionSettings) {
        this.spatialUnitService = spatialUnitService;
        this.langBean = langBean;
        this.spatialUnit = spatialUnit;
        this.sessionSettings = sessionSettings;
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return spatialUnitService.countSearchResultsInSpatialUnit(sessionSettings.getSelectedInstitution(),
                spatialUnit,
                filters);
    }


    @Override
    protected Page<SpatialUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return spatialUnitService.searchSpatialUnitsInSpatialUnit(sessionSettings.getSelectedInstitution(),
                spatialUnit,
                filter,
                pageable);
    }
}
