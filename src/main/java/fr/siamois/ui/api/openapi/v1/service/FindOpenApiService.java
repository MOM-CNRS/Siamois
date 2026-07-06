package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.ProfilePermissionService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.find.FindCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Création et mise à jour OpenAPI des mobiliers (spécimens) : formulaire custom par type + institution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FindOpenApiService {

    private final SpecimenService specimenService;
    private final RecordingUnitService recordingUnitService;
    private final FormService formService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;
    private final ConversionService conversionService;
    private final ProfilePermissionService profilePermissionService;
    private final PersonService personService;
    private final PersonMapper personMapper;
    private final ActionUnitService actionUnitService;
    private final SpatialUnitService spatialUnitService;
    private final FindOpenApiMapper findOpenApiMapper;

    @Transactional
    public FindResource createFind(FindCreateRequest request,
                                   PersonDTO personDto,
                                   Set<Long> accessibleInstitutionIds,
                                   String lang) {
        String recordingUnitKey = OpenApiParamIds.requireNonBlank(
                request.getRecordingUnitId(), "recordingUnitId");
        long typeConceptId = OpenApiParamIds.parseRequiredConceptId(
                request.getSpecimenTypeConceptId(), "specimenTypeConceptId");

        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, accessibleInstitutionIds, null);
        InstitutionDTO institution = ru.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UE sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de mobilier non autorisée sur cette UE");
        }

        Concept typeConcept = conceptRepository.findById(typeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de mobilier introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        SpecimenDTO shell = new SpecimenDTO();
        shell.setRecordingUnit(new RecordingUnitSummaryDTO(ru));
        shell.setCreatedByInstitution(institution);
        shell.setType(typeDto);
        shell.setCreatedBy(personDto);
        shell.setAuthors(new ArrayList<>(List.of(personDto)));
        shell.setCollectors(new ArrayList<>(List.of(personDto)));
        shell.setCollectionDate(OffsetDateTime.now(ZoneOffset.UTC));
        shell.setValidated(ValidationStatus.INCOMPLETE);

        Map<String, Object> fieldAnswers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution);

        if (customForm == null) {
            if (!fieldAnswers.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Aucun formulaire personnalisé pour ce type de mobilier : impossible d'appliquer fieldAnswers");
            }
            SpecimenDTO created = specimenService.save(shell);
            return findOpenApiMapper.toResource(created);
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        CustomFormResponseViewModel response = formService.initOrReuseResponse(null, shell, fieldSource, true);
        mergeFieldAnswers(response, fieldSource, fieldAnswers);
        formService.updateJpaEntityFromResponse(response, shell);

        SpecimenDTO created = specimenService.save(shell);
        return findOpenApiMapper.toResource(created);
    }

    @Transactional
    public FindResource patchFind(long specimenId,
                                  FindPatchRequest request,
                                  PersonDTO personDto,
                                  Set<Long> accessibleInstitutionIds,
                                  String lang) {
        SpecimenDTO dto = specimenService.findAccessibleById(specimenId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }
        RecordingUnitSummaryDTO ruSum = dto.getRecordingUnit();
        if (ruSum == null || ruSum.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans unité d'enregistrement");
        }
        RecordingUnitDTO ru = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                ruSum.getId(), accessibleInstitutionIds);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }

        Map<String, Object> answers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        if (answers.isEmpty()) {
            return findOpenApiMapper.toResource(dto);
        }

        ConceptDTO specimenType = dto.getType();
        if (specimenType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans type");
        }
        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(specimenType, institution);
        if (customForm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun formulaire personnalisé pour ce type de mobilier : impossible d'appliquer fieldAnswers");
        }

        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            mergeFieldAnswers(response, fieldSource, answers);
            formService.updateJpaEntityFromResponse(response, dto);
        });

        SpecimenDTO saved = specimenService.save(dto);
        return findOpenApiMapper.toResource(saved);
    }

    @Transactional
    public void deleteFind(long specimenId,
                           PersonDTO personDto,
                           Set<Long> accessibleInstitutionIds,
                           String lang) {
        SpecimenDTO dto = specimenService.findAccessibleById(specimenId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }
        RecordingUnitSummaryDTO ruSum = dto.getRecordingUnit();
        if (ruSum == null || ruSum.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans unité d'enregistrement");
        }
        RecordingUnitDTO ru = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                ruSum.getId(), accessibleInstitutionIds);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression non autorisée");
        }

        try {
            specimenService.deleteSpecimenById(specimenId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    private void mergeFieldAnswers(CustomFormResponseViewModel response,
                                   FieldSource fieldSource,
                                   Map<String, Object> fieldAnswers) {
        if (fieldAnswers == null || fieldAnswers.isEmpty() || response.getAnswers() == null) {
            return;
        }
        for (Map.Entry<String, Object> e : fieldAnswers.entrySet()) {
            mergeOneFieldAnswer(response, fieldSource, e.getKey(), e.getValue());
        }
    }

    private void mergeOneFieldAnswer(CustomFormResponseViewModel response,
                                     FieldSource fieldSource,
                                     String key,
                                     Object value) {
        long fieldId;
        try {
            fieldId = Long.parseLong(key);
        } catch (NumberFormatException ex) {
            log.debug("Clé de champ ignorée (non numérique): {}", key);
            return;
        }
        CustomField field = fieldSource.findFieldById(fieldId);
        if (field == null) {
            log.debug("Champ id={} absent du formulaire effectif", fieldId);
            return;
        }
        CustomFieldAnswerViewModel vm = findViewModelForField(response.getAnswers(), field);
        if (vm == null) {
            return;
        }
        Object typed;
        try {
            typed = coerceAnswerValue(field, value);
        } catch (ResponseStatusException rex) {
            throw rex;
        } catch (RuntimeException ex) {
            log.warn("Valeur ignorée pour champ id={} ({}): {}", fieldId, field.getClass().getSimpleName(), ex.toString());
            return;
        }
        if (typed == null && value != null) {
            log.debug("Valeur non convertible pour champ id={} type {}", fieldId, field.getClass().getSimpleName());
            return;
        }
        formService.applyTypedValueToAnswer(vm, typed);
    }

    private Object coerceAnswerValue(CustomField field, Object raw) {
        if (raw == null) return null;
        if (field instanceof CustomFieldInteger) return coerceInteger(raw);
        if (field instanceof CustomFieldText) return String.valueOf(raw);
        if (field instanceof CustomFieldDateTime) return coerceDateTime(raw);
        if (field instanceof CustomFieldSelectOneFromFieldCode) return coerceConcept(raw);
        if (field instanceof CustomFieldSelectOnePerson) return coercePerson(raw);
        if (field instanceof CustomFieldSelectOneActionUnit) return coerceActionUnit(raw);
        if (field instanceof CustomFieldSelectOneSpatialUnit) return coerceSpatialUnit(raw);
        log.debug("Type de champ non pris en charge pour l'API v1: {}", field.getClass().getSimpleName());
        return null;
    }

    private Object coerceInteger(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private Object coerceDateTime(Object raw) {
        if (raw instanceof OffsetDateTime odt) {
            return odt;
        }
        if (raw instanceof String s) {
            return OffsetDateTime.parse(s);
        }
        throw new IllegalArgumentException("Format datetime attendu (chaîne ISO-8601 ou OffsetDateTime)");
    }

    private Object coerceConcept(Object raw) {
        long conceptId = requireLongId(raw, "concept");
        Concept c = conceptRepository.findById(conceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concept introuvable: " + conceptId));
        return conceptMapper.convert(c);
    }

    private Object coercePerson(Object raw) {
        long personId = requireLongId(raw, "personne");
        Person p = personService.findById(personId);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personne introuvable: " + personId);
        }
        return personMapper.convert(p);
    }

    private Object coerceActionUnit(Object raw) {
        long actionId = requireLongId(raw, "unité d'action");
        ActionUnitDTO au = actionUnitService.findById(actionId);
        if (au == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet introuvable: " + actionId);
        }
        return actionUnitSummaryFromFull(au);
    }

    private Object coerceSpatialUnit(Object raw) {
        long suId = requireLongId(raw, "unité spatiale");
        try {
            return new SpatialUnitSummaryDTO(spatialUnitService.findById(suId));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité spatiale introuvable: " + suId);
        }
    }

    private static ActionUnitSummaryDTO actionUnitSummaryFromFull(ActionUnitDTO au) {
        ActionUnitSummaryDTO s = new ActionUnitSummaryDTO(au);
        s.setId(au.getId());
        s.setName(au.getName());
        s.setFullIdentifier(au.getFullIdentifier());
        s.setIdentifier(au.getIdentifier());
        s.setRecordingUnitIdentifierFormat(au.getRecordingUnitIdentifierFormat());
        s.setType(au.getType());
        s.setBeginDate(au.getBeginDate());
        s.setEndDate(au.getEndDate());
        s.setMinRecordingUnitCode(au.getMinRecordingUnitCode());
        s.setMaxRecordingUnitCode(au.getMaxRecordingUnitCode());
        return s;
    }

    private static long requireLongId(Object raw, String label) {
        Long id = extractLongId(raw);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu pour " + label);
        }
        return id;
    }

    private static Long extractLongId(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) {
                return n.longValue();
            }
            if (id instanceof String s && !s.isBlank()) {
                return Long.parseLong(s);
            }
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }

    private static CustomFieldAnswerViewModel findViewModelForField(Map<CustomField, CustomFieldAnswerViewModel> answers,
                                                                    CustomField field) {
        CustomFieldAnswerViewModel direct = answers.get(field);
        if (direct != null) {
            return direct;
        }
        if (field == null || field.getId() == null) {
            return null;
        }
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> en : answers.entrySet()) {
            CustomField k = en.getKey();
            if (k != null && field.getId().equals(k.getId())) {
                return en.getValue();
            }
        }
        return null;
    }
}
