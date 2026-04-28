package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import jdk.jfr.DataAmount;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Data
public class NewFieldManagerBean {
    private boolean showEditor = false;
    private final CustomFormResponseViewModel formResponse;
    private CustomFormPanelUiDto currentPanel;
    private CustomFieldMeasurement newField;

    public void prepareNewField(CustomFormPanelUiDto panel) {
        this.currentPanel = panel;
        this.showEditor = true;
        this.newField = new CustomFieldMeasurement();
        newField.setIsSystemField(false);
    }

    public void cancelNewField() {
        this.showEditor = false;
        this.currentPanel = null; // Clear the panel reference
    }

    public void saveNewField() {
        if (currentPanel == null) {
            throw new IllegalStateException("No panel selected. Call prepareNewField(panel) first.");
        }

        this.showEditor = false;

        // Create a new row and column
        CustomRowUiDto newRow = new CustomRowUiDto();
        CustomColUiDto newCol = new CustomColUiDto();

        newField.setLabel("Longueur");

        // Set the field in the column
        newCol.setField(newField);

        // Add the column to the row
        newRow.setColumns(List.of(newCol));

        // Add the row to the current panel's rows
        if (currentPanel.getRows() == null) {
            currentPanel.setRows(new ArrayList<>());
        }
        currentPanel.getRows().add(newRow);

        // clear
        cancelNewField();
    }

}