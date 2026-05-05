package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitParentsLazyDataModel extends BaseRecordingUnitLazyDataModel {

    @Getter
    private final transient RecordingUnitDTO recordingUnit;

    public RecordingUnitParentsLazyDataModel(RecordingUnitService recordingUnitService, LangBean langBean, RecordingUnitDTO recordingUnit) {
        super(recordingUnitService, langBean);
        this.recordingUnit = recordingUnit;
    }

    @Override
    protected Page<RecordingUnitDTO> loadRecordingUnits(String fullIdentifierFilter,
                                                        Long[] categoryIds,
                                                        Long[] personIds,
                                                        String globalFilter,
                                                        Pageable pageable) {
        return recordingUnitService.findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                recordingUnit.getId(),
                fullIdentifierFilter, categoryIds, globalFilter,
                langBean.getLanguageCode(),
                pageable
        );
    }


    @Override
    protected Page<RecordingUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return recordingUnitService.findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
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
