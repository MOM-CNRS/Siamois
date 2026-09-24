package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.phase.PhaseCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Détail/écriture d'une phase ({@code GET}/{@code PATCH}/{@code POST /api/v1/phases}...) — le
 * pendant, pour Phase, de {@link FindOpenApiService}. Plus simple que Find/RecordingUnit : Phase
 * n'a jamais de {@code CustomFieldAnswer}, donc ni {@code FormService} ni
 * {@code CustomFormResponseViewModel} n'entrent en jeu ici — {@link PhaseAnswersProjector} (lecture)
 * et {@link #applyAnswerPatch} (écriture) ci-dessous sont tous deux de simples lectures/écritures
 * réflexives sur {@link PhaseDTO}, exactement comme {@code ProjectApiService} le fait pour Projet.
 */
@Service
@RequiredArgsConstructor
public class PhaseOpenApiService {

    private final PhaseService phaseService;
    private final ActionUnitService actionUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;
    private final PhaseOpenApiMapper phaseOpenApiMapper;
    private final PhaseListProjectionService phaseListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;

    @Transactional(readOnly = true)
    public PhaseResource getPhaseById(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        PhaseDTO phase = requireAccessiblePhase(id, personDto, accessibleInstitutionIds);
        return toResourceWithPermissionsAndAnswers(phase, personDto, lang);
    }

    @Transactional
    public PhaseResource createPhase(PhaseCreateRequest request, PersonDTO personDto,
                                     Set<Long> accessibleInstitutionIds, String lang) {
        String projectId = requireNonBlank(request.getProjectId(), "projectId");
        String typeId = requireNonBlank(request.getTypeId(), "typeId");

        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectId, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasProjectPermission(userInfo, au.getId(),
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de phase non autorisée sur ce projet");
        }

        Concept typeConcept = conceptService.findById(parseLong(typeId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de phase introuvable"));

        PhaseDTO shell = new PhaseDTO();
        shell.setActionUnit(new ActionUnitSummaryDTO(au));
        shell.setType(conceptMapper.convert(typeConcept));
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            shell.setTitle(request.getTitle().trim());
        }

        PhaseDTO saved = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> phaseService.save(shell));
        return toResourceWithPermissionsAndAnswers(saved, personDto, lang);
    }

    @Transactional
    public PhaseResource patchPhase(long id, PhasePatchRequest request, PersonDTO personDto,
                                    Set<Long> accessibleInstitutionIds, String lang) {
        PhaseDTO phase = requireAccessiblePhase(id, personDto, accessibleInstitutionIds);
        InstitutionDTO institution = phase.getActionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasProjectPermission(userInfo, phase.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }

        applyAnswerPatch(phase, request.getAnswers());

        PhaseDTO saved = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> phaseService.save(phase));
        return toResourceWithPermissionsAndAnswers(saved, personDto, lang);
    }

    private PhaseResource toResourceWithPermissionsAndAnswers(PhaseDTO phase, PersonDTO personDto, String lang) {
        UserInfo userInfo = new UserInfo(phase.getActionUnit().getCreatedByInstitution(), personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, phase.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES);

        PhaseListProjectionService.PhaseListProjection projection = phaseListProjectionService.buildOne(phase, lang);
        PhaseResource resource = phaseOpenApiMapper.toResource(phase, lang, projection.resolvedLabels());
        resource.setAnswers(projection.answersFor(phase.getId()));
        resource.setPermissions(ProjectResourcePermissions.of(canEdit));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
    }

    /**
     * Écrit {@code PhasePatchRequest.answers} sur {@code dto} par réflexion — miroir en écriture de
     * {@link PhaseAnswersProjector}, même sous-ensemble de types de champ pris en charge : TEXT,
     * INTEGER, SELECT_ONE_FROM_FIELD_CODE, SELECT_MULTIPLE_FROM_FIELD_CODE.
     */
    private void applyAnswerPatch(PhaseDTO dto, Map<String, AnswerInput> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, AnswerInput> entry : answers.entrySet()) {
            String fieldId = entry.getKey();
            CustomField field = PhaseAnswersProjector.fieldById(fieldId);
            if (field == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de formulaire inconnu : " + fieldId);
            }
            AnswerInput input = entry.getValue();

            if (field instanceof CustomFieldSelectMultipleFromFieldCode) {
                if (input == null || input.values() == null) {
                    continue;
                }
                Set<ConceptDTO> concepts = input.values().stream()
                        .map(this::coerceConceptId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                writeAnswerBinding(dto, field, concepts);
                continue;
            }

            Object raw = input == null ? null : input.value();
            Object coerced = raw == null ? null : coerceScalarAnswer(field, raw);
            writeAnswerBinding(dto, field, coerced);
        }
    }

    private Object coerceScalarAnswer(CustomField field, Object raw) {
        if (field instanceof CustomFieldText) return String.valueOf(raw);
        if (field instanceof CustomFieldInteger) return coerceIntegerAnswer(raw);
        if (field instanceof CustomFieldSelectOneFromFieldCode) return coerceConceptId(raw);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ce champ n'est pas modifiable via l'API : " + field.getClass().getSimpleName());
    }

    private static Integer coerceIntegerAnswer(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entier attendu, reçu : " + raw);
        }
    }

    private ConceptDTO coerceConceptId(Object raw) {
        long conceptId = extractLongId(raw);
        Concept concept = conceptService.findById(conceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept introuvable : " + conceptId));
        return conceptMapper.convert(concept);
    }

    private static long extractLongId(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // falls through
            }
        }
        if (raw instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id == null) id = m.get("resourceId");
            if (id instanceof Number n) return n.longValue();
            if (id instanceof String s) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // falls through
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu, reçu : " + raw);
    }

    private static void writeAnswerBinding(PhaseDTO dto, CustomField field, Object value) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ sans liaison : " + field.getId());
        }
        java.beans.PropertyDescriptor descriptor = org.springframework.beans.BeanUtils.getPropertyDescriptor(PhaseDTO.class, binding);
        java.lang.reflect.Method setter = descriptor == null ? null : descriptor.getWriteMethod();
        if (setter == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ non modifiable : " + field.getId());
        }
        try {
            setter.invoke(dto, value);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valeur invalide pour " + field.getId() + " : " + e.getMessage());
        }
    }

    /**
     * Phase par id numérique, avec vérification d'appartenance institutionnelle et de droit de
     * consultation (même règle que le projet parent — {@code canViewProject}, Phase n'a pas de
     * droit de lecture propre). 404 aussi bien si la phase n'existe pas que si elle existe mais est
     * hors périmètre — jamais de distinction observable de l'extérieur.
     */
    private PhaseDTO requireAccessiblePhase(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        PhaseDTO phase = phaseService.findById(id);
        if (phase == null || phase.getActionUnit() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase introuvable");
        }
        InstitutionDTO institution = phase.getActionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null
                || accessibleInstitutionIds == null || !accessibleInstitutionIds.contains(institution.getId())
                || !profilePermissionService.canViewProject(personDto, institution, phase.getActionUnit().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase introuvable ou hors périmètre");
        }
        return phase;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " est requis");
        }
        return value;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu : " + value);
        }
    }

    /**
     * The access check every phase-scoped endpoint starts with (404 when out of scope) — public for
     * the endpoints that page something else under a phase (its recording units).
     */
    public PhaseDTO requireAccessible(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        return requireAccessiblePhase(id, personDto, accessibleInstitutionIds);
    }

    /** Previous/next phase in the same project ({@code GET /api/v1/phases/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        PhaseDTO phase = requireAccessiblePhase(id, personDto, accessibleInstitutionIds);
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.PHASE,
                phase.getActionUnit().getId(), phase.getId());
    }
}
