package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitInSpatialUnitLazyDataModel extends BaseRecordingUnitLazyDataModel {

    @Getter
    private final transient SpatialUnitDTO spatialUnit;

    public RecordingUnitInSpatialUnitLazyDataModel(RecordingUnitService recordingUnitService,
                                                   LangBean langBean, SpatialUnitDTO spatialUnit) {
        super(recordingUnitService,langBean);
        this.spatialUnit = spatialUnit;
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
        return recordingUnitService.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit.getId(),
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
