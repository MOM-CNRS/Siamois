package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class RecordingUnitChildrenLazyDataModel extends BaseRecordingUnitLazyDataModel {

    @Getter
    private final transient RecordingUnit recordingUnit;

    public RecordingUnitChildrenLazyDataModel(RecordingUnitService recordingUnitService, LangBean langBean, RecordingUnit recordingUnit) {
        super(recordingUnitService, langBean);
        this.recordingUnit = recordingUnit;
    }

    @Override
    protected Page<RecordingUnit> loadRecordingUnits(String fullIdentifierFilter,
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




}
