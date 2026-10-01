package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Base commune aux implémentations de {@link FieldSource} : index des champs (par id) et de leurs
 * règles conditionnelles, et parcours panels -> rows -> cols d'un {@link FormUiDto}.
 */
abstract class AbstractFieldSource implements FieldSource {

    protected final Map<Long, CustomField> byId = new HashMap<>();
    private final Map<CustomField, FieldRules> rulesByField = new HashMap<>();

    /**
     * Parcourt panels -> rows -> cols d'un formulaire et appelle {@code onColumn} pour chaque
     * colonne ayant un champ renseigné. Ne fait rien si le formulaire ou son layout est null.
     */
    protected final void walkForm(FormUiDto form, BiConsumer<CustomField, CustomColUiDto> onColumn) {
        if (form == null || form.getLayout() == null) {
            return;
        }
        for (CustomFormPanelUiDto panel : form.getLayout()) {
            if (panel.getRows() == null) continue;
            processRows(onColumn, panel);
        }
    }

    private static void processRows(BiConsumer<CustomField, CustomColUiDto> onColumn, CustomFormPanelUiDto panel) {
        for (CustomRowUiDto row : panel.getRows()) {
            if (row.getColumns() == null) continue;
            processColumns(onColumn, row);
        }
    }

    private static void processColumns(BiConsumer<CustomField, CustomColUiDto> onColumn, CustomRowUiDto row) {
        for (CustomColUiDto column : row.getColumns()) {
            CustomField field = column.getField();
            if (field != null) {
                onColumn.accept(field, column);
            }
        }
    }

    /** Enregistre les règles de la colonne pour ce champ, si elle en déclare. */
    protected final void registerRules(CustomField field, CustomColUiDto column) {
        if (column.getRules() != null && !column.getRules().isEmpty()) {
            rulesByField.put(field, column.getRules());
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
    public FieldRules getRules(CustomField field) {
        return rulesByField.getOrDefault(field, FieldRules.NONE);
    }
}
