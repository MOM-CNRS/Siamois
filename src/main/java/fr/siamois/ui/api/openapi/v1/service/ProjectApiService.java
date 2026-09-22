package fr.siamois.ui.api.openapi.v1.service;

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
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectHistoryAuthorResource;
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
    private final BookmarkService bookmarkService;
    private final HistoryAuditService historyAuditService;

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

    public AccessibleProjectForApi requireAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = actionUnitService.findAccessibleProjectByKey(projectIdOrKey, caller.accessibleInstitutionIds());
        ActionUnitDTO project = row.actionUnit();
        if (!profilePermissionService.canViewProject(caller.person(), project.getCreatedByInstitution(), project.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable ou non accessible");
        }
        return row;
    }

    /**
     * {@code _permissions} for a single project (detail/create/patch response) — one direct check,
     * not batched, since there's only one row.
     */
    public ProjectResourcePermissions permissionsFor(ProjectApiCaller caller, AccessibleProjectForApi row) {
        ActionUnitDTO dto = row.actionUnit();
        boolean canWrite = profilePermissionService.hasActionUnitWritePermission(
                caller.person(), dto.getCreatedByInstitution(), dto.getId());
        return ProjectResourcePermissions.of(canWrite);
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
        if (!profilePermissionService.hasActionUnitWritePermission(userInfo, dto)) {
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

    @Transactional(readOnly = true)
    public List<PhaseResource> listPhasesForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        return phaseService.findAllByActionUnitId(row.actionUnit().getId()).stream()
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
    private static Sort parseRecordingUnitSort(String sortParam) {
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
