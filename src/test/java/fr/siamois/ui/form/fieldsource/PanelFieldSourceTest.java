package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PanelFieldSourceTest {

    private static Concept conceptWithId(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setExternalId("concept-" + id);
        return concept;
    }

    @Test
    void getRules_shouldReturnIndexedRulesForField() {
        CustomField dependentField = CustomFieldText.builder().id(1L).concept(conceptWithId(1L)).build();
        CustomField independentField = CustomFieldText.builder().id(2L).concept(conceptWithId(2L)).build();

        FieldRules rules = FieldRules.NONE.withOptions(new OptionsFilter.RelatedConcepts(99L));
        CustomColUiDto dependentCol = new CustomColUiDto.Builder().field(dependentField).rules(rules).build();
        CustomColUiDto independentCol = new CustomColUiDto.Builder().field(independentField).build();

        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(List.of(dependentCol, independentCol));

        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));

        FormUiDto form = new FormUiDto();
        form.setLayout(List.of(panel));

        PanelFieldSource fieldSource = new PanelFieldSource(form);

        assertEquals(rules, fieldSource.getRules(dependentField));
        assertSame(FieldRules.NONE, fieldSource.getRules(independentField));
    }
}
