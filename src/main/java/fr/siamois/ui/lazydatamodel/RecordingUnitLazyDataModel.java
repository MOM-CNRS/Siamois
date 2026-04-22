package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;


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

    @Override
    protected void prepareSortDTO(@Nullable Map<String, SortMeta> sortBy, @NonNull SortDTO sortDTO) {
        if (sortBy != null && !sortBy.isEmpty()) {
            SortMeta meta = sortBy.get(RecordingUnitSpec.FULL_IDENTIFIER);
            if (meta != null) {
                sortDTO.add(RecordingUnitSpec.FULL_IDENTIFIER, meta.getOrder());
            }
        }
    }

    @Override
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        if (filterBy != null && !filterBy.isEmpty()) {
            FilterMeta meta = filterBy.get(RecordingUnitSpec.FULL_IDENTIFIER);
            if (meta != null && meta.getFilterValue() != null) {
                filterDTO.add(RecordingUnitSpec.FULL_IDENTIFIER, meta.getFilterValue().toString(), FilterDTO.FilterType.START_WITH);
            }
        }
    }
}
