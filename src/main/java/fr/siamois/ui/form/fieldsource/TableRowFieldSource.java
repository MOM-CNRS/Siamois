package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumn;

/**
 * FieldSource pour une ligne de tableau.
 *
 * Il se base sur :
 *  - la définition globale de la table (colonnes → CustomField)
 *  - optionnellement, un CustomForm spécifique à la ligne pour récupérer les EnabledWhenJson/DependsOnJson.
 */
public class TableRowFieldSource extends AbstractFieldSource {

    private final TableDefinition tableDefinition;

    public TableRowFieldSource(TableDefinition tableDefinition) {
        this(tableDefinition, null);
    }

    /**
     * @param tableDefinition définition globale des colonnes
     * @param rowSpecificForm CustomForm spécifique à la ligne (si tu veux récupérer les EnabledWhenJson/DependsOnJson depuis le form)
     */
    public TableRowFieldSource(TableDefinition tableDefinition, FormUiDto rowSpecificForm) {
        this.tableDefinition = tableDefinition != null ? tableDefinition : new TableDefinition();
        indexFromTableDefinition();
        if (rowSpecificForm != null) {
            walkForm(rowSpecificForm, this::indexSpecsForKnownField);
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

    private void indexSpecsForKnownField(CustomField field, CustomColUiDto column) {
        if (field.getId() == null || !byId.containsKey(field.getId())) {
            return;
        }
        registerSpecs(field, column);
    }
}
