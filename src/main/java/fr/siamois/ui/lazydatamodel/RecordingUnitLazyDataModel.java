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
            for (String column : RecordingUnitSpec.allColumns()) {
                SortMeta meta = sortBy.get(column);
                if (meta != null) {
                    processColumn(sortDTO, column, meta);
                }
            }
        }
    }

    /**
     * Sort a relation should sort an attribute and not by ID
     * @param sortDTO The SortDTO information
     * @param column The attribute name
     * @param meta The SortMeta provided by Primefaces
     */
    private static void processColumn(@NonNull SortDTO sortDTO, @NonNull String column, SortMeta meta) {
        String columnToSort = switch (column) {
            case RecordingUnitSpec.SPATIAL_UNIT_FILTER ->  RecordingUnitSpec.SPATIAL_UNIT_FILTER + ".name";
            case RecordingUnitSpec.AUTHOR_FILTER ->   RecordingUnitSpec.AUTHOR_FILTER + ".email";
            default -> column;
        };
        sortDTO.add(columnToSort, meta.getOrder());
    }

}
