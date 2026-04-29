package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.domain.services.form.CustomFieldMeasurementService;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
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
    private final CustomFormResponseViewModel formResponse;
    private final List<CustomFieldMeasurement> addFieldOptions; // options of existing fields when clicking the split button dropdown

    private boolean showEditor = false;
    private CustomFormPanelUiDto currentPanel;
    private CustomFieldMeasurementDTO newField;
    private ConceptAutocompleteDTO type;
    private ConceptAutocompleteDTO nature;
    private UnitDefinitionDTO unit;

    public void prepareNewField(CustomFormPanelUiDto panel) {
        this.currentPanel = panel;
        this.showEditor = true;
        this.newField = new CustomFieldMeasurementDTO();
        newField.setSystemField(false);
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
        newField.setUnit(unit);
        newField.setConcept(type.concept());
        newField.setMeasurementNature(nature != null ? nature.concept() : null);

        CustomFieldMeasurement created = customFieldMeasurementService.save(newField);

        // 2. Delegate to the common UI update logic
        attachFieldToPanel(currentPanel, created);

        // 3. Cleanup
        cancelNewField();
    }

    public void addFieldFromMeasurement(CustomFormPanelUiDto panel, CustomFieldMeasurement field) {
        attachFieldToPanel(panel, field);
    }

    /**
     * Common logic to inject a field into the UI tree.
     * Logic: If a row exists, add to last row. Otherwise, create a new row.
     */
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

        // 4. Initialize the placeholder in the answer map
        // Use putIfAbsent to prevent overwriting if the user clicks the same library item twice
        CustomFieldAnswerMeasurementViewModel answer = new CustomFieldAnswerMeasurementViewModel();
        answer.setValue(new MeasurementAnswerDTO());
        formResponse.getAnswers().putIfAbsent(field, answer);
    }

    public void removeField(CustomFormPanelUiDto panel, CustomColUiDto colToRemove) {
        if (panel == null || panel.getRows() == null || colToRemove == null) {
            return;
        }

        // Use an Iterator to safely remove the row if it becomes empty
        Iterator<CustomRowUiDto> rowIterator = panel.getRows().iterator();

        while (rowIterator.hasNext()) {
            CustomRowUiDto row = rowIterator.next();

            if (row.getColumns() != null) {
                // Find and remove the column
                boolean removed = row.getColumns().removeIf(c -> c.equals(colToRemove));

                if (removed) {
                    // 1. Clean up the answer map to prevent data persistence/leaks
                    if (formResponse != null && formResponse.getAnswers() != null) {
                        formResponse.getAnswers().remove(colToRemove.getField());
                    }

                    // 2. If the row has no more columns, delete the row entirely
                    if (row.getColumns().isEmpty()) {
                        rowIterator.remove();
                    }

                    // Exit loop once found and handled
                    break;
                }
            }
        }
    }


}