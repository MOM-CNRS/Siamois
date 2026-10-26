package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.models.study.Study;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.study.StudyService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.Kind;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.ProjectContext;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.StudyOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.study.StudyCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.study.StudyPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.study.StudyResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Détail/écriture d'une étude ({@code GET}/{@code PATCH}/{@code POST /api/v1/studies}...), pendant de
 * {@link ContainerOpenApiService}. Les champs système sont lus par {@link StudyAnswersProjector} et écrits
 * par {@link FieldAnswerPatchService} sur le {@link StudyDTO} ; les champs additionnels du type vivent dans
 * leurs propres lignes {@code CustomFieldAnswer}. Les liens aux UE et au mobilier sont des champs de
 * référence multiples, résolus en entités par {@code StudyService}.
 */
@Service
@RequiredArgsConstructor
public class StudyOpenApiService {

    private final StudyService studyService;
    private final ProjectTypedCreationService projectTypedCreationService;
    private final ProfilePermissionService profilePermissionService;
    private final StudyOpenApiMapper studyOpenApiMapper;
    private final StudyListProjectionService studyListProjectionService;
    private final MultiValueAnswers multiValueAnswers;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final FieldAnswerWireService fieldAnswerWireService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final EffectiveFormResolver effectiveFormResolver;

    @Transactional(readOnly = true)
    public StudyResource getStudyById(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        return loadStudy(id, personDto, accessibleInstitutionIds, lang);
    }

    private StudyResource loadStudy(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        return toResourceWithPermissionsAndAnswers(requireAccessibleStudy(id, personDto, accessibleInstitutionIds), personDto, lang);
    }

    @Transactional
    public StudyResource createStudy(StudyCreateRequest request, PersonDTO personDto,
                                             Set<Long> accessibleInstitutionIds, String lang) {
        ProjectContext project = projectTypedCreationService.resolveProject(
                request.getProjectId(), personDto, accessibleInstitutionIds, lang);
        projectTypedCreationService.requirePermission(project, "Création d'étude non autorisée sur ce projet",
                PermissionConstants.INSTANCE_EDIT_STUDIES,
                PermissionConstants.ORGANIZATION_EDIT_STUDIES,
                PermissionConstants.PROJECT_EDIT_STUDIES);
        StudyDTO shell = projectTypedCreationService.newEntity(Kind.STUDY, project,
                projectTypedCreationService.resolveType(request.getTypeId(), Kind.STUDY));
        IdentifierPatch.applyChosen(request.getIdentifier(), shell::setIdentifier,
                () -> studyService.identifierAlreadyExistInAction(shell));

        StudyDTO saved = OpenApiExecutionContext.callWithUserInfo(project.userInfo(), () -> studyService.save(shell));
        return toResourceWithPermissionsAndAnswers(saved, personDto, lang);
    }

