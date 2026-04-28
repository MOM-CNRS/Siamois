package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.panel.models.panel.list.RecordingUnitListPanel;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.RowEditEvent;
import org.primefaces.model.FilterMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public abstract class BaseRecordingUnitLazyDataModel extends BaseLazyDataModel<RecordingUnitDTO> {

    protected final transient RecordingUnitService recordingUnitService;
    protected final transient LangBean langBean;

    private ConceptDTO bulkEditTypeValue;

    private static final Map<String, String> FIELD_MAPPING;

    // Fields definition for cell/bulk edit
    CustomFieldSelectOneFromFieldCode typeField = new CustomFieldSelectOneFromFieldCode();

    BaseRecordingUnitLazyDataModel(RecordingUnitService recordingUnitService, LangBean langBean) {
        this.recordingUnitService = recordingUnitService;
        this.langBean = langBean;
        typeField.setFieldCode("SIARU.TYPE");
    }

    static {
        Map<String, String> map = new HashMap<>();
        map.put("category", "c_label");
        map.put("creationTime", "creation_time");
        map.put("author", "p_lastname");
        FIELD_MAPPING = Collections.unmodifiableMap(map); // Ensure immutability
    }

    @Deprecated
    protected abstract Page<RecordingUnitDTO> loadRecordingUnits(
            String nameFilter, Long[] categoryIds, Long[] personIds,
            String globalFilter, Pageable pageable);

    @Override
    protected String getDefaultSortField() {
        return "recording_unit_id";
    }

    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    public String getRowKey(RecordingUnitDTO recordingUnit) {
        return recordingUnit != null ? Long.toString(recordingUnit.getId()) : null;
    }


    @Override
    public RecordingUnitDTO getRowData(String rowKey) {
        List<RecordingUnitDTO> units = getWrappedData();
        Long value = Long.valueOf(rowKey);

        for (RecordingUnitDTO unit : units) {
            if (unit.getId().equals(value)) {
                return unit;
            }
        }

        return null;
    }

    public void handleRowEdit(RowEditEvent<RecordingUnitDTO> event) {

        RecordingUnitListPanel.handleRuRowEdit(event, recordingUnitService, langBean);
    }

    public void saveFieldBulk() {
        List<Long> ids = getSelectedUnits().stream()
                .map(RecordingUnitDTO::getId)
                .toList();
        int updateCount = recordingUnitService.bulkUpdateType(ids, bulkEditTypeValue);
        // Update in-memory list (for UI sync)
        for (RecordingUnitDTO s : getSelectedUnits()) {
            s.setType(bulkEditTypeValue);
        }
        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", updateCount);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        if (filterBy == null || filterBy.isEmpty()) {
            return;
        }

        FilterMeta meta = filterBy.get(RecordingUnitSpec.FULL_IDENTIFIER);
        if (meta != null && meta.getFilterValue() != null) {
            filterDTO.add(RecordingUnitSpec.FULL_IDENTIFIER, meta.getFilterValue().toString(), FilterDTO.FilterType.CONTAINS);
        }

        for (String entityFilter : new String[]{RecordingUnitSpec.AUTHOR_FILTER,
                RecordingUnitSpec.ACTION_UNIT_FILTER,
                RecordingUnitSpec.SPATIAL_UNIT_FILTER,
                RecordingUnitSpec.CONTRIBUTORS_FILTER,
                RecordingUnitSpec.TYPE_FILTER}) {
            FilterMeta entityMeta = filterBy.get(entityFilter);
            if (entityMeta != null && entityMeta.getFilterValue() instanceof List<?> ids && !ids.isEmpty()) {
                filterDTO.add(entityFilter, ids, FilterDTO.FilterType.CONTAINS);
            }
        }

        for (String dateFilter : new String[]{RecordingUnitSpec.OPENING_DATE_FILTER, RecordingUnitSpec.CLOSING_DATE_FILTER}) {
            FilterMeta dateMeta = filterBy.get(dateFilter);
            if (dateMeta != null && dateMeta.getFilterValue() instanceof List<?> range && !range.isEmpty()) {
                List<LocalDate> dates = (List<LocalDate>) range;
                filterDTO.add(dateFilter, dates, FilterDTO.FilterType.CONTAINS);
            }
        }
    }

    public void duplicateRow() {
        // Create a copy from selected row
        RecordingUnitDTO original = getRowData();
        RecordingUnitDTO newRec = new RecordingUnitDTO(original);

        // Save it
        newRec = recordingUnitService.save(newRec);

        newRec.setFullIdentifier(recordingUnitService.generateFullIdentifier(newRec.getActionUnit(), newRec));
        if (recordingUnitService.fullIdentifierAlreadyExistInAction(newRec)) {
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists");
            newRec.resetFullIdentifier();
        }

        newRec = recordingUnitService.save(newRec);

        // Add it to the model
        addRowToModel(newRec);
    }


}