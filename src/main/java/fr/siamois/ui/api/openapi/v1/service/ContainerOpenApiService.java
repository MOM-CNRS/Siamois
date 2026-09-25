package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.models.container.Container;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
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

    private final ContainerService containerService;
    private final ActionUnitService actionUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;
    private final ContainerOpenApiMapper containerOpenApiMapper;
    private final ContainerListProjectionService containerListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final FieldAnswerWireService fieldAnswerWireService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final EffectiveFormResolver effectiveFormResolver;

    @Transactional(readOnly = true)
    public ContainerResource getContainerById(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        ContainerDTO container = requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
        return toResourceWithPermissionsAndAnswers(container, personDto, lang);
    }

    @Transactional
    public ContainerResource createContainer(ContainerCreateRequest request, PersonDTO personDto,
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
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de contenant non autorisée sur ce projet");
        }

        Concept typeConcept = conceptService.findById(parseLong(typeId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de contenant introuvable"));

        ContainerDTO shell = new ContainerDTO();
        shell.setActionUnit(new ActionUnitSummaryDTO(au));
        shell.setType(conceptMapper.convert(typeConcept));

        ContainerDTO saved = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> containerService.save(shell));
        return toResourceWithPermissionsAndAnswers(saved, personDto, lang);
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
        boolean statusChange = ValidationOpenApiService.changes(container.getValidated(), request.getValidated());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((answersChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(container.getValidated(), request.getValidated(), canEdit, canValidate);

        if (answersChange || !statusChange) {
            Long projectId = container.getActionUnit().getId();
            OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
                // The same effective form the container-types catalog lays out (system + additional fields).
                FormUiDto form = effectiveFormResolver.resolveEffectiveForm(Container.DETAILS_FORM, projectId,
                        ConfigurableTable.CONTENANT, container.getType() != null ? container.getType().getId() : null);
                Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                        fieldAnswerPatchService.apply(container, form, request.getAnswers(), projectId);
                return containerService.save(container, additionalAnswers);
            });
        }
        // After the save: save() writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(fr.siamois.domain.models.container.Container.class, id, request.getValidated(), personDto);
        return getContainerById(id, personDto, accessibleInstitutionIds, lang);
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
        resource.setAnswers(answers);
        resource.setPermissions(ProjectResourcePermissions.of(canEdit)
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, container.getActionUnit().getId())));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
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

    /** Previous/next container in the same project ({@code GET /api/v1/containers/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(long id, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        ContainerDTO container = requireAccessibleContainer(id, personDto, accessibleInstitutionIds);
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.CONTAINER,
                container.getActionUnit().getId(), container.getId());
    }
}
