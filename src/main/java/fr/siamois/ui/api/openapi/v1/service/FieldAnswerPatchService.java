package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionCode;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.container.CustomFieldSelectMultipleContainer;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectOneRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.specimen.CustomFieldSelectMultipleSpecimen;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.*;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.FieldAnswerMaps;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

/**
 * Applies a PATCH's {@code answers} (field id → {@code AnswerInput}) to an entity DTO, for every
 * entity whose form goes through {@link FormService} (recording unit, find, phase, container).
 * <p>
 * One implementation of the "raw JSON value → typed form value" coercion for every field kind, so
 * the four PATCH endpoints accept the same set of fields: each used to carry its own partial copy.
 * System fields are written onto the DTO (including clearing one with {@code value: null}, which
 * {@link FormService#updateJpaEntityFromResponse} alone skips); answers to additional fields are
 * returned for the caller to persist with its service's {@code save(dto, additionalAnswers)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldAnswerPatchService {

    private final FormService formService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;
    private final PersonRepository personRepository;
    private final PersonMapper personMapper;
    private final ActionUnitRepository actionUnitRepository;
    private final ActionUnitSummaryMapper actionUnitSummaryMapper;
    private final ActionCodeRepository actionCodeRepository;
    private final ActionCodeMapper actionCodeMapper;
    private final SpatialUnitRepository spatialUnitRepository;
    private final SpatialUnitSummaryMapper spatialUnitSummaryMapper;
    private final RecordingUnitRepository recordingUnitRepository;
    private final RecordingUnitSummaryMapper recordingUnitSummaryMapper;
    private final PhaseRepository phaseRepository;
    private final PhaseMapper phaseMapper;
    private final ContainerRepository containerRepository;
    private final ContainerMapper containerMapper;
    private final SpecimenRepository specimenRepository;
    private final SpecimenSummaryMapper specimenSummaryMapper;
    private final UnitDefinitionMapper unitDefinitionMapper;

    /**
     * @param dto           the entity DTO to write system fields onto
     * @param effectiveForm the entity's effective form (system fields + the type's active additional
     *                      fields) — only fields it contains can be written
     * @param answers       field id → {@code AnswerInput} / legacy raw value, as the request carries them
     * @param projectId     the entity's project: references to phases, containers, finds and
     *                      recording units must belong to it
     * @return the answers to the additional fields this patch touched, to be persisted by the caller
     */
    public Map<CustomField, CustomFieldAnswerViewModel> apply(Object dto,
                                                              FormUiDto effectiveForm,
                                                              Map<String, ?> answers,
                                                              Long projectId) {
        return apply(dto, effectiveForm, answers, projectId, true);
    }

    /**
     * Same as {@link #apply(Object, FormUiDto, Map, Long)}, but a field absent from the form, of a
     * kind the API cannot write, or whose value does not parse is skipped (and logged) instead of
     * failing the request with a 400 — kept for the mobile client's legacy payloads. A reference to
     * an entity that does not exist still fails.
     */
    public Map<CustomField, CustomFieldAnswerViewModel> applyLenient(Object dto,
                                                                     FormUiDto effectiveForm,
                                                                     Map<String, ?> answers,
                                                                     Long projectId) {
        return apply(dto, effectiveForm, answers, projectId, false);
    }

    private Map<CustomField, CustomFieldAnswerViewModel> apply(Object dto,
                                                               FormUiDto effectiveForm,
                                                               Map<String, ?> answers,
                                                               Long projectId,
                                                               boolean strict) {
        if (answers == null || answers.isEmpty()) return Map.of();

        FieldSource fieldSource = new PanelFieldSource(effectiveForm);
        CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);

        Map<CustomField, CustomFieldAnswerViewModel> touchedAdditional = new HashMap<>();
        List<CustomField> clearedSystemFields = new ArrayList<>();
        for (Map.Entry<String, ?> entry : answers.entrySet()) {
            CustomField field;
            CustomFieldAnswerViewModel viewModel;
            try {
                field = requireField(fieldSource, entry.getKey());
                viewModel = viewModelOf(response, field);
                if (viewModel == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ non modifiable : " + field.getId());
                }
            } catch (ResponseStatusException e) {
                if (strict) throw e;
                log.debug("Réponse ignorée pour le champ {} : {}", entry.getKey(), e.getReason());
                continue;
            }

            Object raw = FieldAnswerMaps.unwrap(entry.getValue());
            if (raw == null && isMultiple(field)) {
                continue; // "values: null" = leave the multi-value field untouched ("values: []" clears it)
            }
            Object typed;
            if (raw == null) {
                typed = emptyValueOf(field);
            } else if (!strict && !isWritable(field)) {
                log.debug("Type de champ non pris en charge pour l'API v1, réponse ignorée : {}", field.getClass().getSimpleName());
                continue;
            } else {
                try {
                    typed = coerce(field, raw, projectId);
                } catch (InvalidAnswerValueException e) {
                    if (strict) throw e;
                    log.warn("Valeur ignorée pour le champ {} : {}", field.getId(), e.getReason());
                    continue;
                }
            }
            formService.applyTypedValueToAnswer(viewModel, typed);

            if (Boolean.TRUE.equals(field.getIsSystemField())) {
                if (raw == null) clearedSystemFields.add(field);
            } else {
                touchedAdditional.put(field, viewModel);
            }
        }

        formService.updateJpaEntityFromResponse(response, dto);
        clearedSystemFields.forEach(field -> formService.clearSystemField(dto, field));
        return touchedAdditional;
    }

    private static CustomField requireField(FieldSource fieldSource, String key) {
        long fieldId;
        try {
            fieldId = Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant de champ invalide : " + key);
        }
        CustomField field = fieldSource.findFieldById(fieldId);
        if (field == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ absent du formulaire de l'entité : " + key);
        }
        return field;
    }

    private static CustomFieldAnswerViewModel viewModelOf(CustomFormResponseViewModel response, CustomField field) {
        if (response.getAnswers() == null) return null;
        CustomFieldAnswerViewModel direct = response.getAnswers().get(field);
        if (direct != null) return direct;
        return response.getAnswers().entrySet().stream()
                .filter(e -> e.getKey() != null && Objects.equals(e.getKey().getId(), field.getId()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean isWritable(CustomField field) {
        Object f = Hibernate.unproxy(field);
        return isMultiple(field)
                || f instanceof CustomFieldText || f instanceof CustomFieldInteger || f instanceof CustomFieldDecimal
                || f instanceof CustomFieldDateTime || f instanceof CustomFieldMeasurement
                || f instanceof CustomFieldSelectOneFromFieldCode || f instanceof CustomFieldSelectOne
                || f instanceof CustomFieldSelectOnePerson || f instanceof CustomFieldSelectOneActionUnit
                || f instanceof CustomFieldSelectOneActionCode || f instanceof CustomFieldSelectOneSpatialUnit
                || f instanceof CustomFieldSelectOneRecordingUnit;
    }

    private static boolean isMultiple(CustomField field) {
        Object f = Hibernate.unproxy(field);
        return f instanceof CustomFieldSelectMultipleFromFieldCode
                || f instanceof CustomFieldSelectMultiple
                || f instanceof CustomFieldSelectMultiplePerson
                || f instanceof CustomFieldSelectMultipleSpatialUnitTree
                || f instanceof CustomFieldSelectMultipleRecordingUnit
                || f instanceof CustomFieldSelectMultiplePhase
                || f instanceof CustomFieldSelectMultipleContainer
                || f instanceof CustomFieldSelectMultipleSpecimen;
    }

    /** What the view model takes for "cleared": an empty collection for multi-value fields (their handlers skip null). */
    private Object emptyValueOf(CustomField field) {
        Object f = Hibernate.unproxy(field);
        if (f instanceof CustomFieldSelectMultipleSpatialUnitTree
                || f instanceof CustomFieldSelectMultipleRecordingUnit
                || f instanceof CustomFieldSelectMultiplePhase
                || f instanceof CustomFieldSelectMultipleContainer) {
            return new LinkedHashSet<>();
        }
        if (isMultiple(field)) return new ArrayList<>();
        return null;
    }

    // ========== Coercion: raw JSON value → the typed value FormService's view-model handlers take ==========

    private Object coerce(CustomField field, Object raw, Long projectId) {
        Object f = Hibernate.unproxy(field);
        try {
            if (f instanceof CustomFieldText) return String.valueOf(raw);
            if (f instanceof CustomFieldInteger) return raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw).trim());
            if (f instanceof CustomFieldDecimal) return toDouble(raw);
            if (f instanceof CustomFieldDateTime) return toOffsetDateTime(raw);
            if (f instanceof CustomFieldMeasurement measurement) return toMeasurement(raw, measurement);
            if (f instanceof CustomFieldSelectOneFromFieldCode || f instanceof CustomFieldSelectOne) {
                return one(raw, "Concept", conceptRepository::findById, conceptMapper::convert);
            }
            if (f instanceof CustomFieldSelectMultipleFromFieldCode || f instanceof CustomFieldSelectMultiple) {
                return new ArrayList<>(many(raw, "Concept", conceptRepository::findById, conceptMapper::convert));
            }
            if (f instanceof CustomFieldSelectOnePerson) return one(raw, "Personne", personRepository::findById, personMapper::convert);
            if (f instanceof CustomFieldSelectMultiplePerson) {
                return new ArrayList<>(many(raw, "Personne", personRepository::findById, personMapper::convert));
            }
            if (f instanceof CustomFieldSelectOneActionUnit) {
                return one(raw, "Projet", actionUnitRepository::findById, actionUnitSummaryMapper::convert);
            }
            if (f instanceof CustomFieldSelectOneActionCode) return actionCode(raw);
            if (f instanceof CustomFieldSelectOneSpatialUnit) {
                return one(raw, "Unité spatiale", spatialUnitRepository::findById, spatialUnitSummaryMapper::convert);
            }
            if (f instanceof CustomFieldSelectMultipleSpatialUnitTree) {
                return many(raw, "Unité spatiale", spatialUnitRepository::findById, spatialUnitSummaryMapper::convert);
            }
            if (f instanceof CustomFieldSelectOneRecordingUnit) {
                return one(raw, "Unité d'enregistrement", id -> recordingUnitRepository.findById(id)
                        .map(ru -> inProject(ru.getActionUnit(), projectId, "Unité d'enregistrement", id, ru)), recordingUnitSummaryMapper::convert);
            }
            if (f instanceof CustomFieldSelectMultipleRecordingUnit) {
                return many(raw, "Unité d'enregistrement", id -> recordingUnitRepository.findById(id)
                        .map(ru -> inProject(ru.getActionUnit(), projectId, "Unité d'enregistrement", id, ru)), recordingUnitSummaryMapper::convert);
            }
            if (f instanceof CustomFieldSelectMultiplePhase) {
                return many(raw, "Phase", id -> phaseRepository.findById(id)
                        .map(p -> inProject(p.getActionUnit(), projectId, "Phase", id, p)), phaseMapper::convert);
            }
            if (f instanceof CustomFieldSelectMultipleContainer) {
                return many(raw, "Contenant", id -> containerRepository.findById(id)
                        .map(c -> inProject(c.getActionUnit(), projectId, "Contenant", id, c)), containerMapper::convert);
            }
            if (f instanceof CustomFieldSelectMultipleSpecimen) {
                return new ArrayList<>(many(raw, "Mobilier", id -> specimenRepository.findById(id)
                        .map(s -> inProject(s.getActionUnit(), projectId, "Mobilier", id, s)), specimenSummaryMapper::convert));
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            // Only a value that does not parse; any other failure is a bug and must surface.
            throw new InvalidAnswerValueException("Valeur invalide pour le champ " + field.getId() + " : " + e.getMessage(), e);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ce type de champ n'est pas modifiable via l'API : " + f.getClass().getSimpleName());
    }

    private static <E> E inProject(ActionUnit actionUnit, Long projectId, String label, long id, E entity) {
        if (projectId != null && actionUnit != null && !Objects.equals(actionUnit.getId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " hors projet : " + id);
        }
        return entity;
    }

    private static <E, D> D one(Object raw, String label, Function<Long, Optional<E>> find, Function<E, D> convert) {
        long id = requireLongId(raw, label);
        return find.apply(id).map(convert)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " introuvable : " + id));
    }

    /** A LinkedHashSet, keeping the picked order: the Set-based view-model handlers require a Set. */
    private static <E, D> Set<D> many(Object raw, String label, Function<Long, Optional<E>> find, Function<E, D> convert) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        Set<D> out = new LinkedHashSet<>();
        for (Object item : items) {
            out.add(one(item, label, find, convert));
        }
        return out;
    }

    private Object actionCode(Object raw) {
        String code = raw instanceof Map<?, ?> m && m.get("id") != null ? String.valueOf(m.get("id")) : String.valueOf(raw);
        ActionCode actionCode = actionCodeRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code d'action introuvable : " + code));
        return actionCodeMapper.convert(actionCode);
    }

    private static Double toDouble(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(raw).trim().replace(',', '.'));
    }

    /** ISO date-time, or a bare ISO date (what the React date picker sends) taken at midnight UTC. */
    private static OffsetDateTime toOffsetDateTime(Object raw) {
        if (raw instanceof OffsetDateTime odt) return odt;
        String s = String.valueOf(raw).trim();
        if (s.length() == 10) return LocalDate.parse(s).atStartOfDay().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(s);
    }

    private MeasurementAnswerDTO toMeasurement(Object raw, CustomFieldMeasurement field) {
        if (!(raw instanceof Map<?, ?>) && !(raw instanceof Number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mesure invalide : objet { numericValue, comment? } ou nombre attendu");
        }
        MeasurementAnswerDTO dto = new MeasurementAnswerDTO();
        if (raw instanceof Map<?, ?> map) {
            Object numeric = map.get("numericValue");
            if (numeric != null && !String.valueOf(numeric).isBlank()) dto.setNumericValue(toDouble(numeric));
            Object comment = map.get("comment");
            if (comment != null && !String.valueOf(comment).isBlank()) dto.setComment(String.valueOf(comment).trim());
        } else {
            dto.setNumericValue(toDouble(raw));
        }
        // The unit is the field's own (zInf, zSup, …), never the client's.
        if (field.getUnit() != null) {
            dto.setUnit(unitDefinitionMapper.convert(field.getUnit()));
        } else {
            UnitDefinitionDTO unit = dto.getUnit();
            if (unit != null && (unit.getId() == null || unit.getId() <= 0)) unit.setId(null);
        }
        return dto;
    }

    private static long requireLongId(Object raw, String label) {
        Long id = extractLongId(raw);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu pour " + label.toLowerCase(Locale.ROOT));
        }
        return id;
    }

    private static Long extractLongId(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id == null) id = m.get("resourceId");
            if (id == null) id = m.get("conceptId");
            return extractLongId(id);
        }
        if (raw instanceof String s && !s.isBlank()) return Long.parseLong(s.trim());
        return null;
    }

    /** A value that does not parse for its field — a 400, or skipped by {@link #applyLenient}. */
    private static final class InvalidAnswerValueException extends ResponseStatusException {
        InvalidAnswerValueException(String reason, Throwable cause) {
            super(HttpStatus.BAD_REQUEST, reason, cause);
        }
    }
}
