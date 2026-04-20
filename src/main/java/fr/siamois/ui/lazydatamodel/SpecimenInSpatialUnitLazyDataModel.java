package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpecimenInSpatialUnitLazyDataModel extends BaseSpecimenLazyDataModel {

    // locals
    @Getter
    private final transient SpatialUnitDTO spatialUnit;

    public SpecimenInSpatialUnitLazyDataModel(
            SpecimenService specimenService,
            LangBean langBean, SpatialUnitDTO spatialUnit) {
        super(specimenService, langBean);
        this.spatialUnit = spatialUnit;
    }

    @Override
    protected Page<SpecimenDTO> loadSpecimens(String fullIdentifierFilter,
                                              Long[] categoryIds,
                                              Long[] personIds,
                                              String globalFilter,
                                              Pageable pageable) {
        return Page.empty();
    }


    @Override
    protected Page<SpecimenDTO> loadData(FilterDTO filter, Pageable pageable) {
        return specimenService.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit.getId(),
                null, null, null,
                langBean.getLanguageCode(),
                pageable
        );
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return 0;
    }
}
