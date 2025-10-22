package fr.siamois.domain.services.attributeconverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exceptions.form.CantDeserializeFormPanelException;
import fr.siamois.domain.models.exceptions.form.CantSerializeFormPanelException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converter for CustomFormLayout, which serializes and deserializes a list of CustomFormPanel objects to and from a JSON string.
 * This converter is used to store the layout of custom forms in the database.
 */
@Converter
@Component
@Slf4j
public class CustomFormLayoutConverter implements AttributeConverter<List<CustomFormPanel>, String> {

    private final ApplicationContext applicationContext;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CLASS_NAME_KEY = "className";

    public CustomFormLayoutConverter(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private CustomFieldRepository getCustomFieldRepository() {
        return applicationContext.getBean(CustomFieldRepository.class);
    }


    @Override
    public String convertToDatabaseColumn(List<CustomFormPanel> layout) {
        try {
            List<Map<String, Object>> serializedLayout = layout.stream()
                    .map(this::serializePanel)
                    .toList();
            return objectMapper.writeValueAsString(serializedLayout);
        } catch (JsonProcessingException e) {
            throw new CantSerializeFormPanelException(e.getMessage());
        }
    }

    private Map<String, Object> serializePanel(CustomFormPanel panel) {
        Map<String, Object> panelMap = new HashMap<>();
        panelMap.put(CLASS_NAME_KEY, panel.getClassName());
        panelMap.put("name", panel.getName());
        panelMap.put("isSystemPanel", panel.getIsSystemPanel());

        List<Map<String, Object>> rows = Optional.ofNullable(panel.getRows()).orElse(List.of()).stream()
                .map(this::serializeRow)
                .toList();

        panelMap.put("rows", rows);
        return panelMap;
    }

    private Map<String, Object> serializeRow(CustomRow row) {
        Map<String, Object> rowMap = new HashMap<>();
        List<Map<String, Object>> columns = Optional.ofNullable(row.getColumns()).orElse(List.of()).stream()
                .map(this::serializeCol)
                .toList();
        rowMap.put("columns", columns);
        return rowMap;
    }

    private Map<String, Object> serializeCol(CustomCol col) {
        Map<String, Object> colMap = new HashMap<>();
        colMap.put(CLASS_NAME_KEY, col.getClassName());
        if (col.getField() != null) {
            colMap.put("fieldId", col.getField().getId());
        }
        return colMap;
    }

    @Override
    public List<CustomFormPanel> convertToEntityAttribute(String json) {
        try {
            List<Map<String, Object>> parsedPanels = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsedPanels.stream()
                    .map(this::deserializePanel)
                    .toList();
        } catch (IOException e) {
            log.error("Error deserializing CustomFormPanel JSON", e);
            throw new CantDeserializeFormPanelException(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CustomFormPanel deserializePanel(Map<String, Object> panelMap) {
        CustomFormPanel panel = new CustomFormPanel();
        panel.setClassName((String) panelMap.get(CLASS_NAME_KEY));
        panel.setName((String) panelMap.get("name"));
        panel.setIsSystemPanel((Boolean) panelMap.get("isSystemPanel"));

        List<Map<String, Object>> rowsList = (List<Map<String, Object>>) panelMap.get("rows");
        List<CustomRow> rows = Optional.ofNullable(rowsList).orElse(List.of()).stream()
                .map(this::deserializeRow)
                .toList();

        panel.setRows(rows);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private CustomRow deserializeRow(Map<String, Object> rowMap) {
        CustomRow row = new CustomRow();
        List<Map<String, Object>> colsList = (List<Map<String, Object>>) rowMap.get("columns");

        List<CustomCol> columns = Optional.ofNullable(colsList).orElse(List.of()).stream()
                .map(this::deserializeCol)
                .toList();

        row.setColumns(columns);
        return row;
    }


    private CustomCol deserializeCol(Map<String, Object> colMap) {
        CustomCol col = new CustomCol();
        col.setClassName((String) colMap.get(CLASS_NAME_KEY));

        Object fieldId = colMap.get("fieldId");
        if (fieldId != null) {
            CustomField field = getCustomFieldRepository().findById(Long.valueOf(fieldId.toString()))
                    .orElse(null);
            col.setField(field);
        }

        return col;
    }
}
