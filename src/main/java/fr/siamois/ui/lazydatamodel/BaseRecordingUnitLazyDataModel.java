package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.RowEditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public abstract class BaseRecordingUnitLazyDataModel extends BaseLazyDataModel<RecordingUnit> {

    protected final transient RecordingUnitService recordingUnitService;
    protected final transient LangBean langBean;

    private Concept bulkEditTypeValue;

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

    @Override
    protected Page<RecordingUnit> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return loadRecordingUnits(name, categoryIds, personIds, globalFilter, pageable);
    }

    protected abstract Page<RecordingUnit> loadRecordingUnits(
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
    public String getRowKey(RecordingUnit recordingUnit) {
        return recordingUnit != null ? Long.toString(recordingUnit.getId()) : null;
    }


    @Override
    public RecordingUnit getRowData(String rowKey) {
        List<RecordingUnit> units = getWrappedData();
        Long value = Long.valueOf(rowKey);

        for (RecordingUnit unit : units) {
            if (unit.getId().equals(value)) {
                return unit;
            }
        }

        return null;
    }

    public void handleRowEdit(RowEditEvent<RecordingUnit> event) {

        RecordingUnit toSave = event.getObject();

        try {
            recordingUnitService.save(toSave);
        }
        catch(FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", toSave.getFullIdentifier());
            return ;
        }

        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", toSave.getFullIdentifier());
    }

    public void saveFieldBulk() {
        List<Long> ids = getSelectedUnits().stream()
                .map(RecordingUnit::getId)
                .toList();
        int updateCount = recordingUnitService.bulkUpdateType(ids, bulkEditTypeValue);
        // Update in-memory list (for UI sync)
        for (RecordingUnit s : getSelectedUnits()) {
            s.setType(bulkEditTypeValue);
        }
        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", updateCount);
    }

    public void duplicateRow() {
        // Create a copy from selected row
        RecordingUnit original = getRowData();
        RecordingUnit newRec = new RecordingUnit(original);
        newRec.setIdentifier(recordingUnitService.generateNextIdentifier(newRec));

        // Save it
        newRec= recordingUnitService.save(newRec);

        // Add it to the model
        addRowToModel(newRec);
    }
}
