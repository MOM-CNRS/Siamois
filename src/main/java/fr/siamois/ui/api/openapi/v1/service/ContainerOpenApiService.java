package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.ui.api.openapi.v1.resource.document.EntityDocumentCounts;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.models.container.Container;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.ProjectContext;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
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
 * Détail/écriture d'un contenant ({@code GET}/{@code PATCH}/{@code POST /api/v1/containers}...) —
 * le pendant, pour Container, de {@link PhaseOpenApiService}. Comme Phase, aucun
 * {@code CustomFieldAnswer} n'entre en jeu ici — {@link ContainerAnswersProjector} (lecture) et
 * {@link FieldAnswerPatchService} (écriture) sont tous deux de simples lectures/écritures réflexives sur
 * {@link ContainerDTO}. Un type de champ de plus que Phase à gérer : {@code CustomFieldMeasurement}
 * (longueur/largeur/hauteur/poids) et {@code CustomFieldSelectOneSpatialUnit} (lieu) — coercion
 * calquée sur {@code RecordingUnitOpenApiService}'s own.
 */
@Service
@RequiredArgsConstructor
public class ContainerOpenApiService {

    private static final String TYPE_NOT_FOUND = "Type de contenant introuvable";

    private final ContainerService containerService;
    private final ProjectTypedCreationService projectTypedCreationService;
    private final ProfilePermissionService profilePermissionService;
    private final ContainerOpenApiMapper containerOpenApiMapper;
    private final ContainerListProjectionService containerListProjectionService;
    private final MultiValueAnswers multiValueAnswers;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final FieldAnswerWireService fieldAnswerWireService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final EffectiveFormResolver effectiveFormResolver;
    private final DocumentService documentService;

    @Transactional(readOnly = true)
    public ContainerResource getContainerById(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        return loadContainer(id, personDto, accessibleInstitutionIds, lang);
    }

    private ContainerResource loadContainer(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        ContainerDTO container = requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
        ContainerResource resource = toResourceWithPermissionsAndAnswers(container, personDto, lang);
        resource.setCount(new EntityDocumentCounts(documentService.countLinkedTo(DocumentLinkKind.CONTAINER, id)));
        return resource;
    }

    @Transactional
    public ContainerResource createContainer(ContainerCreateRequest request, PersonDTO personDto,
                                             Set<Long> accessibleInstitutionIds, String lang) {
        ProjectContext project = projectTypedCreationService.resolveProject(
                request.getProjectId(), personDto, accessibleInstitutionIds, lang);
        projectTypedCreationService.requirePermission(project, "Création de contenant non autorisée sur ce projet",
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS);
        ContainerDTO shell = newContainer(project, projectTypedCreationService.resolveType(request.getTypeId(), TYPE_NOT_FOUND));
        IdentifierPatch.applyChosen(request.getIdentifier(), shell::setIdentifier,
                () -> containerService.identifierAlreadyExistInAction(shell));

        ContainerDTO saved = OpenApiExecutionContext.callWithUserInfo(project.userInfo(), () -> containerService.save(shell));
        return toResourceWithPermissionsAndAnswers(saved, personDto, lang);
    }

    /**
     * The identifier a new container of this type would be given in this project, without consuming the
     * counter ({@code GET /api/v1/identifier-suggestions?entityType=containers}) — shown, modifiable, in the creation form.
     */
    @Transactional(readOnly = true)
    public String suggestIdentifier(String projectId, String typeId, PersonDTO personDto,
                                    Set<Long> accessibleInstitutionIds, String lang) {
        return projectTypedCreationService.suggestIdentifier(projectId, typeId, personDto, accessibleInstitutionIds,
                lang, TYPE_NOT_FOUND, (project, type) -> containerService.suggestIdentifier(newContainer(project, type)));
    }

    private static ContainerDTO newContainer(ProjectContext project, ConceptDTO type) {
        ContainerDTO shell = new ContainerDTO();
        shell.setActionUnit(project.projectSummary());
        // Required by TraceableEntity (NOT NULL); the service does not fill them in — same as the
        // find creation (FindOpenApiService): the caller, in the project's organization.
        shell.setCreatedBy(project.userInfo().getUser());
        shell.setCreatedByInstitution(project.institution());
        shell.setType(type);
        return shell;
    }

