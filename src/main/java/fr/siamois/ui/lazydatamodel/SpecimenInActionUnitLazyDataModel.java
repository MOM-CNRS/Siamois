package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpecimenInActionUnitLazyDataModel extends BaseSpecimenLazyDataModel {



    // locals
    @Getter
    private final transient ActionUnit actionUnit;

    public SpecimenInActionUnitLazyDataModel(
            SpecimenService specimenService,
            LangBean langBean, ActionUnit actionUnit) {
        super(specimenService, langBean);
        this.actionUnit = actionUnit;
    }

    @Override
    protected Page<Specimen> loadSpecimens(String fullIdentifierFilter,
                                           Long[] categoryIds,
                                           Long[] personIds,
                                           String globalFilter,
                                           Pageable pageable) {
        return specimenService.findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                actionUnit.getId(),
                fullIdentifierFilter, categoryIds, globalFilter,
                langBean.getLanguageCode(),
                pageable
        );
    }



}
