package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PanelFieldSource implements FieldSource {

    private final CustomForm overviewForm;
    private final CustomForm detailsForm;

    private final Map<Long, CustomField> byId = new HashMap<>();
    private final Map<CustomField, EnabledWhenJson> enabledByField = new HashMap<>();

    public PanelFieldSource(CustomForm overviewForm, CustomForm detailsForm) {
        this.overviewForm = overviewForm;
        this.detailsForm = detailsForm;
        index(overviewForm);
        index(detailsForm);
    }

    private void index(CustomForm form) {
        if (form == null || form.getLayout() == null) return;

        for (CustomFormPanel p : form.getLayout()) {
            if (p.getRows() == null) continue;
            for (CustomRow r : p.getRows()) {
                if (r.getColumns() == null) continue;
                for (CustomCol c : r.getColumns()) {
                    CustomField f = c.getField();
                    if (f == null) continue;
                    byId.put(f.getId(), f);
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
