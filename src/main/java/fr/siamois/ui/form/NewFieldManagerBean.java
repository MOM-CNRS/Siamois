package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.domain.services.form.CustomFieldMeasurementService;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerMeasurementViewModel;
import jdk.jfr.DataAmount;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Data
public class NewFieldManagerBean {

    private final CustomFieldMeasurementService customFieldMeasurementService;
    private boolean showEditor = false;
    private final CustomFormResponseViewModel formResponse;
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

// Prepare new field
        newField.setLabel(
                type.getOriginalPrefLabel()
                        + (nature != null ? " " + nature.getOriginalPrefLabel() : "")
        );
        newField.setSystemField(false);
        newField.setUnit(unit);
        newField.setConcept(type.concept());
        newField.setMeasurementNature(
                nature != null ? nature.concept() : null
        );

        // Save new field
        CustomFieldMeasurement created = customFieldMeasurementService.save(newField);
        // todo : catch error

        this.showEditor = false;

        // Create a new row and column
        CustomRowUiDto newRow = new CustomRowUiDto();
        CustomColUiDto newCol = new CustomColUiDto();

        // Set the field in the column
        newCol.setField(created);

        // Add the column to the row
        newRow.setColumns(List.of(newCol));

        // Add the row to the current panel's rows
        if (currentPanel.getRows() == null) {
            currentPanel.setRows(new ArrayList<>());
        }
        currentPanel.getRows().add(newRow);

        // Init form response
        formResponse.getAnswers().put(
                created,
                new CustomFieldAnswerMeasurementViewModel()
        );

        // clear
        cancelNewField();
    }

}