package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public class ActionUnitSpecimenLazyDataModel extends BaseSpecimenLazyDataModel {

    private final transient SessionSettingsBean sessionSettings;
    private final transient ActionUnitDTO actionUnit;

    public ActionUnitSpecimenLazyDataModel(SpecimenService specimenService,
                                           SessionSettingsBean sessionSettings,
                                           LangBean langBean,
                                           ActionUnitDTO actionUnit) {
        super(specimenService, langBean);
        this.sessionSettings = sessionSettings;
        this.actionUnit = actionUnit;
    }

    @Override
    protected Page<SpecimenDTO> loadData(FilterDTO filter, Pageable pageable) {
        return specimenService.searchSpecimenInActionUnit(sessionSettings.getSelectedInstitution(), actionUnit, filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return specimenService.countSearchResultsInActionUnit(sessionSettings.getSelectedInstitution(), actionUnit, filters);
    }
}
