package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.LangBean;
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
public abstract class BaseSpecimenLazyDataModel extends BaseLazyDataModel<Specimen> {

    // deps
    protected final transient SpecimenService specimenService;
    protected final transient LangBean langBean;

    private static final Map<String, String> FIELD_MAPPING;
    private Concept bulkEditTypeValue;

    // Fields definition for cell/bulk edit
    CustomFieldSelectOneFromFieldCode typeField = new CustomFieldSelectOneFromFieldCode();

    BaseSpecimenLazyDataModel(SpecimenService specimenService, LangBean langBean) {
        this.specimenService = specimenService;
        this.langBean = langBean;
        typeField.setFieldCode("SIAS.CATEGORY");
    }

    static {
        Map<String, String> map = new HashMap<>();
        map.put("category", "c_label");
        map.put("creationTime", "creation_time");
        map.put("author", "p_lastname");
        FIELD_MAPPING = Collections.unmodifiableMap(map); // Ensure immutability
    }

    @Override
    protected Page<Specimen> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        Page<Specimen> page =  loadSpecimens(name, categoryIds, personIds, globalFilter, pageable);
        page.forEach(specimen -> {
            Hibernate.initialize(specimen.getDocuments());
            Hibernate.initialize(specimen.getAuthors());
            Hibernate.initialize(specimen.getCollectors());
            Hibernate.initialize(specimen.getRecordingUnit());
        });
        return page;
    }

    protected abstract Page<Specimen> loadSpecimens(
            String nameFilter, Long[] categoryIds, Long[] personIds,
            String globalFilter, Pageable pageable);

    @Override
    protected String getDefaultSortField() {
        return "specimen_id";
    }

    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    public String getRowKey(Specimen specimen) {
        return specimen != null ? Long.toString(specimen.getId()) : null;
    }


    @Override
    public Specimen getRowData(String rowKey) {
        List<Specimen> units = getWrappedData();
        Long value = Long.valueOf(rowKey);

        for (Specimen unit : units) {
            if (unit.getId().equals(value)) {
                return unit;
            }
        }

        return null;
    }

    public void handleRowEdit(RowEditEvent<Specimen> event) {

        Specimen toSave = event.getObject();

        try {
            specimenService.save(toSave);
        }
        catch(FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", toSave.getFullIdentifier());
            return ;
        }

        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", toSave.getFullIdentifier());
    }

    public void saveFieldBulk() {
        List<Long> ids = getSelectedUnits().stream()
                .map(Specimen::getId)
                .toList();
        int updateCount = specimenService.bulkUpdateType(ids, bulkEditTypeValue);
        // Update in-memory list (for UI sync)
        for (Specimen s : getSelectedUnits()) {
            s.setType(bulkEditTypeValue);
        }
        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", updateCount);
    }

    public void duplicateRow() {
        // Create a copy from selected row
        Specimen original = getRowData();
        Specimen newRec = new Specimen(original);
        newRec.setIdentifier(specimenService.generateNextIdentifier(newRec));

        // Save it
        newRec = specimenService.save(newRec);

        // Add it to the model
        addRowToModel(newRec);
    }

}
