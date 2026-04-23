package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitInActionUnitLazyDataModel extends BaseRecordingUnitLazyDataModel {


    private final transient SessionSettingsBean sessionSettings;

    @Getter
    private final transient ActionUnitDTO actionUnit;

    public RecordingUnitInActionUnitLazyDataModel(RecordingUnitService recordingUnitService,
                                                  SessionSettingsBean sessionSettings,
                                                  LangBean langBean, ActionUnitDTO actionUnit) {
        super(recordingUnitService,langBean);
        this.sessionSettings = sessionSettings;
        this.actionUnit = actionUnit;
    }

    @Deprecated
    @Override
    protected Page<RecordingUnitDTO> loadRecordingUnits(String fullIdentifierFilter,
                                                        Long[] categoryIds,
                                                        Long[] personIds,
                                                        String globalFilter,
                                                        Pageable pageable) {
        return recordingUnitService.findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                sessionSettings.getSelectedInstitution().getId(),
                actionUnit.getId(),
                fullIdentifierFilter, categoryIds, globalFilter,
                langBean.getLanguageCode(),
                pageable
        );
    }


    @Override
    protected Page<RecordingUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return recordingUnitService.searchRecordingUnitInActionUnit(sessionSettings.getSelectedInstitution(),  actionUnit, filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return recordingUnitService.countSearchResultsInActionUnit(sessionSettings.getSelectedInstitution(), actionUnit, filters);
    }
}
