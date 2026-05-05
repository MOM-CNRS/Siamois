package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpecimenInActionUnitLazyDataModel extends BaseSpecimenLazyDataModel {

    // locals
    @Getter
    private final transient ActionUnitDTO actionUnit;

    public SpecimenInActionUnitLazyDataModel(
            SpecimenService specimenService,
            LangBean langBean, ActionUnitDTO actionUnit) {
        super(specimenService, langBean);
        this.actionUnit = actionUnit;
    }

    @Override
    protected Page<SpecimenDTO> loadSpecimens(String fullIdentifierFilter,
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


    @Override
    protected Page<SpecimenDTO> loadData(FilterDTO filter, Pageable pageable) {
        return specimenService.findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                actionUnit.getId(),
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
