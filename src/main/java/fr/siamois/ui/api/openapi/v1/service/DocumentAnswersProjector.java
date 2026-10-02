package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldFile;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.DocumentFileDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Projects a document's form fields into a flat {@code answers} map — the {@link PhaseAnswersProjector}
 * counterpart. Every field of the document table is a system field whose {@code valueBinding} names a real
 * property of {@link DocumentDTO} (additional fields are merged in by {@link AdditionalAnswersListProjector}),
 * so no form engine is needed: a reflective read, shaped as raw values (text, number, date, concept /
 * person / entity references).
 */
@Slf4j
@Component
public class DocumentAnswersProjector {

    public static final String FIELDS_ALL = "all";

    private static final String CONCEPTS = "concepts";

    private static final Map<String, CustomField> FIELDS_BY_ID = indexDetailsFormFields();

    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }

    public static Map<String, CustomField> allFields() {
        return FIELDS_BY_ID;
    }

    private static Map<String, CustomField> indexDetailsFormFields() {
        Map<String, CustomField> out = new LinkedHashMap<>();
        for (CustomField field : SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.DOCUMENT)) {
            if (field != null && field.getId() != null) {
                out.put(String.valueOf(field.getId()), field);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * @param fieldsParam {@code null}/vide → pas de projection ; {@code all} → tout le catalogue ;
     *                    sinon une liste d'ids séparés par des virgules (ids inconnus ignorés).
     */
    // A null selection means "no projection requested", which the list projection tells apart from an empty one.
    @SuppressWarnings("java:S1168")
    public Set<String> resolveRequestedFieldIds(String fieldsParam) {
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return null;
        }
        String value = fieldsParam.trim();
        if (FIELDS_ALL.equalsIgnoreCase(value)) {
            return FIELDS_BY_ID.keySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : value.split(",")) {
            String id = raw.trim();
            if (!id.isEmpty() && FIELDS_BY_ID.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public List<ConceptDTO> collectConcepts(Collection<DocumentDTO> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        List<ConceptDTO> concepts = new ArrayList<>();
        for (DocumentDTO row : rows) {
            if (row == null) continue;
            for (CustomField field : fields) {
                addConcepts(readBinding(row, field), concepts);
            }
        }
        return concepts;
    }

    /** The concept a binding holds, or those of the collection it holds. */
    private static void addConcepts(Object raw, List<ConceptDTO> concepts) {
        if (raw instanceof ConceptDTO c) {
            concepts.add(c);
        } else if (raw instanceof Collection<?> items) {
            for (Object item : items) {
                if (item instanceof ConceptDTO c) concepts.add(c);
            }
        }
    }

    public Map<Long, Map<String, Object>> project(Collection<DocumentDTO> rows,
                                                   Set<String> requestedFieldIds,
                                                   Map<Long, String> resolvedLabels) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (fields.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> labels = resolvedLabels == null ? Map.of() : resolvedLabels;

        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (DocumentDTO row : rows) {
            if (row == null || row.getId() == null) continue;
            Map<String, Object> answers = new LinkedHashMap<>();
            for (CustomField field : fields) {
                answers.put(String.valueOf(field.getId()), toWireValue(field, readBinding(row, field), labels));
            }
            out.put(row.getId(), answers);
        }
        return out;
    }

    /**
     * Un seul document — même projection que {@link #project}, sans le lot : utilisée par le détail
     * ({@code GET /api/v1/documents/{id}}), où un seul appel {@code ConceptLabelBatchResolver} suffit.
     */
    public Map<String, Object> projectOne(DocumentDTO row, Map<Long, String> resolvedLabels) {
        if (row == null || row.getId() == null) {
            return Map.of();
        }
        Map<Long, Map<String, Object>> projected = project(List.of(row), FIELDS_BY_ID.keySet(), resolvedLabels);
        return projected.getOrDefault(row.getId(), Map.of());
    }

    private static List<CustomField> fieldsOf(Set<String> requestedFieldIds) {
        if (requestedFieldIds == null || requestedFieldIds.isEmpty()) {
            return List.of();
        }
        return requestedFieldIds.stream()
                .map(FIELDS_BY_ID::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Object readBinding(DocumentDTO row, CustomField field) {
        String binding = field.getValueBinding();
        // A relation field is never on the DTO: MultiValueAnswers reads it, a preview and a count.
        if (binding == null || binding.isBlank() || RelationField.isRelation(Document.class, binding)) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(DocumentDTO.class, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' du champ {} sans propriété correspondante sur DocumentDTO", binding, field.getId());
            return null;
        }
        try {
            return getter.invoke(row);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Lecture impossible du binding '{}' (champ {}): {}", binding, field.getId(), ex.toString());
            return null;
        }
    }

    private static Map<String, Object> fileValue(DocumentFileDTO file) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileName", file.fileName());
        out.put("mimeType", file.mimeType());
        out.put("size", file.size());
        return out;
    }

    private static boolean isScalar(CustomField field) {
        return field instanceof CustomFieldFile || field instanceof CustomFieldText || field instanceof CustomFieldInteger
                || field instanceof CustomFieldDecimal || field instanceof CustomFieldDateTime;
    }

    /** A value that is not a reference: text, number, date, or the stored file. */
    private static Object scalarValue(CustomField field, Object raw) {
        if (field instanceof CustomFieldFile) {
            return raw instanceof DocumentFileDTO file ? fileValue(file) : null;
        }
        if (field instanceof CustomFieldText) {
            return String.valueOf(raw);
        }
        if (field instanceof CustomFieldInteger) {
            return raw instanceof Number n ? n.intValue() : null;
        }
        if (field instanceof CustomFieldDecimal) {
            return raw instanceof Number n ? n.doubleValue() : null;
        }
        return raw;
    }

    private static Object toWireValue(CustomField field, Object raw, Map<Long, String> labels) {
        if (raw == null) {
            return null;
        }
        if (isScalar(field)) {
            return scalarValue(field, raw);
        }
        if (raw instanceof Collection<?> items) {
            List<ResourceRef> refs = new ArrayList<>(items.size());
            for (Object item : items) {
                ResourceRef ref = toRef(item, labels);
                if (ref != null) refs.add(ref);
            }
            return refs;
        }
        ResourceRef ref = toRef(raw, labels);
        if (ref == null) {
            log.debug("Type de champ non projeté pour Document: {}", field.getClass().getSimpleName());
        }
        return ref;
    }

    private static ResourceRef toRef(Object item, Map<Long, String> labels) {
        if (item instanceof ConceptDTO c) {
            return ref(c.getId(), CONCEPTS, ConceptLabelBatchResolver.labelOf(c, labels));
        }
        if (item instanceof PersonDTO p) return ref(p.getId(), "persons", p.displayName());
        if (item instanceof ActionUnitSummaryDTO a) {
            return ref(a.getId(), "action-units", a.getFullIdentifier() != null ? a.getFullIdentifier() : a.getName());
        }
        if (item instanceof RecordingUnitSummaryDTO r) return ref(r.getId(), "recording-units", r.getFullIdentifier());
        if (item instanceof SpecimenSummaryDTO sp) return ref(sp.getId(), "finds", sp.getFullIdentifier());
        if (item instanceof SpatialUnitSummaryDTO su) return ref(su.getId(), "spatial-units", su.getName());
        if (item instanceof ContainerDTO c) return ref(c.getId(), "containers", c.getIdentifier());
        if (item instanceof PhaseDTO p) {
            String label = p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : p.getIdentifier();
            return ref(p.getId(), "phases", label);
        }
        return null;
    }

    private static ResourceRef ref(Long id, String resourceType, String label) {
        return id == null ? null : new ResourceRef(String.valueOf(id), resourceType, label);
    }
}
