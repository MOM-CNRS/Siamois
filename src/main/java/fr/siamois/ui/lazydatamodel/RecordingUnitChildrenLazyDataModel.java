package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitChildrenLazyDataModel extends BaseRecordingUnitLazyDataModel {

    @Getter
    private final transient RecordingUnitDTO recordingUnit;

    private final transient SessionSettingsBean sessionSettings;

    public RecordingUnitChildrenLazyDataModel(RecordingUnitService recordingUnitService, LangBean langBean,
                                              RecordingUnitDTO recordingUnit, SessionSettingsBean sessionSettings) {
        super(recordingUnitService, langBean);
        this.recordingUnit = recordingUnit;
        this.sessionSettings = sessionSettings;
    }

    @Override
    protected Page<RecordingUnitDTO> loadRecordingUnits(String fullIdentifierFilter,
                                                        Long[] categoryIds,
                                                        Long[] personIds,
                                                        String globalFilter,
                                                        Pageable pageable) {
        return recordingUnitService.findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                recordingUnit.getId(),
                fullIdentifierFilter, categoryIds, globalFilter,
                langBean.getLanguageCode(),
                pageable

        );
    }


    @Override
    protected Page<RecordingUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return recordingUnitService.searchRecordingUnitInRecordingUnit(sessionSettings.getSelectedInstitution(),
                recordingUnit,
                filter,
                pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return recordingUnitService.countSearchResultsInRecordingUnit(sessionSettings.getSelectedInstitution(),
                recordingUnit,
                filters
                );
    }
}
