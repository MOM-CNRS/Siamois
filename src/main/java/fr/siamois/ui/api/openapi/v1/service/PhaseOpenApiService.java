package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
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
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.phase.PhaseCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Détail/écriture d'une phase ({@code GET}/{@code PATCH}/{@code POST /api/v1/phases}...) — le
 * pendant, pour Phase, de {@link FindOpenApiService}. Plus simple que Find/RecordingUnit : Phase
 * n'a jamais de {@code CustomFieldAnswer}, donc ni {@code FormService} ni
 * {@code CustomFormResponseViewModel} n'entrent en jeu ici — {@link PhaseAnswersProjector} (lecture)
 * et {@link FieldAnswerPatchService} (écriture) ci-dessous sont tous deux de simples lectures/écritures
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
    private final ValidationOpenApiService validationOpenApiService;
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final FieldAnswerWireService fieldAnswerWireService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final EffectiveFormResolver effectiveFormResolver;

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
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, phase.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo, phase.getActionUnit().getId());
        boolean answersChange = request.getAnswers() != null && !request.getAnswers().isEmpty();
        boolean statusChange = ValidationOpenApiService.changes(phase.getValidated(), request.getValidated());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((answersChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(phase.getValidated(), request.getValidated(), canEdit, canValidate);

        if (answersChange || !statusChange) {
            Long projectId = phase.getActionUnit().getId();
            OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
                // The same effective form the phase-types catalog lays out (system + additional fields).
                FormUiDto form = effectiveFormResolver.resolveEffectiveForm(Phase.DETAILS_FORM, projectId,
                        ConfigurableTable.PHASE, phase.getType() != null ? phase.getType().getId() : null);
                Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                        fieldAnswerPatchService.apply(phase, form, request.getAnswers(), projectId);
                return phaseService.save(phase, additionalAnswers);
            });
        }
        // After the save: save() writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(fr.siamois.domain.models.phase.Phase.class, id, request.getValidated(), personDto);
        return getPhaseById(id, personDto, accessibleInstitutionIds, lang);
    }

    private PhaseResource toResourceWithPermissionsAndAnswers(PhaseDTO phase, PersonDTO personDto, String lang) {
        UserInfo userInfo = new UserInfo(phase.getActionUnit().getCreatedByInstitution(), personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, phase.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES);

        PhaseListProjectionService.PhaseListProjection projection = phaseListProjectionService.buildOne(phase, lang);
        PhaseResource resource = phaseOpenApiMapper.toResource(phase, lang, projection.resolvedLabels());
        Map<String, Object> answers = new java.util.LinkedHashMap<>(java.util.Objects.requireNonNullElse(projection.answersFor(phase.getId()), Map.of()));
        // The projection reads system fields only; the type's additional fields live in their own answer rows.
        answers.putAll(OpenApiExecutionContext.callWithUserInfo(userInfo, () -> fieldAnswerWireService.additionalAnswers(
                customFieldAnswerService.loadAdditionalFieldAnswers(phase), lang)));
        resource.setAnswers(answers);
        resource.setPermissions(ProjectResourcePermissions.of(canEdit)
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, phase.getActionUnit().getId())));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
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