    @Transactional
    public StudyResource patchStudy(long id, StudyPatchRequest request, PersonDTO personDto,
                                            Set<Long> accessibleInstitutionIds, String lang) {
        StudyDTO study = requireAccessibleStudy(id, personDto, accessibleInstitutionIds);
        InstitutionDTO institution = study.getActionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, study.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_STUDIES,
                PermissionConstants.ORGANIZATION_EDIT_STUDIES,
                PermissionConstants.PROJECT_EDIT_STUDIES);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo, study.getActionUnit().getId());
        boolean answersChange = request.getAnswers() != null && !request.getAnswers().isEmpty();
        String newIdentifier = IdentifierPatch.requested(request.getIdentifier());
        boolean identifierChange = IdentifierPatch.changes(newIdentifier, study.getIdentifier());
        boolean statusChange = ValidationOpenApiService.changes(study.getValidationStatus(), request.getValidationStatus());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((answersChange || identifierChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(study.getValidationStatus(), request.getValidationStatus(), canEdit, canValidate);

        if (identifierChange) {
            study.setIdentifier(newIdentifier);
            IdentifierPatch.requireFree(studyService.identifierAlreadyExistInAction(study));
        }
        if (answersChange || identifierChange || !statusChange) {
            Long projectId = study.getActionUnit().getId();
            OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
                // The same effective form the study-types catalog lays out (system + additional fields).
                FormUiDto form = effectiveFormResolver.resolveEffectiveForm(projectId,
                        ConfigurableTable.STUDY, study.getType() != null ? study.getType().getId() : null);
                Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                        fieldAnswerPatchService.apply(study, form, request.getAnswers(), projectId);
                return studyService.save(study, additionalAnswers);
            });
        }
        // After the save: save() writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(fr.siamois.domain.models.study.Study.class, id, request.getValidationStatus(), personDto);
        return loadStudy(id, personDto, accessibleInstitutionIds, lang);
    }

    private StudyResource toResourceWithPermissionsAndAnswers(StudyDTO study, PersonDTO personDto, String lang) {
        UserInfo userInfo = new UserInfo(study.getActionUnit().getCreatedByInstitution(), personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, study.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_STUDIES,
                PermissionConstants.ORGANIZATION_EDIT_STUDIES,
                PermissionConstants.PROJECT_EDIT_STUDIES);

        StudyListProjectionService.StudyListProjection projection = studyListProjectionService.buildOne(study, lang);
        StudyResource resource = studyOpenApiMapper.toResource(study, lang, projection.resolvedLabels());
        Map<String, Object> answers = new java.util.LinkedHashMap<>(java.util.Objects.requireNonNullElse(projection.answersFor(study.getId()), Map.of()));
        // The projection reads system fields only; the type's additional fields live in their own answer rows.
        answers.putAll(OpenApiExecutionContext.callWithUserInfo(userInfo, () -> fieldAnswerWireService.additionalAnswers(
                customFieldAnswerService.loadAdditionalFieldAnswers(study), lang)));
        // The additional answers join after the projection's own shaping: cut them the same way.
        resource.setAnswers(multiValueAnswers.shapeOne(Study.class, study.getId(), answers, null,
                ValuesLimit.forDetail(), lang));
        resource.setPermissions(ProjectResourcePermissions.of(canEdit)
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, study.getActionUnit().getId()))
                .withManageSettings(profilePermissionService.hasProjectSettingsPermission(userInfo, study.getActionUnit().getId()))
                .withDelete(profilePermissionService.hasDeleteEntitiesPermission(userInfo, study.getActionUnit().getId())));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
    }

    /** The study, if the caller can see it — 404 otherwise, whether it doesn't exist or is out of scope. */
    public StudyDTO requireAccessible(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        return requireAccessibleStudy(id, personDto, accessibleInstitutionIds);
    }

    private StudyDTO requireAccessibleStudy(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        StudyDTO study = studyService.findById(id);
        ActionUnitSummaryDTO project = study == null ? null : study.getActionUnit();
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Étude introuvable");
        }
        if (!canSee(project, personDto, accessibleInstitutionIds)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Étude introuvable ou hors périmètre");
        }
        return study;
    }

    /** The caller sees a project when its organization is in the token's scope and its profile lets it view it. */
    private boolean canSee(ActionUnitSummaryDTO project, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        InstitutionDTO institution = project.getCreatedByInstitution();
        boolean inScope = institution != null && institution.getId() != null
                && accessibleInstitutionIds != null && accessibleInstitutionIds.contains(institution.getId());
        return inScope && profilePermissionService.canViewProject(personDto, institution, project.getId());
    }

    /** Previous/next study in the same project ({@code GET /api/v1/studies/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        StudyDTO study = requireAccessibleStudy(id, personDto, accessibleInstitutionIds);
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.STUDY,
                study.getActionUnit().getId(), study.getId());
    }

    /**
     * Supprime l'étude sans relation avec une autre entité (409 sinon). Droit d'édition requis sur le projet.
     */
    @Transactional
    public void deleteStudy(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        StudyDTO dto = requireAccessibleStudy(id, personDto, accessibleInstitutionIds);
        InstitutionDTO institution = dto.getActionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasDeleteEntitiesPermission(userInfo, dto.getActionUnit().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression non autorisée");
        }
        try {
            OpenApiExecutionContext.runWithUserInfo(userInfo, () -> studyService.deleteById(id));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }
}
