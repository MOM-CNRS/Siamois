package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.*;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import jakarta.persistence.DiscriminatorValue;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Turns a form answer's value (what {@link FormService#readAnswerValueForApi} reads off a view
 * model: scalars, DTOs, collections of DTOs) into its API wire shape — a {@link FieldAnswer}
 * envelope whose references are {@link ResourceRef}s. Shared by every entity detail, so a field
 * kind serializes the same way whatever entity it sits on.
 */
@Service
@RequiredArgsConstructor
public class FieldAnswerWireService {

    public static final String CONCEPTS = "concepts";

    private final FormService formService;
    private final LabelService labelService;

    /** The {@code answer_type} discriminator of a field — the {@code answerType} the API exposes. */
    public static String answerTypeOf(CustomField field) {
        Class<?> fieldClass = Hibernate.getClass(field);
        DiscriminatorValue dv = fieldClass.getAnnotation(DiscriminatorValue.class);
        return dv != null ? dv.value() : fieldClass.getSimpleName();
    }

    /**
     * A field's catalog entry. {@code label} and {@code hint} come already resolved in the caller's
     * language (their resolution service differs between callers).
     */
    public static FieldResource fieldResourceOf(CustomField field, String label, String hint) {
        Object f = Hibernate.unproxy(field);
        String fieldCode = null;
        if (f instanceof CustomFieldSelectOneFromFieldCode one) {
            fieldCode = one.getFieldCode();
        } else if (f instanceof CustomFieldSelectMultipleFromFieldCode multi) {
            fieldCode = multi.getFieldCode();
        }
        return new FieldResource(
                String.valueOf(field.getId()),
                "fields",
                label,
                answerTypeOf(field),
                hint,
                field.getIsSystemField(),
                field.getValueBinding(),
                fieldCode,
                f instanceof CustomFieldText text ? text.getIsTextArea() : null,
                field.getIcon(),
                field.getConceptUri(),
                constraintsOf(f));
    }

    /**
     * The entry constraints a field carries. The defaults its classes fall back to (Integer.MIN_VALUE,
     * ±Double.MAX_VALUE) mean "unbounded" and are dropped rather than sent as bounds.
     */
    private static FieldResource.Constraints constraintsOf(Object f) {
        Double min = null;
        Double max = null;
        Boolean showTime = null;
        String unit = null;
        if (f instanceof CustomFieldInteger i) {
            min = i.getMinValue() != null && i.getMinValue() != Integer.MIN_VALUE ? i.getMinValue().doubleValue() : null;
            max = i.getMaxValue() != null && i.getMaxValue() != Integer.MAX_VALUE ? i.getMaxValue().doubleValue() : null;
        } else if (f instanceof CustomFieldDecimal d) {
            min = d.getMinValue() != null && d.getMinValue() != -Double.MAX_VALUE ? d.getMinValue() : null;
            max = d.getMaxValue() != null && d.getMaxValue() != Double.MAX_VALUE ? d.getMaxValue() : null;
        } else if (f instanceof CustomFieldMeasurement m) {
            min = m.getMinValue() != null ? m.getMinValue().doubleValue() : null;
            max = m.getMaxValue() != null ? m.getMaxValue().doubleValue() : null;
            unit = m.getUnit() != null ? m.getUnit().getSymbol() : null;
        } else if (f instanceof CustomFieldDateTime dt) {
            showTime = dt.getShowTime();
        }
        if (min == null && max == null && showTime == null && unit == null) return null;
        return new FieldResource.Constraints(min, max, showTime, unit);
    }

    /**
     * The answers to an entity's additional fields, keyed by field id, as the detail resources'
     * {@code answers} map carries them (the envelope's {@code field} is left out: the client has
     * the catalog already).
     */
    public Map<String, Object> additionalAnswers(Map<CustomField, CustomFieldAnswerViewModel> loaded, String lang) {
        Map<String, Object> out = new LinkedHashMap<>();
        loaded.forEach((field, viewModel) -> {
            if (field == null || field.getId() == null) return;
            out.put(String.valueOf(field.getId()),
                    toTypedAnswer(answerTypeOf(field), null, formService.readAnswerValueForApi(viewModel), lang));
        });
        return out;
    }

