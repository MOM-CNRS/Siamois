package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;

import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitInActionUnitLazyDataModel extends BaseRecordingUnitLazyDataModel {


    private final transient SessionSettingsBean sessionSettings;

    @Getter
    private final transient ActionUnit actionUnit;

    public RecordingUnitInActionUnitLazyDataModel(RecordingUnitService recordingUnitService,
                                                  SessionSettingsBean sessionSettings,
                                                  LangBean langBean, ActionUnit actionUnit) {
        super(recordingUnitService,langBean);
        this.sessionSettings = sessionSettings;
        this.actionUnit = actionUnit;
    }

    @Override
    protected Page<RecordingUnit> loadRecordingUnits(String fullIdentifierFilter,
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



}
