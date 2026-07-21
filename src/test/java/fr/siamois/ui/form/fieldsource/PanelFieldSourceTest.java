package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PanelFieldSourceTest {

    private static Concept conceptWithId(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setExternalId("concept-" + id);
        return concept;
    }

    @Test
    void getDependsOnSpec_shouldReturnIndexedSpecForField() {
        CustomField dependentField = CustomFieldText.builder().id(1L).concept(conceptWithId(1L)).build();
        CustomField independentField = CustomFieldText.builder().id(2L).concept(conceptWithId(2L)).build();

        DependsOnJson dependsOn = new DependsOnJson();
        dependsOn.setFieldId(99L);

        CustomColUiDto dependentCol = new CustomColUiDto();
        dependentCol.setField(dependentField);
        dependentCol.setDependsOnSpec(dependsOn);

        CustomColUiDto independentCol = new CustomColUiDto();
        independentCol.setField(independentField);

        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(List.of(dependentCol, independentCol));

        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));

        FormUiDto form = new FormUiDto();
        form.setLayout(List.of(panel));

        PanelFieldSource fieldSource = new PanelFieldSource(form);

        assertEquals(99L, fieldSource.getDependsOnSpec(dependentField).getFieldId());
        assertNull(fieldSource.getDependsOnSpec(independentField));
    }
}
