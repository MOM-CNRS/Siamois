package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.*;
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
    public TableRowFieldSource(TableDefinition tableDefinition, CustomForm rowSpecificForm) {
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


    private void indexEnabledWhenFromForm(CustomForm form) {
        if (form.getLayout() == null) return;

        for (CustomFormPanel p : form.getLayout()) {
            if (p.getRows() == null) continue;
            for (CustomRow r : p.getRows()) {
                if (r.getColumns() == null) continue;
                for (CustomCol c : r.getColumns()) {
                    CustomField f = c.getField();
                    if (f == null || f.getId() == null) continue;
                    if (!byId.containsKey(f.getId())) {
                        // ce champ n'est pas une colonne de la table : on peut l'ignorer ou le rajouter si tu veux.
                        continue;
                    }
                    if (c.getEnabledWhenSpec() != null) {
                        enabledByField.put(f, c.getEnabledWhenSpec());
                    }
                }
            }
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
