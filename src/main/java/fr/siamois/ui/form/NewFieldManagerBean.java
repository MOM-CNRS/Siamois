package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.services.form.CustomFieldMeasurementService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerMeasurementViewModel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@RequiredArgsConstructor
@Data
public class NewFieldManagerBean {

    private final CustomFieldMeasurementService customFieldMeasurementService;
    private final RecordingUnitService recordingUnitService;
    private final FormService formService;
    private final CustomFormResponseViewModel formResponse;
    private final AbstractEntityDTO owner; // entity whose form the fields are created from
    private final List<CustomFieldMeasurement> addFieldOptions; // options of existing fields when clicking the split button dropdown
    private final List<UnitDefinitionDTO> unitOptions; // units a new field can measure in

    private boolean showEditor = false;
    private CustomFormPanelUiDto currentPanel;
    private CustomFieldMeasurementDTO newField;
    private ConceptAutocompleteDTO type;
    private ConceptAutocompleteDTO nature;
    private Long unitId; // the unit picked in the editor, among unitOptions

    public void prepareNewField(CustomFormPanelUiDto panel) {
        this.currentPanel = panel;
        this.showEditor = true;
        this.newField = new CustomFieldMeasurementDTO();
        this.unitId = defaultUnitId();
        newField.setSystemField(false);
    }

    private Long defaultUnitId() {
        return unitOptions.stream()
                .filter(UnitDefinitionDTO::isSystemBase)
                .findFirst()
                .or(() -> unitOptions.stream().findFirst())
                .map(UnitDefinitionDTO::getId)
                .orElse(null);
    }

    public void cancelNewField() {
        this.showEditor = false;
        this.currentPanel = null; // Clear the panel reference
    }

    public void saveNewField() {
        if (currentPanel == null) {
            throw new IllegalStateException("No panel selected. Call prepareNewField(panel) first.");
        }

        // 1. Prepare and persist the new field definition
        newField.setLabel(type.getOriginalPrefLabel() + (nature != null ? " " + nature.getOriginalPrefLabel() : ""));
        newField.setSystemField(false);
        newField.setUnit(selectedUnit());
        newField.setConcept(type.concept());
        newField.setMeasurementNature(nature != null ? nature.concept() : null);

        CustomFieldMeasurement created = customFieldMeasurementService.save(newField);

        // 2. Keep the field attached to the unit that created it, and offer it right away among the
        // existing measurement fields of the split button dropdown
        linkToOwner(created);
        if (!addFieldOptions.contains(created)) {
            addFieldOptions.add(created);
        }

        // 3. Delegate to the common UI update logic
        attachFieldToPanel(currentPanel, created);

        // 4. Cleanup
        cancelNewField();
    }

    private UnitDefinitionDTO selectedUnit() {
        if (unitId == null) return null;
        return unitOptions.stream()
                .filter(option -> unitId.equals(option.getId()))
                .findFirst()
                .orElse(null);
    }

    private void linkToOwner(CustomFieldMeasurement created) {
        if (owner instanceof RecordingUnitDTO recordingUnit && recordingUnit.getId() != null) {
            recordingUnitService.addMeasurementField(recordingUnit.getId(), created);
        }
    }

    public void addFieldFromMeasurement(CustomFormPanelUiDto panel, CustomFieldMeasurement field) {
        attachFieldToPanel(panel, field);
    }

    private void attachFieldToPanel(CustomFormPanelUiDto panel, CustomFieldMeasurement field) {
        if (panel.getRows() == null) {
            panel.setRows(new ArrayList<>());
        }

        CustomColUiDto newCol = new CustomColUiDto();
        newCol.setCanBeRemoved(true);
        newCol.setField(field);
        newCol.setClassName("ui-g-12 ui-md-6 ui-lg-6"); // Standard sizing

        if (panel.getRows().isEmpty()) {
            // Scenario A: First field ever
            CustomRowUiDto newRow = new CustomRowUiDto();
            newRow.setColumns(new ArrayList<>(List.of(newCol)));
            panel.getRows().add(newRow);
        } else {
            // Scenario B: Add to the existing last row
            CustomRowUiDto lastRow = panel.getRows().get(panel.getRows().size() - 1);

            // Ensure the columns list is mutable
            if (lastRow.getColumns() == null) {
                lastRow.setColumns(new ArrayList<>());
            }
            lastRow.getColumns().add(newCol);
        }

        CustomFieldAnswerMeasurementViewModel answer = new CustomFieldAnswerMeasurementViewModel();
        formService.initializeMeasurement(answer, field);
        formResponse.getAnswers().putIfAbsent(field, answer);
    }

    public void removeField(CustomFormPanelUiDto panel, CustomColUiDto colToRemove) {
        if (panel == null || panel.getRows() == null || colToRemove == null) {
            return;
        }

        Iterator<CustomRowUiDto> rowIterator = panel.getRows().iterator();
        while (rowIterator.hasNext()) {
            if (processRowRemoval(rowIterator, colToRemove)) {
                break; // Exit once the specific column is handled
            }
        }
    }

    private boolean processRowRemoval(Iterator<CustomRowUiDto> rowIterator, CustomColUiDto colToRemove) {
        CustomRowUiDto row = rowIterator.next();

        if (row.getColumns() == null || !row.getColumns().removeIf(c -> c.equals(colToRemove))) {
            return false;
        }

        // Clean up data source
        if (formResponse != null && formResponse.getAnswers() != null) {
            formResponse.getAnswers().remove(colToRemove.getField());
        }

        // Row cleanup
        if (row.getColumns().isEmpty()) {
            rowIterator.remove();
        }

        return true;
    }


}