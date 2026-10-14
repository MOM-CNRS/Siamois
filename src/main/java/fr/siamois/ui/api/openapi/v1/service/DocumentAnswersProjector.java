package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldFile;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.DocumentFileDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects a document's form fields into a flat {@code answers} map — the {@link PhaseAnswersProjector}
 * counterpart. On top of {@link ReflectiveAnswersProjector}, a document has a stored file, shown as its
 * name, type and size.
 */
@Component
public class DocumentAnswersProjector extends ReflectiveAnswersProjector<DocumentDTO> {

    private static final Map<String, CustomField> FIELDS_BY_ID = indexFields(ConfigurableTable.DOCUMENT);

    public DocumentAnswersProjector() {
        super(DocumentDTO.class, Document.class, FIELDS_BY_ID);
    }

    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }

    public static Map<String, CustomField> allFields() {
        return FIELDS_BY_ID;
    }

    @Override
    protected boolean isScalar(CustomField field) {
        return field instanceof CustomFieldFile || super.isScalar(field);
    }

    @Override
    protected Object scalarValue(CustomField field, Object raw) {
        if (field instanceof CustomFieldFile) {
            return raw instanceof DocumentFileDTO file ? fileValue(file) : null;
        }
        return super.scalarValue(field, raw);
    }

    private static Map<String, Object> fileValue(DocumentFileDTO file) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileName", file.fileName());
        out.put("mimeType", file.mimeType());
        out.put("size", file.size());
        return out;
    }
}
