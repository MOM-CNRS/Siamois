package fr.siamois.ui.form.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exceptions.form.CantSerializeFormPanelException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serializes a {@link FormUiDto} layout to the same compact JSON shape as
 * {@code CustomFormLayoutConverter#convertToDatabaseColumn}, for system forms that are built in
 * memory (never persisted) but still need to expose their layout as JSON over the API.
 */
public final class FormUiDtoLayoutJson {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CLASS_NAME_KEY = "className";

    private FormUiDtoLayoutJson() {
        throw new UnsupportedOperationException();
    }

    public static String serialize(List<CustomFormPanelUiDto> layout) {
        if (layout == null || layout.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> serializedLayout = layout.stream()
                    .map(FormUiDtoLayoutJson::serializePanel)
                    .toList();
            return objectMapper.writeValueAsString(serializedLayout);
        } catch (JsonProcessingException e) {
            throw new CantSerializeFormPanelException(e.getMessage());
        }
    }

    private static Map<String, Object> serializePanel(CustomFormPanelUiDto panel) {
        Map<String, Object> panelMap = new HashMap<>();
        panelMap.put(CLASS_NAME_KEY, panel.getClassName());
        panelMap.put("name", panel.getName());
        panelMap.put("canUserAddFields", panel.getCanUserAddFields());
        panelMap.put("isSystemPanel", panel.getIsSystemPanel());

        List<Map<String, Object>> rows = Optional.ofNullable(panel.getRows()).orElse(List.of()).stream()
                .map(FormUiDtoLayoutJson::serializeRow)
                .toList();

        panelMap.put("rows", rows);
        return panelMap;
    }

    private static Map<String, Object> serializeRow(CustomRowUiDto row) {
        Map<String, Object> rowMap = new HashMap<>();
        List<Map<String, Object>> columns = Optional.ofNullable(row.getColumns()).orElse(List.of()).stream()
                .map(FormUiDtoLayoutJson::serializeCol)
                .toList();
        rowMap.put("columns", columns);
        return rowMap;
    }

    private static Map<String, Object> serializeCol(CustomColUiDto col) {
        Map<String, Object> colMap = new HashMap<>();
        colMap.put(CLASS_NAME_KEY, col.getClassName());
        colMap.put("isRequired", col.isRequired());
        colMap.put("isReadOnly", col.isReadOnly());
        if (col.getField() != null) {
            colMap.put("fieldId", col.getField().getId());
        }
        if (col.getEnabledWhenSpec() != null) {
            Map<String, Object> ew = objectMapper.convertValue(col.getEnabledWhenSpec(), Map.class);
            colMap.put("enabledWhen", ew);
        }
        if (col.getDependsOnSpec() != null) {
            Map<String, Object> dep = objectMapper.convertValue(col.getDependsOnSpec(), Map.class);
            colMap.put("dependsOn", dep);
        }
        return colMap;
    }
}
