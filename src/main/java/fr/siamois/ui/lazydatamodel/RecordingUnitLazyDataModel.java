package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitLazyDataModel extends BaseRecordingUnitLazyDataModel {

    private final transient SessionSettingsBean sessionSettings;

    public RecordingUnitLazyDataModel(RecordingUnitService recordingUnitService, SessionSettingsBean sessionSettings, LangBean langBean) {
        super(recordingUnitService, langBean);
        this.sessionSettings = sessionSettings;

    }

    @Override
    protected Page<RecordingUnitDTO> loadRecordingUnits(String fullIdentifierFilter,
                                                        Long[] categoryIds,
                                                        Long[] personIds,
                                                        String globalFilter,
                                                        Pageable pageable) {
        return Page.empty();
    }

    @Override
    protected Page<RecordingUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return recordingUnitService.searchRecordingUnit(sessionSettings.getSelectedInstitution(), filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return recordingUnitService.countSearchResults(sessionSettings.getSelectedInstitution(), filters);
    }
}
