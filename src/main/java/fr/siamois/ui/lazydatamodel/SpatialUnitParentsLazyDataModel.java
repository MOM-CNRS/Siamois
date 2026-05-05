package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public class SpatialUnitParentsLazyDataModel extends BaseSpatialUnitLazyDataModel {

    private final transient SpatialUnitService spatialUnitService;
    private final transient LangBean langBean;

    @Getter
    private final transient SpatialUnitDTO spatialUnit;

    public SpatialUnitParentsLazyDataModel(SpatialUnitService spatialUnitService
            , LangBean langBean
            , SpatialUnitDTO spatialUnit) {
        this.spatialUnitService = spatialUnitService;
        this.langBean = langBean;

        this.spatialUnit = spatialUnit;
    }

    @Override
    protected Page<SpatialUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return spatialUnitService.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit,
                nameFilter, null, null, null,
                langBean.getLanguageCode(),
                pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return 0;
    }
}
