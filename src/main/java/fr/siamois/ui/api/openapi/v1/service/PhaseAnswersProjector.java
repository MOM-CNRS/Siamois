package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
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
import java.util.function.Function;

/**
 * Projette les champs de formulaire d'une phase en une carte {@code answers} plate — le pendant,
 * pour Phase, de {@link ProjectAnswersProjector}. Même raison de ne pas passer par le moteur de
 * formulaire : les 9 champs de {@link Phase#DETAILS_FORM} sont tous des champs système
 * ({@code isSystemField=true}) dont le {@code valueBinding} pointe une propriété réelle de
 * {@link PhaseDTO} — aucune ligne {@code CustomFieldAnswer} pour une phase. Utilisée en lecture ici
 * ET en écriture par {@code PhaseOpenApiService#applyAnswerPatch} (même index, même sous-ensemble
 * de types de champ pris en charge : TEXT, INTEGER, SELECT_ONE_FROM_FIELD_CODE,
 * SELECT_MULTIPLE_FROM_FIELD_CODE — tout ce que {@code PhaseForm} utilise réellement).
 */
@Slf4j
@Component
public class PhaseAnswersProjector {

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
        for (CustomField field : new PanelFieldSource(Phase.DETAILS_FORM).getAllFields()) {
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

    public List<ConceptDTO> collectConcepts(Collection<PhaseDTO> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        List<ConceptDTO> concepts = new ArrayList<>();
        for (PhaseDTO row : rows) {
            if (row == null) continue;
            for (CustomField field : fields) {
                Object raw = readBinding(row, field);
                if (raw instanceof ConceptDTO c) {
                    concepts.add(c);
                } else if (raw instanceof Collection<?> items) {
                    for (Object item : items) {
                        if (item instanceof ConceptDTO c) concepts.add(c);
                    }
                }
            }
        }
        return concepts;
    }

    public Map<Long, Map<String, Object>> project(Collection<PhaseDTO> rows,
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
        for (PhaseDTO row : rows) {
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
     * Une seule phase — même projection que {@link #project}, sans le lot : utilisée par le détail
     * ({@code GET /api/v1/phases/{id}}), où un seul appel {@code ConceptLabelBatchResolver} suffit.
     */
    public Map<String, Object> projectOne(PhaseDTO row, Map<Long, String> resolvedLabels) {
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

    private static Object readBinding(PhaseDTO row, CustomField field) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(PhaseDTO.class, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' du champ {} sans propriété correspondante sur PhaseDTO", binding, field.getId());
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
        if (field instanceof CustomFieldSelectOneFromFieldCode) {
            return raw instanceof ConceptDTO c ? conceptRef(c, labels) : null;
        }
        if (field instanceof CustomFieldSelectMultipleFromFieldCode) {
            return refList(raw, item -> item instanceof ConceptDTO c ? conceptRef(c, labels) : null);
        }
        log.debug("Type de champ non projeté pour Phase: {}", field.getClass().getSimpleName());
        return null;
    }

    private static List<ResourceRef> refList(Object raw, Function<Object, ResourceRef> mapper) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        List<ResourceRef> out = new ArrayList<>(items.size());
        for (Object item : items) {
            ResourceRef ref = mapper.apply(item);
            if (ref != null) out.add(ref);
        }
        return out;
    }

    private static ResourceRef conceptRef(ConceptDTO concept, Map<Long, String> labels) {
        return new ResourceRef(String.valueOf(concept.getId()), CONCEPTS, ConceptLabelBatchResolver.labelOf(concept, labels));
    }
}
