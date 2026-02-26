package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpecimenDTO;
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
public abstract class BaseSpecimenLazyDataModel extends BaseLazyDataModel<SpecimenDTO> {

    // deps
    protected final transient SpecimenService specimenService;
    protected final transient LangBean langBean;

    private static final Map<String, String> FIELD_MAPPING;
    private ConceptDTO bulkEditTypeValue;

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
    protected Page<SpecimenDTO> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return loadSpecimens(name, categoryIds, personIds, globalFilter, pageable);
    }

    protected abstract Page<SpecimenDTO> loadSpecimens(
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
    public String getRowKey(SpecimenDTO specimen) {
        return specimen != null ? Long.toString(specimen.getId()) : null;
    }


    @Override
    public SpecimenDTO getRowData(String rowKey) {
        List<SpecimenDTO> units = getWrappedData();
        Long value = Long.valueOf(rowKey);

        for (SpecimenDTO unit : units) {
            if (unit.getId().equals(value)) {
                return unit;
            }
        }

        return null;
    }

    public void handleRowEdit(RowEditEvent<SpecimenDTO> event) {

        SpecimenDTO toSave = event.getObject();

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
                .map(SpecimenDTO::getId)
                .toList();
        int updateCount = specimenService.bulkUpdateType(ids, bulkEditTypeValue);
        // Update in-memory list (for UI sync)
        for (SpecimenDTO s : getSelectedUnits()) {
            s.setType(bulkEditTypeValue);
        }
        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", updateCount);
    }

    public void duplicateRow() {
        // Create a copy from selected row
        SpecimenDTO original = getRowData();
        SpecimenDTO newRec = new SpecimenDTO(original);
        newRec.setIdentifier(specimenService.generateNextIdentifier(newRec));

        // Save it
        newRec = specimenService.save(newRec);

        // Add it to the model
        addRowToModel(newRec);
    }

}
