package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
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
 * Projette les champs de formulaire d'un contenant en une carte {@code answers} plate — le
 * pendant, pour Container, de {@link PhaseAnswersProjector}. Même raison de ne pas passer par le
 * moteur de formulaire : les champs de {@link Container#DETAILS_FORM} sont tous des champs
 * système dont le {@code valueBinding} pointe une propriété réelle de {@link ContainerDTO} —
 * aucune ligne {@code CustomFieldAnswer} pour un contenant. Utilisée en lecture ici ET en
 * écriture par {@code ContainerOpenApiService#applyAnswerPatch} (même index, même sous-ensemble
 * de types de champ pris en charge : TEXT, SELECT_ONE_FROM_FIELD_CODE, SELECT_ONE_SPATIAL_UNIT,
 * CustomFieldMeasurement — tout ce que {@code ContainerForm} utilise réellement).
 */
@Slf4j
@Component
public class ContainerAnswersProjector {

    public static final String FIELDS_ALL = "all";

    private static final String CONCEPTS = "concepts";
    private static final String SPATIAL_UNITS = "spatial-units";

    private static final Map<String, CustomField> FIELDS_BY_ID = indexDetailsFormFields();

    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }

    private static Map<String, CustomField> indexDetailsFormFields() {
        Map<String, CustomField> out = new LinkedHashMap<>();
        for (CustomField field : new PanelFieldSource(Container.DETAILS_FORM).getAllFields()) {
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

    public List<ConceptDTO> collectConcepts(Collection<ContainerDTO> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        List<ConceptDTO> concepts = new ArrayList<>();
        for (ContainerDTO row : rows) {
            if (row == null) continue;
            for (CustomField field : fields) {
                Object raw = readBinding(row, field);
                if (raw instanceof ConceptDTO c) {
                    concepts.add(c);
                }
            }
        }
        return concepts;
    }

    public Map<Long, Map<String, Object>> project(Collection<ContainerDTO> rows,
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
        for (ContainerDTO row : rows) {
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
     * Un seul contenant (détail) — même projection que {@link #project}, sans le lot.
     */
    public Map<String, Object> projectOne(ContainerDTO row, Map<Long, String> resolvedLabels) {
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

    private static Object readBinding(ContainerDTO row, CustomField field) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(ContainerDTO.class, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' du champ {} sans propriété correspondante sur ContainerDTO", binding, field.getId());
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
        if (field instanceof CustomFieldSelectOneFromFieldCode) {
            return raw instanceof ConceptDTO c ? conceptRef(c, labels) : null;
        }
        if (field instanceof CustomFieldSelectOneSpatialUnit) {
            return raw instanceof SpatialUnitSummaryDTO s ? spatialUnitRef(s) : null;
        }
        if (field instanceof CustomFieldMeasurement) {
            return raw instanceof MeasurementAnswerDTO m ? measurementRef(m) : null;
        }
        log.debug("Type de champ non projeté pour Container: {}", field.getClass().getSimpleName());
        return null;
    }

    private static ResourceRef conceptRef(ConceptDTO concept, Map<Long, String> labels) {
        return new ResourceRef(String.valueOf(concept.getId()), CONCEPTS, ConceptLabelBatchResolver.labelOf(concept, labels));
    }

    private static ResourceRef spatialUnitRef(SpatialUnitSummaryDTO su) {
        return new ResourceRef(String.valueOf(su.getId()), SPATIAL_UNITS, su.getName());
    }

    private static MeasurementRef measurementRef(MeasurementAnswerDTO measurement) {
        String symbol = measurement.getUnit() != null ? measurement.getUnit().getSymbol() : null;
        return new MeasurementRef(measurement.getNumericValue(), symbol, measurement.getNormalizedValue(), measurement.getComment());
    }
}
