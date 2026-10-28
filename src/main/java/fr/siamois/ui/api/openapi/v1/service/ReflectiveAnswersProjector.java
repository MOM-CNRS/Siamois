package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.mapper.geom.GeometryText;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.*;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Projects the form fields of a configurable table into a flat {@code answers} map. Every field of such a
 * table is a system field whose {@code valueBinding} names a real property of its DTO (additional fields are
 * merged in by {@link AdditionalAnswersListProjector}), so no form engine is needed: a reflective read, shaped
 * as raw values (text, number, date, concept / person / entity references). The table's own projector only
 * says which DTO, entity and fields it is about, and adds the kinds of value that are specific to it.
 *
 * @param <D> the table's DTO
 */
@Slf4j
public abstract class ReflectiveAnswersProjector<D extends AbstractEntityDTO> {

    public static final String FIELDS_ALL = "all";

    private static final String CONCEPTS = "concepts";

    private final Class<D> dtoClass;
    private final Class<?> entityClass;
    private final Map<String, CustomField> fieldsById;

    protected ReflectiveAnswersProjector(Class<D> dtoClass, Class<?> entityClass, Map<String, CustomField> fieldsById) {
        this.dtoClass = dtoClass;
        this.entityClass = entityClass;
        this.fieldsById = fieldsById;
    }

    /** The table's shared system fields, by field id (as a string). */
    protected static Map<String, CustomField> indexFields(ConfigurableTable table) {
        Map<String, CustomField> out = new LinkedHashMap<>();
        SystemFieldCatalog.sharedFieldsOf(table).stream()
                .filter(field -> field != null && field.getId() != null)
                .forEach(field -> out.put(String.valueOf(field.getId()), field));
        return Map.copyOf(out);
    }

    /**
     * @param fieldsParam {@code null}/blank: no projection; {@code all}: the whole catalog;
     *                    otherwise a comma-separated list of ids (unknown ids ignored).
     */
    // A null selection means "no projection requested", which the list projection tells apart from an empty one.
    @SuppressWarnings("java:S1168")
    public Set<String> resolveRequestedFieldIds(String fieldsParam) {
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return null;
        }
        String value = fieldsParam.trim();
        if (FIELDS_ALL.equalsIgnoreCase(value)) {
            return fieldsById.keySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(fieldsById::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<ConceptDTO> collectConcepts(Collection<D> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        List<ConceptDTO> concepts = new ArrayList<>();
        rows.stream().filter(Objects::nonNull)
                .forEach(row -> fields.forEach(field -> addConcepts(readBinding(row, field), concepts)));
        return concepts;
    }

    /** The concept a binding holds, or those of the collection it holds. */
    private static void addConcepts(Object raw, List<ConceptDTO> concepts) {
        if (raw instanceof ConceptDTO c) {
            concepts.add(c);
        } else if (raw instanceof Collection<?> items) {
            items.stream().filter(ConceptDTO.class::isInstance).map(ConceptDTO.class::cast).forEach(concepts::add);
        }
    }

    public Map<Long, Map<String, Object>> project(Collection<D> rows,
                                                  Set<String> requestedFieldIds,
                                                  Map<Long, String> resolvedLabels) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || rows.isEmpty() || fields.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> labels = resolvedLabels == null ? Map.of() : resolvedLabels;
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (D row : rows) {
            if (row == null || row.getId() == null) continue;
            Map<String, Object> answers = new LinkedHashMap<>();
            fields.forEach(field -> answers.put(String.valueOf(field.getId()),
                    toWireValue(field, readBinding(row, field), labels)));
            out.put(row.getId(), answers);
        }
        return out;
    }

    /** One row (a detail): the same projection as {@link #project}, without the batch. */
    public Map<String, Object> projectOne(D row, Map<Long, String> resolvedLabels) {
        if (row == null || row.getId() == null) {
            return Map.of();
        }
        return project(List.of(row), fieldsById.keySet(), resolvedLabels).getOrDefault(row.getId(), Map.of());
    }

    private List<CustomField> fieldsOf(Set<String> requestedFieldIds) {
        if (requestedFieldIds == null) {
            return List.of();
        }
        return requestedFieldIds.stream().map(fieldsById::get).filter(Objects::nonNull).toList();
    }

    private Object readBinding(D row, CustomField field) {
        String binding = field.getValueBinding();
        // A relation field is never on the DTO: MultiValueAnswers reads it, a preview and a count.
        if (binding == null || binding.isBlank() || RelationField.isRelation(entityClass, binding)) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(dtoClass, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' of field {} has no matching property on {}", binding, field.getId(), dtoClass.getSimpleName());
            return null;
        }
        try {
            return getter.invoke(row);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Cannot read binding '{}' (field {}): {}", binding, field.getId(), ex.toString());
            return null;
        }
    }

    /** Whether the field holds a plain value (text, number, date…) rather than a reference. */
    protected boolean isScalar(CustomField field) {
        return field instanceof CustomFieldText || field instanceof CustomFieldInteger
                || field instanceof CustomFieldDecimal || field instanceof CustomFieldDateTime
                || field instanceof CustomFieldBoolean;
    }

    /** A value that is not a reference: text, number or date. */
    protected Object scalarValue(CustomField field, Object raw) {
        if (field instanceof CustomFieldText) {
            return raw instanceof GeometryDTO geometry ? GeometryText.of(geometry) : String.valueOf(raw);
        }
        if (field instanceof CustomFieldInteger) {
            return raw instanceof Number n ? n.intValue() : null;
        }
        if (field instanceof CustomFieldBoolean) {
            return raw instanceof Boolean b ? b : null;
        }
        if (field instanceof CustomFieldDecimal) {
            return raw instanceof Number n ? n.doubleValue() : null;
        }
        return raw;
    }

    private Object toWireValue(CustomField field, Object raw, Map<Long, String> labels) {
        if (raw == null) {
            return null;
        }
        if (isScalar(field)) {
            return scalarValue(field, raw);
        }
        if (raw instanceof Collection<?> items) {
            return items.stream().map(item -> toRef(item, labels)).filter(Objects::nonNull).toList();
        }
        ResourceRef ref = toRef(raw, labels);
        if (ref == null) {
            log.debug("Field type not projected for {}: {}", dtoClass.getSimpleName(), field.getClass().getSimpleName());
        }
        return ref;
    }

    private static ResourceRef toRef(Object item, Map<Long, String> labels) {
        if (item instanceof ConceptDTO c) {
            return ref(c.getId(), CONCEPTS, ConceptLabelBatchResolver.labelOf(c, labels));
        }
        if (item instanceof PersonDTO p) return ref(p.getId(), "persons", p.displayName());
        if (item instanceof ActionUnitSummaryDTO a) {
            return ref(a.getId(), "projects", a.getFullIdentifier() != null ? a.getFullIdentifier() : a.getName());
        }
        if (item instanceof RecordingUnitSummaryDTO r) return ref(r.getId(), "recording-units", r.getFullIdentifier());
        if (item instanceof SpecimenSummaryDTO sp) return ref(sp.getId(), "finds", sp.getFullIdentifier());
        if (item instanceof SpatialUnitSummaryDTO su) return ref(su.getId(), "places", su.getName());
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
