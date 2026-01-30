package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.panel.models.panel.list.RecordingUnitListPanel;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
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
        Page<RecordingUnit> page =  loadRecordingUnits(name, categoryIds, personIds, globalFilter, pageable);
        page.forEach(unit -> {
            Hibernate.initialize(unit.getDocuments());
            Hibernate.initialize(unit.getRelationshipsAsUnit2());
            Hibernate.initialize(unit.getRelationshipsAsUnit1());
            Hibernate.initialize(unit.getParents());
            Hibernate.initialize(unit.getChildren());
        });
        return page;
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

        RecordingUnitListPanel.handleRuRowEdit(event, recordingUnitService, langBean);
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

        // Save it
        newRec = recordingUnitService.save(newRec, newRec.getType());

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