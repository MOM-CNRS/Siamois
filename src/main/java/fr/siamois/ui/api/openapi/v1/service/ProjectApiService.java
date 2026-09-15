package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.ContainerSpec;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

/**
 * Orchestration des cas d'usage OpenAPI « projet » et listes liées : pagination, tri, périmètre institutions.
 */
@Service
@RequiredArgsConstructor
public class ProjectApiService {

    public static final int MAX_PAGE_SIZE = 200;

    public static final String CREATION_TIME = "creationTime";
    public static final String IDENTIFIER = "identifier";
    private static final Set<String> ALLOWED_PROJECT_SORT_FIELDS = Set.of(
            "name", IDENTIFIER, "fullIdentifier", CREATION_TIME
    );

    private static final Set<String> ALLOWED_RECORDING_UNIT_SORT_FIELDS = Set.of(
            CREATION_TIME, "id", IDENTIFIER, "fullIdentifier", "openingDate", "closingDate"
    );

    private static final Set<String> ALLOWED_ORGANIZATION_SORT_FIELDS = Set.of("id", "name", IDENTIFIER, "creationDate");

    private static final Set<String> ALLOWED_PLACE_SORT_FIELDS = Set.of("id", "name", "code", CREATION_TIME);

    private final InstitutionService institutionService;
    private final ActionUnitService actionUnitService;
    private final RecordingUnitService recordingUnitService;
    private final SpatialUnitService spatialUnitService;
    private final DocumentService documentService;
    private final SpecimenService specimenService;
    private final ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    private final FindOpenApiMapper findOpenApiMapper;
    private final PersonMapper personMapper;
    private final ProfilePermissionService profilePermissionService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final PhaseService phaseService;
    private final ContainerService containerService;

    public void validatePagedListRequest(int offset, int limit) {
        if (offset < 0 || limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètres de pagination invalides");
        }
        if (limit > 0 && offset % limit != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset doit être un multiple de limit");
        }
    }

