package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.primefaces.event.RowEditEvent;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;

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
        typeField.setFieldCode(Specimen.CAT_FIELD);
    }

    static {
        Map<String, String> map = new HashMap<>();
        map.put("category", "c_label");
        map.put("creationTime", "creation_time");
        map.put("author", "p_lastname");
        FIELD_MAPPING = Collections.unmodifiableMap(map); // Ensure immutability
    }


    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    protected void prepareFilterDTO(@Nullable Map<String, FilterMeta> filterBy, @NonNull FilterDTO filterDTO) {
        if (filterBy != null && !filterBy.isEmpty()) {
            FilterMeta fullIdentifierMeta = filterBy.get(SpecimenSpec.FULL_IDENTIFIER_FILTER);
            if (fullIdentifierMeta != null && fullIdentifierMeta.getFilterValue() != null) {
                filterDTO.add(SpecimenSpec.FULL_IDENTIFIER_FILTER,
                        fullIdentifierMeta.getFilterValue().toString(), FilterDTO.FilterType.CONTAINS);
            }
        }
    }

    @Override
    protected void prepareSortDTO(@Nullable Map<String, SortMeta> sortBy, @NonNull SortDTO sortDTO) {
        if (sortBy != null && !sortBy.isEmpty()) {
            SortMeta fullIdentifierSortMeta = sortBy.get(SpecimenSpec.FULL_IDENTIFIER_FILTER);
            if (fullIdentifierSortMeta != null) {
                sortDTO.add(SpecimenSpec.FULL_IDENTIFIER_FILTER, fullIdentifierSortMeta.getOrder());
            }
        }
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

    /** @return the newly created copy, so callers can highlight its row. */
    public SpecimenDTO duplicateRow() {
        // Create a copy from selected row
        SpecimenDTO original = getRowData();
        SpecimenDTO newRec = new SpecimenDTO(original);
        newRec.setIdentifier(specimenService.generateNextIdentifier(newRec));

        // Save it
        newRec = specimenService.save(newRec);

        // Add it to the model
        addRowToModel(newRec);
        return newRec;
    }

}
