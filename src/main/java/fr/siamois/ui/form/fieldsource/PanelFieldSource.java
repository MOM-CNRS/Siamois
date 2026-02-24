package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.*;
import fr.siamois.ui.form.CustomColUiDto;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import fr.siamois.ui.form.CustomRowUiDto;
import fr.siamois.ui.form.FormUiDto;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PanelFieldSource implements FieldSource {

    private final Map<Long, CustomField> byId = new HashMap<>();
    private final Map<CustomField, EnabledWhenJson> enabledByField = new HashMap<>();

    public PanelFieldSource(FormUiDto detailsForm) {
        index(detailsForm);
    }

    private void index(FormUiDto form) {
        if (form == null || form.getLayout() == null) {
            return;
        }
        processPanels(form.getLayout());
    }

    private void processPanels(List<CustomFormPanelUiDto> panels) {
        for (CustomFormPanelUiDto panel : panels) {
            if (panel.getRows() != null) {
                processRows(panel.getRows());
            }
        }
    }

    private void processRows(List<CustomRowUiDto> rows) {
        for (CustomRowUiDto row : rows) {
            if (row.getColumns() != null) {
                processColumns(row.getColumns());
            }
        }
    }

    private void processColumns(List<CustomColUiDto> columns) {
        for (CustomColUiDto column : columns) {
            CustomField field = column.getField();
            if (field != null) {
                indexField(field, column);
            }
        }
    }

    private void indexField(CustomField field, CustomColUiDto column) {
        byId.put(field.getId(), field);
        if (column.getEnabledWhenSpec() != null) {
            enabledByField.put(field, column.getEnabledWhenSpec());
        }
    }


    @Override
    public Collection<CustomField> getAllFields() {
        return byId.values();
    }

    @Override
    public CustomField findFieldById(Long id) {
        return byId.get(id);
    }

    @Override
    public EnabledWhenJson getEnabledSpec(CustomField field) {
        return enabledByField.get(field);
    }
}
