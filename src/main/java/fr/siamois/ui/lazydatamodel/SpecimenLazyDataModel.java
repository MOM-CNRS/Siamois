package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpecimenLazyDataModel extends BaseSpecimenLazyDataModel {

    private final transient SessionSettingsBean sessionSettings;

    public SpecimenLazyDataModel(
                                 SpecimenService specimenService,
                                 SessionSettingsBean sessionSettings,
                                 LangBean langBean) {
        super(specimenService, langBean);
        this.sessionSettings = sessionSettings;
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
        return specimenService.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                sessionSettings.getSelectedInstitution().getId(),
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
