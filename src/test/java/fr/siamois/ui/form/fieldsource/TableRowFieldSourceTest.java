package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.FormFieldColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TableRowFieldSourceTest {

    private static Concept conceptWithId(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setExternalId("concept-" + id);
        return concept;
    }

    @Test
    void getDependsOnSpec_shouldReturnIndexedSpecForFieldFromRowSpecificForm() {
        CustomField dependentField = CustomFieldText.builder().id(1L).concept(conceptWithId(1L)).build();

        TableDefinition tableDefinition = new TableDefinition();
        tableDefinition.addColumn(FormFieldColumn.builder().field(dependentField).build());

        DependsOnJson dependsOn = new DependsOnJson();
        dependsOn.setFieldId(99L);

        CustomColUiDto col = new CustomColUiDto();
        col.setField(dependentField);
        col.setDependsOnSpec(dependsOn);

        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(List.of(col));

        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));

        FormUiDto rowSpecificForm = new FormUiDto();
        rowSpecificForm.setLayout(List.of(panel));

        TableRowFieldSource fieldSource = new TableRowFieldSource(tableDefinition, rowSpecificForm);

        assertEquals(99L, fieldSource.getDependsOnSpec(dependentField).getFieldId());
    }

    @Test
    void getDependsOnSpec_shouldReturnNullWhenNoRowSpecificForm() {
        CustomField field = CustomFieldText.builder().id(1L).concept(conceptWithId(1L)).build();

        TableDefinition tableDefinition = new TableDefinition();
        tableDefinition.addColumn(FormFieldColumn.builder().field(field).build());

        TableRowFieldSource fieldSource = new TableRowFieldSource(tableDefinition);

        assertNull(fieldSource.getDependsOnSpec(field));
    }
}
