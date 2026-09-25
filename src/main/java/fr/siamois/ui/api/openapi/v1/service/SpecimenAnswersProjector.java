package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.SpecimenSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.MeasurementRef;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
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
 * Projette les champs de formulaire d'un mobilier en une carte {@code answers} plate, pour une page
 * entière — le pendant, pour la liste mobilier, de {@link ContainerAnswersProjector}. Les champs de
 * {@link Specimen#DETAILS_FORM} sont tous des champs système dont le {@code valueBinding} pointe une
 * propriété réelle de {@link SpecimenDTO} ; les champs additionnels sont fusionnés ensuite par
 * {@link AdditionalAnswersListProjector}.
 *
 * <p>Contrairement à la liste UE, les relations ({@code parents}/{@code children},
 * {@code containers}, {@code phases}…) sont bien chargées sur les DTO de liste mobilier (conversion
 * complète par {@code SpecimenMapper}) : elles sont donc projetées, en listes de références.</p>
 */
@Slf4j
@Component
public class SpecimenAnswersProjector {

    public static final String FIELDS_ALL = "all";

    private static final Map<String, CustomField> FIELDS_BY_ID = indexDetailsFormFields();

    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }

    private static Map<String, CustomField> indexDetailsFormFields() {
        Map<String, CustomField> out = new LinkedHashMap<>();
        for (CustomField field : new PanelFieldSource(Specimen.DETAILS_FORM).getAllFields()) {
            if (field != null && field.getId() != null) {
                out.put(String.valueOf(field.getId()), field);
            }
        }
        return Map.copyOf(out);
    }

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

    /** Concepts (seuls ou en collection) référencés par les champs demandés, à résoudre en un lot. */
    public List<ConceptDTO> collectConcepts(Collection<SpecimenDTO> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        List<ConceptDTO> concepts = new ArrayList<>();
        for (SpecimenDTO row : rows) {
            if (row == null) continue;
            for (CustomField field : fields) {
                Object raw = readBinding(row, field);
                if (raw instanceof ConceptDTO c) {
                    concepts.add(c);
                } else if (raw instanceof Collection<?> items) {
                    items.stream().filter(ConceptDTO.class::isInstance).map(ConceptDTO.class::cast).forEach(concepts::add);
                }
            }
        }
        return concepts;
    }

    public Map<Long, Map<String, Object>> project(Collection<SpecimenDTO> rows,
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
        for (SpecimenDTO row : rows) {
            if (row == null || row.getId() == null) continue;
            Map<String, Object> answers = new LinkedHashMap<>();
            for (CustomField field : fields) {
                answers.put(String.valueOf(field.getId()), toWireValue(field, readBinding(row, field), labels));
            }
            out.put(row.getId(), answers);
        }
        return out;
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

    private static Object readBinding(SpecimenDTO row, CustomField field) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(SpecimenDTO.class, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' du champ {} sans propriété correspondante sur SpecimenDTO", binding, field.getId());
            return null;
        }
        try {
            return getter.invoke(row);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Lecture impossible du binding '{}' (champ {}): {}", binding, field.getId(), ex.toString());
            return null;
        }
    }

    private static Object toWireValue(CustomField field, Object raw, Map<Long, String> labels) {
        if (raw == null) {
            return null;
        }
        if (field instanceof CustomFieldText) {
            return String.valueOf(raw);
        }
        if (field instanceof CustomFieldInteger) {
            return raw instanceof Number n ? n.intValue() : null;
        }
        if (field instanceof CustomFieldDateTime) {
            return raw;
        }
        if (field instanceof CustomFieldMeasurement) {
            return raw instanceof MeasurementAnswerDTO m ? measurementRef(m) : null;
        }
        // Every remaining DETAILS_FORM field is a reference, single or multiple: the value's own
        // shape (one DTO or a collection) decides, same as FieldAnswerWireService on the detail.
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
            log.debug("Type de champ non projeté pour la liste mobilier: {}", field.getClass().getSimpleName());
        }
        return ref;
    }

    private static ResourceRef toRef(Object item, Map<Long, String> labels) {
        if (item instanceof ConceptDTO c) {
            return ref(c.getId(), "concepts", ConceptLabelBatchResolver.labelOf(c, labels));
        }
        if (item instanceof PersonDTO p) return ref(p.getId(), "persons", p.displayName());
        if (item instanceof RecordingUnitSummaryDTO r) return ref(r.getId(), "recording-units", r.getFullIdentifier());
        if (item instanceof ActionUnitSummaryDTO a) {
            return ref(a.getId(), "action-units", a.getFullIdentifier() != null ? a.getFullIdentifier() : a.getName());
        }
        if (item instanceof SpecimenSummaryDTO s) return ref(s.getId(), "finds", s.getFullIdentifier());
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

    private static MeasurementRef measurementRef(MeasurementAnswerDTO measurement) {
        String symbol = measurement.getUnit() != null ? measurement.getUnit().getSymbol() : null;
        return new MeasurementRef(measurement.getNumericValue(), symbol, measurement.getNormalizedValue(), measurement.getComment());
    }
}