    public FieldAnswer toTypedAnswer(String answerType, FieldResource field, Object raw, String lang) {
        return switch (answerType) {
            case "TEXT" -> new TextFieldAnswer(answerType, field, raw instanceof String s ? s : null);
            case "INTEGER" -> new IntegerFieldAnswer(answerType, field, raw instanceof Number n ? n.intValue() : null);
            case "DECIMAL" -> new DecimalFieldAnswer(answerType, field, raw instanceof Number n ? n.doubleValue() : null);
            case "DATETIME" -> new DateFieldAnswer(answerType, field, toOffsetDateTime(raw));
            case "SELECT_ONE_FROM_FIELD_CODE", "SELECT_ONE_PERSON", "SELECT_ONE_ACTION_UNIT",
                 "SELECT_ONE_SPATIAL_UNIT", "SELECT_ONE_ACTION_CODE", "SELECT_ONE_RECORDING_UNIT",
                 "SELECT_ADDRESS", "SELECT_ONE" ->
                    new SelectOneFieldAnswer(answerType, field, raw == null ? null : toResourceRef(answerType, first(raw), lang));
            case "SELECT_MULTIPLE_PERSON", "SELECT_MULTIPLE_FROM_FIELD_CODE",
                 "SELECT_MULTIPLE_RECORDING_UNIT", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE",
                 "SELECT_MULTIPLE_SPECIMEN", "SELECT_MULTIPLE_CONTAINER",
                 "SELECT_MULTIPLE_PHASE", "SELECT_MULTIPLE" ->
                    new SelectManyFieldAnswer(answerType, field, toResourceRefList(answerType, raw, lang));
            case "MEASUREMENT" -> new MeasurementFieldAnswer(answerType, field, toMeasurementRef(raw));
            default -> new TextFieldAnswer(answerType, field, raw != null ? raw.toString() : null);
        };
    }

    private static Object first(Object raw) {
        if (raw instanceof Collection<?> c) return c.isEmpty() ? null : c.iterator().next();
        return raw;
    }

    private static OffsetDateTime toOffsetDateTime(Object raw) {
        if (raw instanceof OffsetDateTime dt) return dt;
        if (raw instanceof LocalDateTime dt) return dt.atOffset(ZoneOffset.UTC);
        return null;
    }

    private List<ResourceRef> toResourceRefList(String answerType, Object raw, String lang) {
        if (raw == null) return null;
        Collection<?> col = raw instanceof Collection<?> c ? c : List.of(raw);
        return col.stream()
                .map(item -> toResourceRef(answerType, item, lang))
                .filter(Objects::nonNull)
                .toList();
    }

    /** One referenced value, whatever DTO shape the form (or the entity's own property) holds it in. */
    private ResourceRef toResourceRef(String answerType, Object item, String lang) {
        if (item == null) return null;
        if (item instanceof ConceptDTO || item instanceof ConceptAutocompleteDTO) return conceptResourceRef(item, lang);
        if (item instanceof PersonDTO p) return ref(p.getId(), "persons", p.displayName());
        if (item instanceof PhaseDTO p) {
            String label = p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : p.getIdentifier();
            return ref(p.getId(), "phases", label);
        }
        if (item instanceof SpatialUnitSummaryDTO s) return ref(s.getId(), "spatial-units", s.getName());
        if (item instanceof PlaceSuggestionDTO s) return ref(s.getId(), "spatial-units", s.getName());
        if (item instanceof SpatialUnitDTO s) return ref(s.getId(), "spatial-units", s.getName());
        if (item instanceof RecordingUnitSummaryDTO r) return ref(r.getId(), "recording-units", r.getFullIdentifier());
        if (item instanceof ActionUnitSummaryDTO a) return ref(a.getId(), "action-units", a.getName());
        if (item instanceof ActionUnitDTO a) return ref(a.getId(), "action-units", a.getName());
        // An action code is keyed by its code: that is also what a PATCH sends back for it.
        if (item instanceof ActionCodeDTO ac) return new ResourceRef(ac.getCode(), "action-codes", ac.getCode());
        if (item instanceof ContainerDTO c) return ref(c.getId(), "containers", c.getIdentifier());
        if (item instanceof SpecimenSummaryDTO s) return ref(s.getId(), "finds", s.getFullIdentifier());
        if (item instanceof AbstractEntityDTO e) return ref(e.getId(), answerType.toLowerCase(Locale.ROOT), null);
        return null;
    }

    private static ResourceRef ref(Long id, String resourceType, String label) {
        return new ResourceRef(String.valueOf(id), resourceType, label);
    }

    private ResourceRef conceptResourceRef(Object raw, String lang) {
        ConceptDTO concept = null;
        String preferredLabel = null;
        if (raw instanceof ConceptDTO c) {
            concept = c;
        } else if (raw instanceof ConceptAutocompleteDTO ac) {
            concept = ac.concept();
            if (ac.getConceptLabelToDisplay() != null) {
                preferredLabel = ac.getConceptLabelToDisplay().getLabel();
            }
            if ((preferredLabel == null || preferredLabel.isBlank()) && ac.getOriginalPrefLabel() != null) {
                preferredLabel = ac.getOriginalPrefLabel();
            }
        }
        if (concept == null) {
            return null;
        }
        String label = preferredLabel;
        if (label == null || label.isBlank()) {
            try {
                label = labelService.findLabelOf(concept, lang).getLabel();
            } catch (RuntimeException ignored) {
                label = null;
            }
        }
        if (label == null || label.isBlank()) {
            label = concept.getExternalId();
        }
        return new ResourceRef(String.valueOf(concept.getId()), CONCEPTS, label);
    }

    private static MeasurementRef toMeasurementRef(Object raw) {
        if (raw instanceof MeasurementAnswerDTO m) {
            String symbol = m.getUnit() != null ? m.getUnit().getSymbol() : null;
            return new MeasurementRef(m.getNumericValue(), symbol, m.getNormalizedValue(), m.getComment());
        }
        return null;
    }
}
