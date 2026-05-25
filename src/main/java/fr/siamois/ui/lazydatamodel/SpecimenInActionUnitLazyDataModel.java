package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpecimenInActionUnitLazyDataModel extends BaseSpecimenLazyDataModel {

    // locals
    @Getter
    private final transient ActionUnitDTO actionUnit;
    private final transient SessionSettingsBean sessionSettings;

    public SpecimenInActionUnitLazyDataModel(
            SpecimenService specimenService,
            LangBean langBean, ActionUnitDTO actionUnit, SessionSettingsBean sessionSettings) {
        super(specimenService, langBean);
        this.actionUnit = actionUnit;
        this.sessionSettings = sessionSettings;
    }



    @Override
    protected Page<SpecimenDTO> loadData(FilterDTO filter, Pageable pageable) {
        return specimenService.searchSpecimenInActionUnit(sessionSettings.getSelectedInstitution(),
                actionUnit,
                filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return specimenService.countSearchResultsInActionUnit(sessionSettings.getSelectedInstitution(),
                actionUnit,
                filters);
    }
}
