package fr.siamois.ui.form.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormUiDtoLayoutJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static CustomFieldText field(long id) {
        CustomFieldText field = new CustomFieldText();
        field.setId(id);
        return field;
    }

    private JsonNode firstColumn(CustomColUiDto col) throws Exception {
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto.Builder()
                .name("panel")
                .addRow(new CustomRowUiDto.Builder().addColumn(col).build())
                .build();

        String json = FormUiDtoLayoutJson.serialize(List.of(panel));
        return objectMapper.readTree(json).get(0).get("rows").get(0).get("columns").get(0);
    }

    @Test
    void serialize_emitsAStructuredWidthObjectForAColumnBuiltWithWidth() throws Exception {
        JsonNode column = firstColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(field(42)).build());

        assertThat(column.get("width").get("span").asInt()).isEqualTo(12);
        assertThat(column.get("width").get("md").asInt()).isEqualTo(6);
        assertThat(column.get("width").get("lg").asInt()).isEqualTo(3);
        assertThat(column.get("hidden").asBoolean()).isFalse();
        assertThat(column.has("className")).isFalse();
    }

    @Test
    void serialize_omitsAbsentBreakpointsFromTheWidthObject() throws Exception {
        JsonNode column = firstColumn(new CustomColUiDto.Builder().width(new ColumnWidth(12, null, null)).field(field(42)).build());

        assertThat(column.get("width").get("span").asInt()).isEqualTo(12);
        assertThat(column.get("width").has("md")).isFalse();
        assertThat(column.get("width").has("lg")).isFalse();
    }

    @Test
    void serialize_marksAHiddenColumn() throws Exception {
        JsonNode column = firstColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).hidden(true).field(field(42)).build());

        assertThat(column.get("hidden").asBoolean()).isTrue();
    }

    @Test
    void serialize_fallsBackToClassNameForAColumnWithNoWidth() throws Exception {
        JsonNode column = firstColumn(new CustomColUiDto.Builder().className("ui-g-12 ui-md-6 ui-lg-4").field(field(42)).build());

        assertThat(column.get("className").asText()).isEqualTo("ui-g-12 ui-md-6 ui-lg-4");
        assertThat(column.has("width")).isFalse();
    }
}
