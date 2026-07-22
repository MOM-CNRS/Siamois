package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

public class PanelFieldSource extends AbstractFieldSource {

    public PanelFieldSource(FormUiDto detailsForm) {
        walkForm(detailsForm, this::indexField);
    }

    private void indexField(CustomField field, CustomColUiDto column) {
        byId.put(field.getId(), field);
        registerSpecs(field, column);
    }
}
