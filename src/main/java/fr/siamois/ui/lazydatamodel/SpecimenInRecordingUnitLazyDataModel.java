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
    protected Page<SpecimenDTO> loadSpecimens(String fullIdentifierFilter,
                                              Long[] categoryIds,
                                              Long[] personIds,
                                              String globalFilter,
                                              Pageable pageable) {
        return specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                sessionSettings.getSelectedInstitution().getId(),
                recordingUnit.getId(),
                fullIdentifierFilter, categoryIds, globalFilter,
                langBean.getLanguageCode(),
                pageable
        );
    }


    @Override
    protected Page<SpecimenDTO> loadData(FilterDTO filter, Pageable pageable) {
        return specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                sessionSettings.getSelectedInstitution().getId(),
                recordingUnit.getId(),
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