    public ProjectApiCaller requireCaller() {
        Person person = AuthenticatedUserUtils.getAuthenticatedUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));
        PersonDTO personDto = personMapper.convert(person);
        List<InstitutionDTO> institutions = List.copyOf(institutionService.findInstitutionsOfPerson(personDto));
        Set<Long> institutionIds = institutions.stream()
                .map(InstitutionDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return new ProjectApiCaller(personDto, institutionIds, institutions);
    }

    public void assertOrganizationInCallerScope(Long organizationId, Set<Long> accessibleInstitutionIds) {
        if (organizationId != null && !accessibleInstitutionIds.contains(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible");
        }
    }

    public InstitutionDTO requireOrganization(Long id, ProjectApiCaller caller) {
        assertOrganizationInCallerScope(id, caller.accessibleInstitutionIds());
        return caller.institutions().stream()
                .filter(inst -> id.equals(inst.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Page<AccessibleProjectForApi> pageAccessibleProjects(
            ProjectApiCaller caller,
            Long organizationId,
            String search,
            int offset,
            int limit,
            List<String> sortParams) {
        assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        // TODO: implement multi-sort; for now only the first element is used
        String sortParam = (sortParams != null && !sortParams.isEmpty()) ? sortParams.get(0) : null;
        Sort sort = parseProjectSort(sortParam);
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit, sort);
        return actionUnitService.findAccessibleProjects(
                caller.person().getId(),
                caller.accessibleInstitutionIds(),
                organizationId,
                search,
                pageable);
    }

    public AccessibleProjectForApi requireAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = actionUnitService.findAccessibleProjectByKey(projectIdOrKey, caller.accessibleInstitutionIds());
        ActionUnitDTO project = row.actionUnit();
        if (!profilePermissionService.canViewProject(caller.person(), project.getCreatedByInstitution(), project.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable ou non accessible");
        }
        return row;
    }

    /**
     * Crée un projet dans une organisation (gestionnaire d'institution ou d'action requis).
     */
    @Transactional
    public AccessibleProjectForApi createProject(ProjectApiCaller caller, ProjectCreateRequest request, String lang) {
        if (request.getOrganizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        assertOrganizationInCallerScope(parseLong(request.getOrganizationId()), caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(parseLong(request.getOrganizationId()));
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasActionUnitCreatePermission(userInfo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de projet non autorisée");
        }

        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name est obligatoire");
        }
        String identifier = request.getIdentifier() == null ? "" : request.getIdentifier().trim();
        if (identifier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identifier est obligatoire");
        }
        if (request.getTypeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "typeConceptId est obligatoire");
        }

        Concept typeConcept = conceptService.findById(parseLong(request.getTypeId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de projet introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        // todo map request dto to domain dto and use service

        ActionUnitDTO shell = new ActionUnitDTO();
        shell.setCreatedByInstitution(institution);
        shell.setCreatedBy(caller.person());
        shell.setName(name);
        shell.setIdentifier(identifier);
        shell.setBeginDate(request.getBeginDate());
        shell.setEndDate(request.getEndDate());
        shell.setType(typeDto);
        applyMainLocation(shell, request.getMainLocationId());
        applySpatialContext(shell, request.getSpatialContextSpatialUnitIds());

        recordingUnitOpenApiService.applySystemProjectFormFieldAnswers(
                shell, null, caller.person(), lang);

        try {
            ActionUnitDTO saved = actionUnitService.save(userInfo, shell, typeDto);
            return actionUnitService.findAccessibleProjectByKey(
                    String.valueOf(saved.getId()), caller.accessibleInstitutionIds());
        } catch (ActionUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (NullActionUnitIdentifierException | FailedActionUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Mise à jour partielle d'un projet accessible, avec contrôle d'écriture ({@link ProfilePermissionService}).
     */
    @Transactional
    public AccessibleProjectForApi patchProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            ProjectPatchRequest patch,
            String lang) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        ActionUnitDTO dto = row.actionUnit();
        InstitutionDTO inst = dto.getCreatedByInstitution();
        if (inst == null || inst.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation de rattachement");
        }
        UserInfo userInfo = new UserInfo(inst, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification du projet non autorisée");
        }
        applyProjectPatch(dto, patch);
        ConceptDTO type = dto.getType();
        if (patch.getTypeId() != null) {
            Concept concept = conceptService.findById(parseLong(patch.getTypeId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de projet inconnu"));
            type = conceptMapper.convert(concept);
            dto.setType(type);
        }
        if (patch.getMainLocationId() != null) {
            applyMainLocation(dto, patch.getMainLocationId());
        }
        if (patch.getSpatialContextSpatialUnitIds() != null) {
            applySpatialContext(dto, patch.getSpatialContextSpatialUnitIds());
        }
        try {
            actionUnitService.save(userInfo, dto, type);
        } catch (ActionUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (NullActionUnitIdentifierException | FailedActionUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return actionUnitService.findAccessibleProjectByKey(String.valueOf(dto.getId()), caller.accessibleInstitutionIds());
    }

    /**
     * Supprime un projet sans unité d'enregistrement ni projet enfant (sinon 409).
     */
    @Transactional
    public void deleteProject(ProjectApiCaller caller, String projectIdOrKey, String lang) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        ActionUnitDTO dto = row.actionUnit();
        if (dto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans identifiant");
        }
        InstitutionDTO inst = dto.getCreatedByInstitution();
        if (inst == null || inst.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation de rattachement");
        }
        UserInfo userInfo = new UserInfo(inst, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression du projet non autorisée");
        }
        if (row.recordingUnitCount() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible de supprimer : le projet contient des unités d'enregistrement");
        }
        if (row.childActionUnitCount() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible de supprimer : le projet contient des sous-projets");
        }
        try {
            actionUnitService.deleteProjectWhenEmpty(dto.getId());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    private static void applyProjectPatch(ActionUnitDTO dto, ProjectPatchRequest patch) {
        if (patch.getName() != null) {
            dto.setName(patch.getName());
        }
        if (patch.getIdentifier() != null) {
            String identifier = patch.getIdentifier().trim();
            if (!identifier.isEmpty()) {
                dto.setIdentifier(identifier);
            }
        }
        if (patch.getBeginDate() != null) {
            dto.setBeginDate(patch.getBeginDate());
        }
        if (patch.getEndDate() != null) {
            dto.setEndDate(patch.getEndDate());
        }
    }

    private void applyMainLocation(ActionUnitDTO dto, String mainLocationId) {
        if (mainLocationId == null || mainLocationId.isBlank()) {
            dto.setMainLocation(null);
            return;
        }
        long placeId = parseLong(mainLocationId.trim());
        try {
            dto.setMainLocation(new SpatialUnitSummaryDTO(spatialUnitService.findById(placeId)));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable: " + placeId);
        }
    }

    private void applySpatialContext(ActionUnitDTO dto, List<String> spatialContextSpatialUnitIds) {
        if (spatialContextSpatialUnitIds == null) {
            return;
        }
        LinkedHashSet<SpatialUnitSummaryDTO> context = new LinkedHashSet<>();
        for (String rawId : spatialContextSpatialUnitIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            long placeId = parseLong(rawId.trim());
            try {
                context.add(new SpatialUnitSummaryDTO(spatialUnitService.findById(placeId)));
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable: " + placeId);
            }
        }
        dto.setSpatialContext(context);
    }

    /**
     * Institutions que l'utilisateur peut consulter.
     * Tri et pagination appliqués sur la liste déjà portée par {@link ProjectApiCaller#institutions()}
     * (chargée une seule fois lors de l'appel à {@link #requireCaller()}).
     */
    public Page<InstitutionDTO> pageAccessibleOrganizations(ProjectApiCaller caller, int offset, int limit, List<String> sortParams) {
        // TODO: implement multi-sort; for now only the first element is used
        String sortParam = (sortParams != null && !sortParams.isEmpty()) ? sortParams.get(0) : null;
        Sort sort = parseOrganizationSort(sortParam);
        List<InstitutionDTO> sorted = sortInstitutions(caller.institutions(), sort);
        long total = sorted.size();
        int from = Math.min(offset, (int) total);
        int to = Math.min(offset + limit, (int) total);
        List<InstitutionDTO> slice = sorted.subList(from, to);
        Pageable pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        return new PageImpl<>(slice, pageable, total);
    }

    public Page<RecordingUnitDTO> pageRecordingUnitsForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            int offset,
            int limit,
            String sortParam) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Sort sort = parseRecordingUnitSort(sortParam);
        return recordingUnitService.findByActionUnitId(row.actionUnit().getId(), limit, offset, sort);
    }

    /**
     * Documents rattachés au projet (unité d'action) via la table {@code action_unit_document}.
     */
    @Transactional(readOnly = true)
    public List<DocumentResource> listDocumentsForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        return toSortedDocumentResources(documentService.findForActionUnit(row.actionUnit()));
    }

    @Transactional(readOnly = true)
    public List<PhaseResource> listPhasesForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        return listPhasesForAccessibleProject(caller, projectIdOrKey, null);
    }

    /**
     * @param q Filtre optionnel (sous-chaîne, insensible à la casse) sur le titre ou l'identifiant —
     *          pour l'autocomplete "phase" du formulaire. Pas de pagination serveur : le nombre de
     *          phases d'un projet reste borné, contrairement aux listes d'UE/mobiliers.
     */
    public List<PhaseResource> listPhasesForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey,
                                                               @Nullable String q) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        String needle = q != null ? q.trim().toLowerCase() : null;
        return phaseService.findAllByActionUnitId(row.actionUnit().getId()).stream()
                .filter(phase -> needle == null || needle.isEmpty()
                        || (phase.getTitle() != null && phase.getTitle().toLowerCase().contains(needle))
                        || (phase.getIdentifier() != null && phase.getIdentifier().toLowerCase().contains(needle)))
                .map(phase -> {
                    PhaseResource resource = new PhaseResource();
                    resource.setId(phase.getId() != null ? String.valueOf(phase.getId()) : null);
                    resource.setIdentifier(phase.getIdentifier());
                    resource.setTitle(phase.getTitle());
                    String label = phase.getTitle() != null && !phase.getTitle().isBlank()
                            ? phase.getTitle()
                            : phase.getIdentifier();
                    resource.setLabel(label);
                    return resource;
                })
                .toList();
    }

    /**
     * Création minimale d'une phase depuis un champ formulaire (autocomplete "créer une nouvelle phase"),
     * miroir de {@code EntityFormContext.saveNewPhaseFromField}. {@code PhaseService.save} fait déjà le
     * contrôle de permission (PROJECT_EDIT_PHASES) et la génération d'identifiant.
     */
    @Transactional
    public PhaseResource createPhaseForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey,
                                                          String title, @Nullable Integer orderNumber,
                                                          @Nullable Long typeConceptId) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title est obligatoire");
        }
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), null);

        PhaseDTO dto = new PhaseDTO();
        dto.setActionUnit(new ActionUnitSummaryDTO(row.actionUnit()));
        dto.setTitle(title.trim());
        dto.setOrderNumber(orderNumber);
        if (typeConceptId != null) {
            Concept typeConcept = conceptService.findById(typeConceptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de phase introuvable: " + typeConceptId));
            dto.setType(conceptMapper.convert(typeConcept));
        }

        PhaseDTO saved = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            try {
                return phaseService.save(dto);
            } catch (ForbiddenOperationException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }
        });

        PhaseResource resource = new PhaseResource();
        resource.setId(saved.getId() != null ? String.valueOf(saved.getId()) : null);
        resource.setIdentifier(saved.getIdentifier());
        resource.setTitle(saved.getTitle());
        resource.setLabel(saved.getTitle() != null && !saved.getTitle().isBlank() ? saved.getTitle() : saved.getIdentifier());
        return resource;
    }

    private static final int CONTAINER_SEARCH_LIMIT = 50;

    /**
     * Recherche paginée de contenants d'un projet (autocomplete "contenant" du formulaire), miroir de
     * {@code EntityFormContext.getContainerOptions}. Filtre optionnel sur l'identifiant (sous-chaîne).
     */
    public List<ContainerResource> searchContainersForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey,
                                                                        @Nullable String q) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        FilterDTO filter = new FilterDTO();
        filter.addScopeFilter(ContainerSpec.ACTION_UNIT_FILTER, List.of(row.actionUnit().getId()), FilterDTO.FilterType.EQUAL);
        if (q != null && !q.isBlank()) {
            filter.add(ContainerSpec.IDENTIFIER_FILTER, q.trim(), FilterDTO.FilterType.CONTAINS);
        }

        return containerService.searchContainers(institution, filter, PageRequest.of(0, CONTAINER_SEARCH_LIMIT))
                .getContent().stream()
                .map(ProjectApiService::toContainerResource)
                .toList();
    }

    /**
     * Création minimale d'un contenant depuis un champ formulaire (autocomplete "créer un nouveau
     * contenant"), miroir de {@code EntityFormContext.saveNewContainerFromField}. {@code ContainerService.save}
     * fait déjà le contrôle de permission (PROJECT_EDIT_CONTAINERS) et la génération d'identifiant.
     */
    @Transactional
    public ContainerResource createContainerForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey,
                                                                  @Nullable Long typeConceptId, @Nullable Long parentId) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), null);

        ContainerDTO dto = new ContainerDTO();
        dto.setActionUnit(new ActionUnitSummaryDTO(row.actionUnit()));
        dto.setParentId(parentId);
        if (typeConceptId != null) {
            Concept typeConcept = conceptService.findById(typeConceptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de contenant introuvable: " + typeConceptId));
            dto.setType(conceptMapper.convert(typeConcept));
        }

        ContainerDTO saved = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            try {
                return containerService.save(dto);
            } catch (ForbiddenOperationException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }
        });

        return toContainerResource(saved);
    }

    private static final int SPECIMEN_AUTOCOMPLETE_LIMIT = 20;

    /**
     * Recherche de mobiliers d'un projet (autocomplete "mobilier" du formulaire), miroir de
     * {@code EntityFormContext.completeSpecimenOptions} (scope : unité d'action de l'UE, pas juste
     * l'institution). Réutilise {@link FindResource}/{@link FindOpenApiMapper}, déjà branchés pour
     * {@code GET /api/v1/recording-units/{id}/mobiliers}.
     */
    public List<FindResource> searchSpecimensForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey,
                                                                   @Nullable String q) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        FilterDTO filter = new FilterDTO();
        if (q != null && !q.isBlank()) {
            filter.add(SpecimenSpec.FULL_IDENTIFIER_FILTER, q.trim(), FilterDTO.FilterType.CONTAINS);
        }

        return specimenService.searchSpecimenInActionUnit(
                        institution, row.actionUnit(), filter, PageRequest.of(0, SPECIMEN_AUTOCOMPLETE_LIMIT))
                .getContent().stream()
                .map(findOpenApiMapper::toResource)
                .toList();
    }

    private static ContainerResource toContainerResource(ContainerDTO dto) {
        ContainerResource resource = new ContainerResource();
        resource.setId(dto.getId() != null ? String.valueOf(dto.getId()) : null);
        resource.setIdentifier(dto.getIdentifier());
        resource.setLabel(dto.getIdentifier());
        return resource;
    }

    /**
     * Supprime une UE par sa clé primaire (recording_unit_id), avec contrôle d'écriture.
     */
    @Transactional
    public void deleteRecordingUnit(ProjectApiCaller caller, long recordingUnitId, String acceptLanguage) {
        RecordingUnitDTO dto = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                recordingUnitId, caller.accessibleInstitutionIds());
        InstitutionDTO inst = dto.getCreatedByInstitution();
        if (inst == null || inst.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité d'enregistrement sans organisation");
        }
        String lang = primaryAcceptLanguage(acceptLanguage);
        UserInfo userInfo = new UserInfo(inst, caller.person(), lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, dto)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression de l'unité non autorisée");
        }
        try {
            recordingUnitService.deleteRecordingUnitById(recordingUnitId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    /**
     * Documents rattachés à une UE via la table {@code recording_unit_document}.
     */
    @Transactional(readOnly = true)
    public List<DocumentResource> listDocumentsForAccessibleRecordingUnit(ProjectApiCaller caller, String recordingUnitKey) {
        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, caller.accessibleInstitutionIds(), null);
        requireRecordingUnitViewPermission(caller, ru);
        return toSortedDocumentResources(documentService.findForRecordingUnit(ru));
    }

    private List<DocumentResource> toSortedDocumentResources(List<Document> docs) {
        return docs.stream()
                .sorted(Comparator.comparing(Document::getId, Comparator.nullsLast(Long::compareTo)))
                .map(projectDocumentOpenApiMapper::toResource)
                .toList();
    }

    private void requireRecordingUnitViewPermission(ProjectApiCaller caller, RecordingUnitDTO ru) {
        if (!profilePermissionService.canViewRecordingUnit(caller.person(), ru)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unité introuvable ou non accessible");
        }
    }

    /**
     * Mobiliers (spécimens) rattachés à une UE accessible, avec pagination (même périmètre que le détail UE).
     */
    @Transactional(readOnly = true)
    public Page<FindResource> pageFindsForAccessibleRecordingUnit(
            ProjectApiCaller caller,
            String recordingUnitKey,
            int offset,
            int limit,
            String sortParam,
            String acceptLanguage) {
        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, caller.accessibleInstitutionIds(), null);
        requireRecordingUnitViewPermission(caller, ru);
        InstitutionDTO institution = ru.getCreatedByInstitution();
        if (institution == null || institution.getId() == null || ru.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité d'enregistrement sans institution");
        }
        String lang = primaryAcceptLanguage(acceptLanguage);
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit);
        Page<SpecimenDTO> page = specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institution.getId(),
                ru.getId(),
                null,
                null,
                null,
                lang,
                sortParam,
                pageable);
        return page.map(findOpenApiMapper::toResource);
    }

    /**
     * Langue principale depuis l'en-tête {@code Accept-Language} (première entrée, sans qualité).
     */
    public static String primaryAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.FRENCH.getLanguage();
        }
        String first = acceptLanguage.split(",")[0].trim();
        int semi = first.indexOf(';');
        if (semi > 0) {
            first = first.substring(0, semi).trim();
        }
        int dash = first.indexOf('-');
        return (dash > 0 ? first.substring(0, dash) : first).toLowerCase(Locale.ROOT);
    }

    // ---- Sort helpers -------------------------------------------------------

    /**
     * Résolution générique d'un paramètre de tri "field:direction".
     * Si le champ n'est pas dans {@code allowedFields}, utilise {@code defaultProperty} en préservant la direction.
     */
    private static Sort parseSort(String sortParam, Set<String> allowedFields, String defaultProperty) {
        if (sortParam == null || sortParam.isBlank()) return Sort.by(Sort.Direction.ASC, defaultProperty);
        String[] parts = sortParam.split(":", 2);
        String property = allowedFields.contains(parts[0].trim()) ? parts[0].trim() : defaultProperty;
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, property);
    }

    /**
     * Tri avec tri secondaire stable par {@code id:asc} (sauf si le tri primaire est déjà {@code id}).
     * Défaut : {@code creationTime:desc, id:desc}.
     */
    private static Sort parseSortWithStableId(String sortParam, Set<String> allowedFields) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, CREATION_TIME).and(Sort.by(Sort.Direction.DESC, "id"));
        if (sortParam == null || sortParam.isBlank()) return defaultSort;
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!allowedFields.contains(property)) return defaultSort;
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort primary = Sort.by(dir, property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static Sort parseProjectSort(String sortParam) {
        return parseSort(sortParam, ALLOWED_PROJECT_SORT_FIELDS, "name");
    }

    private static Sort parseOrganizationSort(String sortParam) {
        return parseSort(sortParam, ALLOWED_ORGANIZATION_SORT_FIELDS, "name");
    }

    private static Sort parseRecordingUnitSort(String sortParam) {
        return parseSortWithStableId(sortParam, ALLOWED_RECORDING_UNIT_SORT_FIELDS);
    }

    public static Sort parsePlaceSort(String sortParam) {
        return parseSortWithStableId(sortParam, ALLOWED_PLACE_SORT_FIELDS);
    }

    private static List<InstitutionDTO> sortInstitutions(List<InstitutionDTO> institutions, Sort sort) {
        if (institutions.isEmpty()) return List.of();
        Sort.Order order = sort.stream().findFirst().orElse(new Sort.Order(Sort.Direction.ASC, "name"));
        Comparator<InstitutionDTO> cmp = switch (order.getProperty()) {
            case "id" -> Comparator.comparing(InstitutionDTO::getId, Comparator.nullsLast(Long::compareTo));
            case IDENTIFIER -> Comparator.comparing(
                    InstitutionDTO::getIdentifier, Comparator.nullsLast(String::compareToIgnoreCase));
            case "creationDate" -> Comparator.comparing(
                    InstitutionDTO::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(InstitutionDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase));
        };
        if (order.isDescending()) cmp = cmp.reversed();
        return institutions.stream().sorted(cmp).toList();
    }
}
