package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.ContainerSpec;
import fr.siamois.infrastructure.database.repositories.specs.PhaseSpec;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectHistoryAuthorResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectSiblingResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectSiblingsResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectHistoryEntryResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    /**
     * Synthetic sort key : ce n'est pas un chemin JPA de {@code ActionUnit} mais le nombre d'unités
     * d'enregistrement rattachées. Résolu via {@code ActionUnitSpec#orderByRecordingUnitCount}
     * et retiré du {@code Pageable} avant d'atteindre le repository.
     */
    public static final String RECORDING_UNIT_COUNT = "recordingUnitCount";

    private static final Set<String> ALLOWED_PROJECT_SORT_FIELDS = Set.of(
            "id", "name", IDENTIFIER, "fullIdentifier", "beginDate", "endDate",
            CREATION_TIME, RECORDING_UNIT_COUNT
    );

    /**
     * Subset of {@link #ALLOWED_PROJECT_SORT_FIELDS} a sibling cursor can actually walk —
     * {@code ActionUnitSpec.cursor}'s lexicographic tuple comparison silently drops a row once its
     * sort field is {@code null} (Postgres sorts NULLs LAST on ASC; the comparison does not), and
     * {@link #RECORDING_UNIT_COUNT} isn't a JPA path at all. {@code id}/{@code name}/
     * {@code creationTime} are the three {@code ActionUnit} columns declared {@code nullable = false}
     * — see {@code ActionUnit.name} and {@code TraceableEntity.creationTime}.
     */
    private static final Set<String> CURSORABLE_PROJECT_SORT_FIELDS = Set.of("id", "name", CREATION_TIME);

    // Union of the real JPA paths and RecordingUnitSpec's own synthetic sort keys
    // (specimenCount/relationshipCount/parentsCount/childrenCount, the *Label concept sorts,
    // matrixColor/tpq/taq) — RecordingUnitTableColumnDefaults' extra sortable columns all need one
    // of these. RecordingUnitSpec.allColumns() is the same list the JSF table's own filter engine
    // (RecordingUnitSortFilterService) validates filter keys against, so this can't silently drift
    // from what a f.<key> is actually allowed to be either.
    private static final Set<String> ALLOWED_RECORDING_UNIT_SORT_FIELDS = Stream.concat(
            Stream.of(CREATION_TIME, "id", IDENTIFIER, "fullIdentifier", "openingDate", "closingDate"),
            RecordingUnitSpec.allColumns().stream()
    ).collect(Collectors.toUnmodifiableSet());

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
    private final BookmarkService bookmarkService;
    private final HistoryAuditService historyAuditService;
    private final ValidationOpenApiService validationOpenApiService;

    private static final String ACTION_UNIT_RESOURCE_URI_PREFIX = "/action-unit/";

    /**
     * Resource URI a Project bookmark is stored under — must match JSF's own
     * {@code ActionUnitPanel.entityRessourceUri()} exactly (plan §5), not the REST {@code _links.self}
     * path, since bookmarks created from JSF and from the React main-panel share the same row.
     */
    public static String actionUnitResourceUri(Long actionUnitId) {
        return actionUnitId == null ? null : ACTION_UNIT_RESOURCE_URI_PREFIX + actionUnitId;
    }

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

    /**
     * Une page de projets accessibles. {@code sortParam} suit la forme {@code "champ:direction"} et doit
     * appartenir à {@link #ALLOWED_PROJECT_SORT_FIELDS} — un champ inconnu est un 400, jamais un repli
     * silencieux (un repli silencieux est exactement ce qui a masqué le binding cassé de {@code ?sort=}).
     *
     * <p>TODO: multi-tri ; un seul critère est supporté pour l'instant.</p>
     */
    public Page<AccessibleProjectForApi> pageAccessibleProjects(
            ProjectApiCaller caller,
            Long organizationId,
            String search,
            int offset,
            int limit,
            String sortParam) {
        return pageAccessibleProjects(caller, organizationId, search, offset, limit, sortParam, ProjectListFilter.EMPTY);
    }

    /**
     * @param filter per-column filters (plan §3 phase 3, {@code f.<key>} query params) — already
     *               parsed and validated by {@link ProjectListFilter#parse}; {@link ProjectListFilter#EMPTY}
     *               for no filtering.
     */
    public Page<AccessibleProjectForApi> pageAccessibleProjects(
            ProjectApiCaller caller,
            Long organizationId,
            String search,
            int offset,
            int limit,
            String sortParam,
            ProjectListFilter filter) {
        assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        int pageNumber = offset / limit;

        // recordingUnitCount n'est pas un chemin JPA : il est porté par une Specification et doit être
        // retiré du Pageable, sinon Hibernate échoue sur une propriété inconnue.
        Sort.Direction countDirection = recordingUnitCountSortDirection(sortParam);
        Pageable pageable = countDirection != null
                ? PageRequest.of(pageNumber, limit, Sort.by(Sort.Direction.ASC, "id"))
                : PageRequest.of(pageNumber, limit, parseProjectSort(sortParam));

        return actionUnitService.findAccessibleProjects(
                caller.person().getId(),
                caller.accessibleInstitutionIds(),
                organizationId,
                search,
                pageable,
                countDirection,
                filter);
    }

    /**
     * What an unscoped list's create form may pick a project from ({@code GET /projects?canCreate=…}):
     * each kind's create endpoint checks this same instance / organisation / project triple.
     * A find is created on a recording unit, with that recording unit's edit right.
     */
    public enum CreatableKind {
        RECORDING_UNIT("recordingUnit", PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS),
        FIND("find", PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS),
        PHASE("phase", PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES),
        CONTAINER("container", PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS, PermissionConstants.PROJECT_EDIT_CONTAINERS);

        private final String key;
        private final String instanceCode;
        private final String organizationCode;
        private final String projectCode;

        CreatableKind(String key, String instanceCode, String organizationCode, String projectCode) {
            this.key = key;
            this.instanceCode = instanceCode;
            this.organizationCode = organizationCode;
            this.projectCode = projectCode;
        }

        public static CreatableKind parse(String key) {
            for (CreatableKind kind : values()) {
                if (kind.key.equals(key)) return kind;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "canCreate inconnu : " + key);
        }
    }

    /** {@code filter}, restricted to the organisation's projects in which the caller may create {@code kind}. */
    public ProjectListFilter restrictToCreatable(ProjectApiCaller caller, Long organizationId,
                                                 ProjectListFilter filter, CreatableKind kind) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "canCreate exige organizationId");
        }
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(organizationId);
        Set<Long> granting = profilePermissionService.actionUnitIdsGranting(
                new UserInfo(institution, caller.person(), null),
                kind.instanceCode, kind.organizationCode, kind.projectCode);
        return granting == null ? filter : filter.withIdIn(granting);
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
     * {@code GET /projects/{id}/siblings} — the previous/next project per
     * {@code CURSORABLE_PROJECT_SORT_FIELDS}, on the SAME accessible-projects spec as the list
     * itself ({@link ActionUnitService#findSiblingProject}), never JSF's institution-only
     * {@code ActionUnitService.findNextByInstitution} (that would leak a project id the caller
     * cannot actually open).
     *
     * @param sortParam "champ:direction", defaulting to {@code creationTime:asc} — the JSF-parity
     *                  order — when absent. A caller wanting siblings to follow the list's own
     *                  current sort/filter passes the same params the list request used.
     */
    public ProjectSiblingsResource findSiblings(
            ProjectApiCaller caller,
            String projectIdOrKey,
            Long organizationId,
            String search,
            String sortParam,
            ProjectListFilter filter) {
        AccessibleProjectForApi current = requireAccessibleProject(caller, projectIdOrKey);
        ActionUnitDTO currentDto = current.actionUnit();

        CursorSort sort = parseCursorableProjectSort(sortParam);
        Comparable<?> currentValue = sortFieldValue(currentDto, sort.field());

        Optional<ActionUnitDTO> previous = actionUnitService.findSiblingProject(
                caller.person().getId(), caller.accessibleInstitutionIds(), organizationId, search, filter,
                sort.field(), sort.direction(), currentValue, currentDto.getId(), false);
        Optional<ActionUnitDTO> next = actionUnitService.findSiblingProject(
                caller.person().getId(), caller.accessibleInstitutionIds(), organizationId, search, filter,
                sort.field(), sort.direction(), currentValue, currentDto.getId(), true);

        return new ProjectSiblingsResource(
                previous.map(ProjectApiService::toSiblingResource).orElse(null),
                next.map(ProjectApiService::toSiblingResource).orElse(null));
    }

    private static ProjectSiblingResource toSiblingResource(ActionUnitDTO dto) {
        String label = (dto.getFullIdentifier() != null && !dto.getFullIdentifier().isBlank())
                ? dto.getFullIdentifier() : dto.getName();
        return new ProjectSiblingResource(String.valueOf(dto.getId()), label, actionUnitResourceUri(dto.getId()));
    }

    private record CursorSort(String field, Sort.Direction direction) {}

    /**
     * Parses {@code sort} exactly like {@link #parseProjectSort} but restricted to
     * {@link #CURSORABLE_PROJECT_SORT_FIELDS} — a field outside that set (nullable, or the
     * synthetic {@code recordingUnitCount}) is a <strong>400</strong>, never a silent fallback to
     * the default order.
     */
    private static CursorSort parseCursorableProjectSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return new CursorSort(CREATION_TIME, Sort.Direction.ASC);
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!CURSORABLE_PROJECT_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Champ de tri non utilisable pour la navigation précédent/suivant : " + property);
        }
        return new CursorSort(property, sortDirection(parts));
    }

    private static Comparable<?> sortFieldValue(ActionUnitDTO dto, String field) {
        return switch (field) {
            case "id" -> dto.getId();
            case "name" -> dto.getName();
            case CREATION_TIME -> dto.getCreationTime();
            default -> throw new IllegalStateException("Unreachable: validated by parseCursorableProjectSort");
        };
    }

    /**
     * {@code _permissions} for a single project (detail/create/patch response) — one direct check,
     * not batched, since there's only one row.
     */
    public ProjectResourcePermissions permissionsFor(ProjectApiCaller caller, AccessibleProjectForApi row) {
        ActionUnitDTO dto = row.actionUnit();
        boolean canWrite = profilePermissionService.hasActionUnitWritePermission(
                caller.person(), dto.getCreatedByInstitution(), dto.getId());
        boolean canManageSettings = dto.getCreatedByInstitution() != null
                && profilePermissionService.hasProjectPermission(
                        new UserInfo(dto.getCreatedByInstitution(), caller.person(), null),
                        dto.getId(), PermissionConstants.PROJECT_MANAGE_SETTINGS);
        boolean canValidate = dto.getCreatedByInstitution() != null
                && profilePermissionService.hasValidatePermission(
                        new UserInfo(dto.getCreatedByInstitution(), caller.person(), null), dto.getId());
        return ProjectResourcePermissions.of(canWrite).withManageSettings(canManageSettings).withValidate(canValidate);
    }

    /**
     * {@code _permissions} for a whole list page, batched (plan §5) — see
     * {@link ProfilePermissionService#actionUnitIdsWithWritePermission}, which is itself institution-aware
     * since a list page can span several institutions (unlike JSF's always-one-institution table views).
     */
    public Map<Long, ProjectResourcePermissions> permissionsFor(ProjectApiCaller caller, Collection<AccessibleProjectForApi> rows) {
        Map<Long, InstitutionDTO> institutionByActionUnitId = new LinkedHashMap<>();
        for (AccessibleProjectForApi row : rows) {
            Long id = row.actionUnit().getId();
            if (id != null) {
                institutionByActionUnitId.put(id, row.actionUnit().getCreatedByInstitution());
            }
        }
        Set<Long> canWriteIds = profilePermissionService.actionUnitIdsWithWritePermission(caller.person(), institutionByActionUnitId);
        Map<Long, ProjectResourcePermissions> result = new LinkedHashMap<>();
        for (Long id : institutionByActionUnitId.keySet()) {
            result.put(id, ProjectResourcePermissions.of(canWriteIds.contains(id)));
        }
        return result;
    }

    /**
     * {@code bookmarked} for a single project — one direct check.
     */
    public boolean isBookmarked(ProjectApiCaller caller, AccessibleProjectForApi row, String lang) {
        ActionUnitDTO dto = row.actionUnit();
        String uri = actionUnitResourceUri(dto.getId());
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (uri == null || institution == null) {
            return false;
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        return Boolean.TRUE.equals(bookmarkService.isRessourceBookmarkedByUser(userInfo, uri));
    }

    /**
     * {@code bookmarked} for a whole list page, batched (plan §5) — see
     * {@link BookmarkService#findBookmarkedResourceUris}. Grouped by institution first, same reasoning
     * as {@link #permissionsFor(ProjectApiCaller, Collection)}: a bookmark row is
     * {@code (person, institution, resourceUri)}, so the lookup itself is institution-scoped.
     */
    public Set<String> bookmarkedResourceUris(ProjectApiCaller caller, Collection<AccessibleProjectForApi> rows, String lang) {
        Map<Long, List<AccessibleProjectForApi>> rowsByInstitutionId = rows.stream()
                .filter(row -> row.actionUnit().getCreatedByInstitution() != null
                        && row.actionUnit().getCreatedByInstitution().getId() != null)
                .collect(Collectors.groupingBy(row -> row.actionUnit().getCreatedByInstitution().getId()));

        Set<String> bookmarked = new HashSet<>();
        for (List<AccessibleProjectForApi> group : rowsByInstitutionId.values()) {
            InstitutionDTO institution = group.get(0).actionUnit().getCreatedByInstitution();
            UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
            Set<String> uris = group.stream()
                    .map(row -> actionUnitResourceUri(row.actionUnit().getId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            bookmarked.addAll(bookmarkService.findBookmarkedResourceUris(userInfo, uris));
        }
        return bookmarked;
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
        boolean canEdit = profilePermissionService.hasActionUnitWritePermission(userInfo, dto);
        boolean statusChange = ValidationOpenApiService.changes(dto.getValidated(), patch.getValidated());
        // A validator may change the status alone without the edit right; anything else needs it.
        if (!(statusChange && patch.isStatusOnly()) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification du projet non autorisée");
        }
        validationOpenApiService.requireAllowed(dto.getValidated(), patch.getValidated(), canEdit,
                profilePermissionService.hasValidatePermission(userInfo, dto.getId()));
        if (statusChange && patch.isStatusOnly()) {
            validationOpenApiService.apply(ActionUnit.class, dto.getId(), patch.getValidated(), caller.person());
            return actionUnitService.findAccessibleProjectByKey(String.valueOf(dto.getId()), caller.accessibleInstitutionIds());
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
        if (patch.isGeomPresent()) {
            dto.setGeom(patch.getGeom());
        }
        applyAnswerPatch(dto, patch.getAnswers());
        // applyAnswerPatch may have overwritten `type` (fieldId -101, valueBinding "type") via
        // reflection — save(...) takes it as its own parameter (see save's own javadoc for why),
        // so re-read it from the DTO rather than passing the now-possibly-stale local.
        type = dto.getType();
        try {
            actionUnitService.save(userInfo, dto, type);
        } catch (ActionUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (NullActionUnitIdentifierException | FailedActionUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        // After the save: it writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(ActionUnit.class, dto.getId(), patch.getValidated(), caller.person());
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
        if (!profilePermissionService.hasActionUnitWritePermission(userInfo, dto)) {
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

    /**
     * Applique {@code ProjectPatchRequest.answers} à {@code dto}, après les champs plats
     * ({@code applyProjectPatch}, {@code typeId}, {@code mainLocationId}, {@code spatialContextSpatialUnitIds})
     * — voir la précédence documentée sur {@link ProjectPatchRequest#getAnswers()}.
     *
     * <p>Projet n'a pas de lignes {@code CustomFieldAnswer} (voir {@code ProjectAnswersProjector}) :
     * comme en lecture, écrire une réponse est une écriture réflexive directe sur la propriété
     * {@code ActionUnitDTO} que {@code valueBinding} désigne, pas un appel au moteur de formulaire.
     * Champs pris en charge : TEXT, INTEGER, DECIMAL, DATETIME, SELECT_ONE_FROM_FIELD_CODE,
     * SELECT_MULTIPLE_FROM_FIELD_CODE, SELECT_ONE_SPATIAL_UNIT — le sous-ensemble que
     * {@code ActionUnit.DETAILS_FORM} utilise réellement (pas tout le zoo de {@code CustomField}
     * que RecordingUnit doit couvrir).</p>
     */
    private void applyAnswerPatch(ActionUnitDTO dto, Map<String, AnswerInput> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, AnswerInput> entry : answers.entrySet()) {
            String fieldId = entry.getKey();
            CustomField field = ProjectAnswersProjector.fieldById(fieldId);
            if (field == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de formulaire inconnu : " + fieldId);
            }
            AnswerInput input = entry.getValue();

            if (field instanceof CustomFieldSelectMultipleFromFieldCode) {
                // "values: null" veut dire "ne pas toucher" (même convention que
                // RecordingUnitPatchRequest.answers) — contrairement à "value: null" pour un champ
                // scalaire, qui veut dire "vider".
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
        if (field instanceof CustomFieldDecimal) return coerceDecimalAnswer(raw);
        if (field instanceof CustomFieldDateTime) return coerceDateTimeAnswer(raw);
        if (field instanceof CustomFieldSelectOneFromFieldCode) return coerceConceptId(raw);
        if (field instanceof CustomFieldSelectOneSpatialUnit) return coerceSpatialUnitId(raw);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ce champ n'est pas modifiable via l'API : " + field.getClass().getSimpleName());
    }

    private ConceptDTO coerceConceptId(Object raw) {
        long conceptId = extractLongId(raw);
        Concept concept = conceptService.findById(conceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept introuvable : " + conceptId));
        return conceptMapper.convert(concept);
    }

    private SpatialUnitSummaryDTO coerceSpatialUnitId(Object raw) {
        long placeId = extractLongId(raw);
        try {
            return new SpatialUnitSummaryDTO(spatialUnitService.findById(placeId));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable : " + placeId);
        }
    }

    private static Integer coerceIntegerAnswer(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entier attendu, reçu : " + raw);
        }
    }

    private static Double coerceDecimalAnswer(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre décimal attendu, reçu : " + raw);
        }
    }

    private static OffsetDateTime coerceDateTimeAnswer(Object raw) {
        if (raw instanceof OffsetDateTime odt) return odt;
        String value = String.valueOf(raw).trim();
        try {
            // Un champ de date envoie souvent juste "AAAA-MM-JJ" (fields/renderers.tsx's DateRenderer) ;
            // OffsetDateTime.parse seul rejetterait ça, faute d'offset.
            if (value.length() <= 10) {
                return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            }
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date invalide, reçu : " + raw);
        }
    }

    private static long extractLongId(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // falls through to the error below
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
                    // falls through to the error below
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu, reçu : " + raw);
    }

    /**
     * Écrit {@code value} sur la propriété {@code ActionUnitDTO} que {@code field.getValueBinding()}
     * désigne, par réflexion — miroir en écriture de la lecture faite par
     * {@code ProjectAnswersProjector}.
     */
    private static void writeAnswerBinding(ActionUnitDTO dto, CustomField field, Object value) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ sans liaison : " + field.getId());
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(ActionUnitDTO.class, binding);
        Method setter = descriptor == null ? null : descriptor.getWriteMethod();
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

    /**
     * @param filter per-column filters (parity with {@link #pageAccessibleProjects}'s own {@code
     *               f.<key>} support) — already parsed and validated by
     *               {@link RecordingUnitListFilter#parse}; {@link RecordingUnitListFilter#EMPTY}
     *               for no filtering.
     */
    public Page<RecordingUnitDTO> pageRecordingUnitsForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            int offset,
            int limit,
            String sortParam,
            String search,
            RecordingUnitListFilter filter) {
        // Authorization first, same order pageAccessibleProjects/requireAccessibleProject follow
        // everywhere else in this service — a caller with no access to the project must never learn
        // anything about it, including whether its sort/filter params would otherwise be valid.
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Sort sort = parseRecordingUnitSort(sortParam);
        FilterDTO filterDTO = filter.toFilterDTO(search);
        return recordingUnitService.findByActionUnitId(row.actionUnit().getId(), limit, offset, sort, filterDTO);
    }

    /**
     * Whether the caller can write recording units on this project — a single boolean for the
     * whole {@link #pageRecordingUnitsForProject} page, since every row shares the same project
     * (unlike {@link #permissionsFor}, which handles a project LIST that can span institutions).
     * Mirrors {@link ProfilePermissionService#hasRecordingUnitWritePermission}'s own instance/
     * organization/project triple, without needing a {@code RecordingUnitDTO} per row to get there.
     */
    /**
     * Nombre de mobiliers du projet — un seul appel, pour {@code _counts.finds} sur le détail
     * uniquement (jamais batché sur une page de liste, qui ne l'affiche pas).
     */
    public long countFindsForProject(AccessibleProjectForApi row) {
        Integer count = specimenService.countByActionContext(row.actionUnit());
        return count == null ? 0L : count;
    }

    /**
     * Nombre de phases du projet — même pattern que {@link #countFindsForProject}, détail
     * uniquement.
     */
    public long countPhasesForProject(AccessibleProjectForApi row) {
        return phaseService.countByActionContext(row.actionUnit());
    }

    /**
     * Nombre de contenants du projet — même pattern que {@link #countPhasesForProject}, détail
     * uniquement.
     */
    public long countContainersForProject(AccessibleProjectForApi row) {
        return containerService.countByActionContext(row.actionUnit());
    }
    // See countPhasesForProject just above for the same pattern.

    private static final Set<String> ALLOWED_CONTAINER_SORT_FIELDS =
            Set.of(ContainerSpec.IDENTIFIER_FILTER, "id");

    /**
     * Same contract as {@link #parsePhaseSort} — an unknown property is a 400.
     */
    static Sort parseContainerSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, ContainerSpec.IDENTIFIER_FILTER).and(Sort.by(Sort.Direction.ASC, "id"));
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_CONTAINER_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri inconnu : " + property);
        }
        Sort primary = Sort.by(sortDirection(parts), property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * Page de contenants d'un projet ({@code GET /api/v1/projects/{id}/containers}) — pendant
     * réduit de {@link #pagePhasesForProject} : recherche libre sur {@code identifier} et tri sur
     * une petite liste blanche, sans le contrat {@code f.<clé>} par colonne.
     */
    public Page<ContainerDTO> pageContainersForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            int offset,
            int limit,
            String sortParam,
            String search) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Sort sort = parseContainerSort(sortParam);
        Pageable pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        FilterDTO filterDTO = new FilterDTO();
        filterDTO.add(ContainerSpec.ACTION_UNIT_FILTER, List.of(row.actionUnit().getId()), FilterDTO.FilterType.CONTAINS);
        if (search != null && !search.isBlank()) {
            filterDTO.add(ContainerSpec.IDENTIFIER_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        return containerService.searchContainers(institution, filterDTO, pageable);
    }

    /**
     * Whether the caller can write containers on this project — a single boolean for the whole
     * {@link #pageContainersForProject} page, same pattern as {@link #canEditPhasesForProject}.
     */
    public boolean canEditContainersForProject(ProjectApiCaller caller, String projectIdOrKey, String lang) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        return profilePermissionService.hasProjectPermission(userInfo, row.actionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS);
    }

    private static final Set<String> ALLOWED_FIND_SORT_FIELDS =
            Set.of(SpecimenSpec.FULL_IDENTIFIER_FILTER, "collectionDate", "id");

    /**
     * Same contract as {@link #parseRecordingUnitSort} — an unknown property is a 400, not a
     * silent fallback (unlike {@link #parseSortWithStableId}, which several other list endpoints
     * use and which is deliberately lenient; reusing it here would mask a client's own sort typo).
     */
    static Sort parseFindSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, SpecimenSpec.FULL_IDENTIFIER_FILTER).and(Sort.by(Sort.Direction.ASC, "id"));
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_FIND_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri inconnu : " + property);
        }
        Sort primary = Sort.by(sortDirection(parts), property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * Page de mobiliers d'un projet ({@code GET /api/v1/projects/{id}/mobiliers}) — pendant réduit
     * de {@link #pageRecordingUnitsForProject} : recherche libre sur {@code fullIdentifier} et tri
     * sur une petite liste blanche, sans le contrat {@code f.<clé>} par colonne (pas encore
     * construit côté mobilier — voir le plan de migration React, lot Mobilier).
     */
    public Page<SpecimenDTO> pageFindsForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            int offset,
            int limit,
            String sortParam,
            String search) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Sort sort = parseFindSort(sortParam);
        Pageable pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        FilterDTO filterDTO = new FilterDTO();
        if (search != null && !search.isBlank()) {
            filterDTO.add(SpecimenSpec.FULL_IDENTIFIER_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        return specimenService.searchSpecimenInActionUnit(institution, row.actionUnit(), filterDTO, pageable);
    }

    /**
     * Whether the caller can write finds on this project — a single boolean for the whole
     * {@link #pageFindsForProject} page, same pattern as {@link #canEditRecordingUnitsForProject}.
     */
    public boolean canEditFindsForProject(ProjectApiCaller caller, String projectIdOrKey, String lang) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        return profilePermissionService.hasProjectPermission(userInfo, row.actionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_FINDS,
                PermissionConstants.ORGANIZATION_EDIT_FINDS,
                PermissionConstants.PROJECT_EDIT_FINDS);
    }

    public boolean canEditRecordingUnitsForProject(ProjectApiCaller caller, String projectIdOrKey, String lang) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        return profilePermissionService.hasProjectPermission(userInfo, row.actionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);
    }

    /**
     * Documents rattachés au projet (unité d'action) via la table {@code action_unit_document}.
     */
    @Transactional(readOnly = true)
    public List<DocumentResource> listDocumentsForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        return toSortedDocumentResources(documentService.findForActionUnit(row.actionUnit()));
    }

    /**
     * Revision history for the fiche header (plan §4/§5), not a separate tab — thin wrapper over
     * {@link HistoryAuditService}, which is already entity-agnostic and used the same way by JSF's
     * {@code AbstractSingleEntityPanel} for every entity type, not just Project.
     */
    @Transactional(readOnly = true)
    public List<ProjectHistoryEntryResource> listHistoryForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Long actionUnitId = row.actionUnit().getId();
        if (actionUnitId == null) {
            return List.of();
        }
        return historyAuditService.findAllRevisionForEntity(ActionUnitDTO.class, actionUnitId).stream()
                .sorted()
                .map(revision -> {
                    Person author = revision.revisionEntity().getUpdatedBy();
                    ProjectHistoryAuthorResource authorResource = author == null ? null
                            : new ProjectHistoryAuthorResource(author.getId(), author.getName(), author.getLastname());
                    return new ProjectHistoryEntryResource(
                            revision.revisionEntity().getRevId(),
                            revision.getDate(),
                            revision.revisionType().name(),
                            authorResource);
                })
                .toList();
    }

    private static final Set<String> ALLOWED_PHASE_SORT_FIELDS =
            Set.of(PhaseSpec.IDENTIFIER_FILTER, "orderNumber", "title", "id");

    /**
     * Same contract as {@link #parseFindSort} — an unknown property is a 400.
     */
    static Sort parsePhaseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "orderNumber").and(Sort.by(Sort.Direction.ASC, "id"));
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_PHASE_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri inconnu : " + property);
        }
        Sort primary = Sort.by(sortDirection(parts), property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * Page de phases d'un projet ({@code GET /api/v1/projects/{id}/phases}) — pendant réduit de
     * {@link #pageFindsForProject} : recherche libre sur {@code identifier} et tri sur une petite
     * liste blanche, sans le contrat {@code f.<clé>} par colonne (voir le plan de migration React,
     * lot Phases).
     */
    public Page<PhaseDTO> pagePhasesForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            int offset,
            int limit,
            String sortParam,
            String search) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Sort sort = parsePhaseSort(sortParam);
        Pageable pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        FilterDTO filterDTO = new FilterDTO();
        filterDTO.add(PhaseSpec.ACTION_UNIT_FILTER, List.of(row.actionUnit().getId()), FilterDTO.FilterType.CONTAINS);
        if (search != null && !search.isBlank()) {
            filterDTO.add(PhaseSpec.IDENTIFIER_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        return phaseService.searchPhases(institution, filterDTO, pageable);
    }

    /**
     * Whether the caller can write phases on this project — a single boolean for the whole
     * {@link #pagePhasesForProject} page, same pattern as {@link #canEditFindsForProject}.
     */
    public boolean canEditPhasesForProject(ProjectApiCaller caller, String projectIdOrKey, String lang) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        return profilePermissionService.hasProjectPermission(userInfo, row.actionUnit().getId(),
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES);
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
        return pageFindsOfRecordingUnit(caller, recordingUnitKey, offset, limit, sortParam, null, acceptLanguage).page();
    }

    /**
     * One page of a recording unit's finds, plus the recording unit itself — what the caller needs to
     * compute the page's {@code _permissions} (its project) and bookmarks (its institution).
     */
    public record RecordingUnitFindsPage(Page<FindResource> page, RecordingUnitDTO recordingUnit) {
    }

    /** Same as {@link #pageFindsForAccessibleRecordingUnit}, plus a {@code search} on fullIdentifier. */
    public RecordingUnitFindsPage pageFindsOfRecordingUnit(
            ProjectApiCaller caller,
            String recordingUnitKey,
            int offset,
            int limit,
            String sortParam,
            String search,
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
                search != null && !search.isBlank() ? search : null,
                null,
                null,
                lang,
                sortParam,
                pageable);
        return new RecordingUnitFindsPage(page.map(findOpenApiMapper::toResource), ru);
    }

    /** One page of recording units scoped to something other than a project, and the project they belong to. */
    public record ScopedRecordingUnitPage(Page<RecordingUnitDTO> page, Long projectId) {
    }

    /**
     * Direct children of an accessible recording unit ({@code GET /recording-units/{id}/children}),
     * same search/sort/{@code f.*} contract as {@link #pageRecordingUnitsForProject}.
     */
    public ScopedRecordingUnitPage pageRecordingUnitChildren(
            ProjectApiCaller caller,
            String recordingUnitKey,
            int offset,
            int limit,
            String sortParam,
            String search,
            RecordingUnitListFilter filter) {
        RecordingUnitDTO parent = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, caller.accessibleInstitutionIds(), null);
        requireRecordingUnitViewPermission(caller, parent);
        Sort sort = parseRecordingUnitSort(sortParam);
        Page<RecordingUnitDTO> page = recordingUnitService.findChildrenOf(
                parent.getId(), limit, offset, sort, filter.toFilterDTO(search));
        return new ScopedRecordingUnitPage(page, parent.getActionUnit() != null ? parent.getActionUnit().getId() : null);
    }

    /**
     * Recording units of a phase ({@code GET /phases/{id}/recording-units}). The caller must have
     * checked access to the phase itself (PhaseOpenApiService#requireAccessible) — its project is
     * this page's scope.
     */
    public Page<RecordingUnitDTO> pageRecordingUnitsForPhase(
            long phaseId,
            int offset,
            int limit,
            String sortParam,
            String search,
            RecordingUnitListFilter filter) {
        Sort sort = parseRecordingUnitSort(sortParam);
        return recordingUnitService.findByPhaseId(phaseId, limit, offset, sort, filter.toFilterDTO(search));
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

    /**
     * Tri projet : défaut {@code name:asc}, tri secondaire stable {@code id:asc} (sinon la pagination
     * n'est pas déterministe quand plusieurs projets portent le même nom), et <strong>400</strong> sur
     * un champ hors {@link #ALLOWED_PROJECT_SORT_FIELDS}.
     */
    private static Sort parseProjectSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_PROJECT_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri inconnu : " + property);
        }
        Sort primary = Sort.by(sortDirection(parts), property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * {@code null} sauf si le tri demandé porte sur le champ synthétique {@code recordingUnitCount}.
     * Valide le champ au passage (400 sur champ inconnu), comme {@link #parseProjectSort(String)}.
     */
    private static Sort.Direction recordingUnitCountSortDirection(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) return null;
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_PROJECT_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri inconnu : " + property);
        }
        return RECORDING_UNIT_COUNT.equals(property) ? sortDirection(parts) : null;
    }

    private static Sort.Direction sortDirection(String[] parts) {
        return parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private static Sort parseOrganizationSort(String sortParam) {
        return parseSort(sortParam, ALLOWED_ORGANIZATION_SORT_FIELDS, "name");
    }

    /**
     * Tri UE : défaut {@code creationTime:desc}, tri secondaire stable {@code id:asc} (sauf champ
     * synthétique — voir la note ci-dessous), et <strong>400</strong> sur un champ hors
     * {@link #ALLOWED_RECORDING_UNIT_SORT_FIELDS}, comme {@link #parseProjectSort(String)}. Une
     * faute de frappe dans un des 10+ champs triables introduits par
     * {@code RecordingUnitTableColumnDefaults} ne doit jamais retomber silencieusement sur le tri
     * par défaut — c'est précisément ce repli silencieux qui avait masqué le bug de {@code ?sort=}
     * que {@link #parseProjectSort(String)} corrige déjà pour le projet.
     *
     * <p>Note : sur un tri synthétique (un compteur ou un libellé de concept),
     * {@code RecordingUnitSortFilterService.stripSyntheticSort} reconstruit un {@code PageRequest}
     * sans aucun tri avant d'atteindre le repository — le départage stable par {@code id} posé ici
     * est donc perdu dans ce cas précis (le tri primaire, lui, est bien appliqué en mémoire par
     * {@code applySyntheticSort}). Comportement identique à celui de JSF ; non corrigé ici.</p>
     */
    static Sort parseRecordingUnitSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, CREATION_TIME).and(Sort.by(Sort.Direction.ASC, "id"));
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_RECORDING_UNIT_SORT_FIELDS.contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri inconnu : " + property);
        }
        Sort primary = Sort.by(sortDirection(parts), property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
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
