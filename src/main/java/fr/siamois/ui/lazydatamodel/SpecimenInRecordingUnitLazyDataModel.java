package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class SpecimenInRecordingUnitLazyDataModel extends BaseSpecimenLazyDataModel {

    private final transient SessionSettingsBean sessionSettings;

    // locals
    @Getter
    private final transient RecordingUnitDTO recordingUnit;

    public SpecimenInRecordingUnitLazyDataModel(
            SpecimenService specimenService,
            SessionSettingsBean sessionSettings,
            LangBean langBean, RecordingUnitDTO recordingUnit) {
        super(specimenService, langBean);
        this.sessionSettings = sessionSettings;
        this.recordingUnit = recordingUnit;
    }



    @Override
    protected Page<SpecimenDTO> loadData(FilterDTO filter, Pageable pageable) {
        return specimenService.searchSpecimenInRecordingUnit(sessionSettings.getSelectedInstitution(),
                recordingUnit,
                filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return specimenService.countSearchResultsInRecordingUnit(sessionSettings.getSelectedInstitution(),
                recordingUnit,
                filters);
    }
}
