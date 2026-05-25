package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitInSpatialUnitLazyDataModel extends BaseRecordingUnitLazyDataModel {

    @Getter
    private final transient SpatialUnitDTO spatialUnit;
    private final transient SessionSettingsBean sessionSettings;

    public RecordingUnitInSpatialUnitLazyDataModel(RecordingUnitService recordingUnitService,
                                                   LangBean langBean, SpatialUnitDTO spatialUnit, SessionSettingsBean sessionSettings) {
        super(recordingUnitService,langBean);
        this.spatialUnit = spatialUnit;
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
        return recordingUnitService.searchRecordingUnitInSpatialUnit(sessionSettings.getSelectedInstitution(),
                spatialUnit,
                filter,
                pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return recordingUnitService.countSearchResultsInSpatialUnit(sessionSettings.getSelectedInstitution(),
                spatialUnit,
                filters
        );
    }
}
