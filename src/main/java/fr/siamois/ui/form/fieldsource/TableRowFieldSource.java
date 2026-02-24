package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.*;
import fr.siamois.ui.form.CustomColUiDto;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import fr.siamois.ui.form.CustomRowUiDto;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.table.FormFieldColumn;
import fr.siamois.ui.table.TableColumn;
import fr.siamois.ui.table.TableDefinition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * FieldSource pour une ligne de tableau.
 *
 * Il se base sur :
 *  - la définition globale de la table (colonnes → CustomField)
 *  - optionnellement, un CustomForm spécifique à la ligne pour récupérer les EnabledWhenJson.
 */
public class TableRowFieldSource implements FieldSource {

    private final TableDefinition tableDefinition;

    /**
     * Map par id de field pour résolution rapide.
     */
    private final Map<Long, CustomField> byId = new HashMap<>();

    /**
     * Map des règles enabledWhen par field (optionnel).
     */
    private final Map<CustomField, EnabledWhenJson> enabledByField = new HashMap<>();

    public TableRowFieldSource(TableDefinition tableDefinition) {
        this(tableDefinition, null);
    }

    /**
     * @param tableDefinition définition globale des colonnes
     * @param rowSpecificForm CustomForm spécifique à la ligne (si tu veux récupérer les EnabledWhenJson depuis le form)
     */
    public TableRowFieldSource(TableDefinition tableDefinition, FormUiDto rowSpecificForm) {
        this.tableDefinition = tableDefinition != null ? tableDefinition : new TableDefinition();
        indexFromTableDefinition();
        if (rowSpecificForm != null) {
            indexEnabledWhenFromForm(rowSpecificForm);
        }
    }

    private void indexFromTableDefinition() {
        for (TableColumn col : tableDefinition.getColumns()) {
            if (col instanceof FormFieldColumn fCol) {
                CustomField f = fCol.getField();
                if (f != null && f.getId() != null) {
                    byId.put(f.getId(), f);
                }
            }
        }
    }


    private void indexEnabledWhenFromForm(FormUiDto form) {
        if (form.getLayout() == null) {
            return;
        }

        for (CustomFormPanelUiDto panel : form.getLayout()) {
            processPanel(panel);
        }
    }

    private void processPanel(CustomFormPanelUiDto panel) {
        if (panel.getRows() == null) {
            return;
        }

        for (CustomRowUiDto row : panel.getRows()) {
            processRow(row);
        }
    }

    private void processRow(CustomRowUiDto row) {
        if (row.getColumns() == null) {
            return;
        }

        for (CustomColUiDto column : row.getColumns()) {
            processColumn(column);
        }
    }

    private void processColumn(CustomColUiDto column) {
        CustomField field = column.getField();
        if (field == null || field.getId() == null || !byId.containsKey(field.getId())) {
            return;
        }

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