    @Transactional
    public ContainerResource patchContainer(long id, ContainerPatchRequest request, PersonDTO personDto,
                                            Set<Long> accessibleInstitutionIds, String lang) {
        ContainerDTO container = requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
        InstitutionDTO institution = container.getActionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, container.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo, container.getActionUnit().getId());
        boolean answersChange = request.getAnswers() != null && !request.getAnswers().isEmpty();
        String newIdentifier = IdentifierPatch.requested(request.getIdentifier());
        boolean identifierChange = IdentifierPatch.changes(newIdentifier, container.getIdentifier());
        boolean statusChange = ValidationOpenApiService.changes(container.getValidationStatus(), request.getValidationStatus());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((answersChange || identifierChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(container.getValidationStatus(), request.getValidationStatus(), canEdit, canValidate);

        if (identifierChange) {
            container.setIdentifier(newIdentifier);
            IdentifierPatch.requireFree(containerService.identifierAlreadyExistInAction(container));
        }
        if (answersChange || identifierChange || !statusChange) {
            Long projectId = container.getActionUnit().getId();
            OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
                // The same effective form the container-types catalog lays out (system + additional fields).
                FormUiDto form = effectiveFormResolver.resolveEffectiveForm(projectId,
                        ConfigurableTable.CONTENANT, container.getType() != null ? container.getType().getId() : null);
                Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                        fieldAnswerPatchService.apply(container, form, request.getAnswers(), projectId);
                return containerService.save(container, additionalAnswers);
            });
        }
        // After the save: save() writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(fr.siamois.domain.models.container.Container.class, id, request.getValidationStatus(), personDto);
        return loadContainer(id, personDto, accessibleInstitutionIds, lang);
    }

    private ContainerResource toResourceWithPermissionsAndAnswers(ContainerDTO container, PersonDTO personDto, String lang) {
        UserInfo userInfo = new UserInfo(container.getActionUnit().getCreatedByInstitution(), personDto, lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, container.getActionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS);

        ContainerListProjectionService.ContainerListProjection projection = containerListProjectionService.buildOne(container, lang);
        ContainerResource resource = containerOpenApiMapper.toResource(container, lang, projection.resolvedLabels());
        Map<String, Object> answers = new java.util.LinkedHashMap<>(java.util.Objects.requireNonNullElse(projection.answersFor(container.getId()), Map.of()));
        // The projection reads system fields only; the type's additional fields live in their own answer rows.
        answers.putAll(OpenApiExecutionContext.callWithUserInfo(userInfo, () -> fieldAnswerWireService.additionalAnswers(
                customFieldAnswerService.loadAdditionalFieldAnswers(container), lang)));
        // The additional answers join after the projection's own shaping: cut them the same way.
        resource.setAnswers(multiValueAnswers.shapeOne(Container.class, container.getId(), answers, null,
                ValuesLimit.forDetail(), lang));
        resource.setPermissions(ProjectResourcePermissions.of(canEdit)
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, container.getActionUnit().getId()))
                .withManageSettings(profilePermissionService.hasProjectSettingsPermission(userInfo, container.getActionUnit().getId()))
                .withDelete(profilePermissionService.hasDeleteEntitiesPermission(userInfo, container.getActionUnit().getId())));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
    }

    /** The container, if the caller can see it — 404 otherwise, whether it doesn't exist or is out of scope. */
    public ContainerDTO requireAccessible(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        return requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
    }

    private ContainerDTO requireAccessibleContainer(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        ContainerDTO container = containerService.findById(id);
        if (container == null || container.getActionUnit() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenant introuvable");
        }
        InstitutionDTO institution = container.getActionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null
                || accessibleInstitutionIds == null || !accessibleInstitutionIds.contains(institution.getId())
                || !profilePermissionService.canViewProject(personDto, institution, container.getActionUnit().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenant introuvable ou hors périmètre");
        }
        return container;
    }

    /** Previous/next container in the same project ({@code GET /api/v1/containers/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        ContainerDTO container = requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.CONTAINER,
                container.getActionUnit().getId(), container.getId());
    }

    /**
     * Supprime le contenant sans relation avec une autre entité (409 sinon). Droit d'édition requis sur le projet.
     */
    @Transactional
    public void deleteContainer(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        ContainerDTO dto = requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
        InstitutionDTO institution = dto.getActionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasDeleteEntitiesPermission(userInfo, dto.getActionUnit().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression non autorisée");
        }
        try {
            OpenApiExecutionContext.runWithUserInfo(userInfo, () -> containerService.deleteById(id));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }
}
