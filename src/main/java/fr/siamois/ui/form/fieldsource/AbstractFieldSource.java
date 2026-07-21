package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Base commune aux implémentations de {@link FieldSource} : index champ/spec (par id, par
 * EnabledWhenJson, par DependsOnJson) et parcours panels -> rows -> cols d'un {@link FormUiDto}.
 */
abstract class AbstractFieldSource implements FieldSource {

    protected final Map<Long, CustomField> byId = new HashMap<>();
    private final Map<CustomField, EnabledWhenJson> enabledByField = new HashMap<>();
    private final Map<CustomField, DependsOnJson> dependsOnByField = new HashMap<>();

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
            for (CustomRowUiDto row : panel.getRows()) {
                if (row.getColumns() == null) continue;
                for (CustomColUiDto column : row.getColumns()) {
                    CustomField field = column.getField();
                    if (field != null) {
                        onColumn.accept(field, column);
                    }
                }
            }
        }
    }

    /** Enregistre l'EnabledWhenJson/DependsOnJson de la colonne pour ce champ, si présents. */
    protected final void registerSpecs(CustomField field, CustomColUiDto column) {
        if (column.getEnabledWhenSpec() != null) {
            enabledByField.put(field, column.getEnabledWhenSpec());
        }
        if (column.getDependsOnSpec() != null) {
            dependsOnByField.put(field, column.getDependsOnSpec());
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

    @Override
    public DependsOnJson getDependsOnSpec(CustomField field) {
        return dependsOnByField.get(field);
    }
}
