package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.template.ProjectTemplateApplier;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.domain.services.tree.HierarchyTreeQuery;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.TreePage;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.api.AccessibleProjectsQuery;
import fr.siamois.dto.api.ProjectCursor;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdLabelRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitFilterSpec;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.ExactMatchFirst;
import fr.siamois.infrastructure.database.repositories.specs.TextSearchSpec;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing Action Units.
 * This service provides methods to find, save, and manage Action Units in the system.
 */
@Slf4j
@Service
@Transactional(rollbackFor = ActionUnitAlreadyExistsException.class)
@RequiredArgsConstructor
public class ActionUnitService implements ArkEntityService {

    private static final String NOT_FOUND_BY_ID = "ActionUnit not found with ID: ";


    private final ActionUnitRepository actionUnitRepository;
    private final ProjectDeletionService projectDeletionService;
    private final RecordingUnitRepository recordingUnitRepository;
    private final ConceptService conceptService;
    private final ActionUnitMapper actionUnitMapper;
    private final PersonMapper personMapper;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ConceptMapper conceptMapper;
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileRepository profileRepository;
    private final DocumentRepository documentRepository;
    private final RecordingUnitIdLabelRepository recordingUnitIdLabelRepository;
    private final ProfileService profileService;
    private final PersonProfileAssignmentService personProfileAssignmentService;
    private final ProfileMapper profileMapper;
    private final ProfilePermissionService profilePermissionService;
    private final InstitutionService institutionService;
    private final ProjectIdentifierService projectIdentifierService;
    private final List<ProjectTemplateApplier> templateAppliers;


    /**
     * Find an action unit by its ID
     *
     * @param id The ID of the action unit
     * @return The ActionUnit having the given ID
     * @throws ActionUnitNotFoundException If no action unit are found for the given id
     * @throws RuntimeException            If the repository method returns a RuntimeException
     */
    @Transactional(readOnly = true)
    public ActionUnitDTO findById(long id) {
        try {
            ActionUnit actionUnit = actionUnitRepository.findById(id)
                    .orElseThrow(() -> new ActionUnitNotFoundException(NOT_FOUND_BY_ID + id));
            return convertWithCount(actionUnit);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Profile> createProjectProfiles(ActionUnitDTO actionUnitDTO) {
        Map<String, Profile> projectProfiles = new HashMap<>();
        projectProfiles.put(ProfileConstants.PROJECT_MANAGER, profileService.createOrGetProjectManagerProfile(actionUnitDTO));
        projectProfiles.put(ProfileConstants.PROJECT_MEMBER, profileService.createOrGetProjectMemberProfile(actionUnitDTO));
        return projectProfiles;
    }

    /**
     * Save an ActionUnit without a transaction.
     *
     * @param info           User information containing the user and institution
     * @param actionUnitDTO  The ActionUnit to save
     * @param typeConceptDTO The concept type of the ActionUnit
     * @return The saved ActionUnit
     */
    @CacheEvict(value = "MyActionUnits", allEntries = true)
    public ActionUnit saveNotTransactional(UserInfo info, ActionUnitDTO actionUnitDTO, ConceptDTO typeConceptDTO)
            throws ActionUnitAlreadyExistsException {
        ensureUniqueNameAndIdentifier(info, actionUnitDTO);
        prepareActionUnitDtoForSave(info, actionUnitDTO);

        ActionUnit actionUnit = actionUnitMapper.invertConvert(actionUnitDTO);
        actionUnit.setType(conceptService.saveOrGetConcept(typeConceptDTO));
        actionUnit.setCreatedBy(personMapper.invertConvert(info.getUser()));
        applyMainLocation(actionUnitDTO, actionUnit);
        applySpatialContext(actionUnitDTO, actionUnit);

        try {
            return actionUnitRepository.save(actionUnit);
        } catch (RuntimeException e) {
            throw new FailedActionUnitSaveException(e.getMessage());
        }
    }

    private void ensureUniqueNameAndIdentifier(UserInfo info, ActionUnitDTO actionUnitDTO)
            throws ActionUnitAlreadyExistsException {
        Long institutionId = info.getInstitution().getId();
        Optional<ActionUnit> existingName = actionUnitRepository
                .findByNameAndCreatedByInstitutionId(actionUnitDTO.getName(), institutionId);
        if (conflictsWithExisting(actionUnitDTO, existingName)) {
            throw new ActionUnitAlreadyExistsException(
                    "name",
                    String.format("Action unit with name %s already exist", actionUnitDTO.getName()));
        }

        Optional<ActionUnit> existingId = actionUnitRepository
                .findByIdentifierAndCreatedByInstitutionId(actionUnitDTO.getIdentifier(), institutionId);
        if (conflictsWithExisting(actionUnitDTO, existingId)) {
            throw new ActionUnitAlreadyExistsException(
                    "identifier",
                    String.format("Action unit with identifier %s already exist", actionUnitDTO.getIdentifier()));
        }
    }

    private static boolean conflictsWithExisting(ActionUnitDTO actionUnitDTO, Optional<ActionUnit> existing) {
        return existing.isPresent()
                && (actionUnitDTO.getId() == null || !existing.get().getId().equals(actionUnitDTO.getId()));
    }

    private void prepareActionUnitDtoForSave(UserInfo info, ActionUnitDTO actionUnitDTO) {
        actionUnitDTO.setCreatedByInstitution(info.getInstitution());
        if (actionUnitDTO.getCreationTime() == null) {
            actionUnitDTO.setCreationTime(OffsetDateTime.now(ZoneId.systemDefault()));
        }
        if (actionUnitDTO.getFullIdentifier() == null) {
            if (actionUnitDTO.getIdentifier() == null) {
                throw new NullActionUnitIdentifierException("ActionUnit identifier must be set");
            }
            actionUnitDTO.setFullIdentifier(actionUnitDTO.getIdentifier());
        }
    }

    private void applyMainLocation(ActionUnitDTO actionUnitDTO, ActionUnit actionUnit) {
        if (actionUnitDTO.getMainLocation() == null) {
            actionUnit.setMainLocation(null);
            return;
        }
        SpatialUnitSummaryDTO mainLocation = actionUnitDTO.getMainLocation();
        if (mainLocation.getId() == null) {
            actionUnit.setMainLocation(saveNewMainLocation(actionUnit, mainLocation));
            return;
        }
        spatialUnitRepository.findById(mainLocation.getId())
                .ifPresentOrElse(actionUnit::setMainLocation,
                        () -> {
                            throw new FailedActionUnitSaveException("Lieu introuvable: " + mainLocation.getId());
                        });
    }

    private SpatialUnit saveNewMainLocation(ActionUnit actionUnit, SpatialUnitSummaryDTO mainLocation) {
        SpatialUnit toSave = new SpatialUnit();
        toSave.setType(actionUnit.getMainLocation().getType());
        toSave.setName(mainLocation.getName());
        toSave.setCreatedBy(actionUnit.getCreatedBy());
        toSave.setCode(mainLocation.getCode());
        toSave.setCreatedByInstitution(actionUnit.getCreatedByInstitution());
        return spatialUnitRepository.save(toSave);
    }

    private void applySpatialContext(ActionUnitDTO actionUnitDTO, ActionUnit actionUnit) {
        if (actionUnitDTO.getSpatialContext() == null) {
            return;
        }
        Set<SpatialUnit> persistentContext = new HashSet<>();
        for (SpatialUnitSummaryDTO summary : actionUnitDTO.getSpatialContext()) {
            resolveSpatialContextEntry(actionUnit, summary).ifPresent(persistentContext::add);
        }
        actionUnit.setSpatialContext(persistentContext);
    }

    private Optional<SpatialUnit> resolveSpatialContextEntry(ActionUnit actionUnit, SpatialUnitSummaryDTO summary) {
        if (summary.getId() == null) {
            SpatialUnit toSave = new SpatialUnit();
            toSave.setName(summary.getName());
            toSave.setCode(summary.getCode());
            toSave.setType(conceptMapper.invertConvert(summary.getType()));
            toSave.setCreatedBy(actionUnit.getCreatedBy());
            toSave.setCreatedByInstitution(actionUnit.getCreatedByInstitution());
            return Optional.of(spatialUnitRepository.save(toSave));
        }
        return spatialUnitRepository.findById(summary.getId());
    }

    /**
     * Save an ActionUnit with a transaction.
     *
     * @param info        User information containing the user and institution
     * @param actionUnit  The ActionUnit to save
     * @param typeConcept The concept type of the ActionUnit
     * @return The saved ActionUnit
     */
    @Transactional(rollbackFor = ActionUnitAlreadyExistsException.class)
    @CacheEvict(value = "MyActionUnits", allEntries = true)
    public ActionUnitDTO save(UserInfo info, ActionUnitDTO actionUnit, ConceptDTO typeConcept)
            throws ActionUnitAlreadyExistsException {
        return saveWithTemplate(info, actionUnit, typeConcept, null);
    }

    /**
     * Save an ActionUnit with a transaction. A new project with no identifier is numbered automatically
     * ({@link ProjectIdentifierService}), and is configured from the given template.
     *
     * @param templateId the project template to configure a new project from, null or {@code empty} for none
     */
    @Transactional(rollbackFor = ActionUnitAlreadyExistsException.class)
    @CacheEvict(value = "MyActionUnits", allEntries = true)
    public ActionUnitDTO save(UserInfo info, ActionUnitDTO actionUnit, ConceptDTO typeConcept, String templateId)
            throws ActionUnitAlreadyExistsException {
        return saveWithTemplate(info, actionUnit, typeConcept, templateId);
    }

    // Shared by both transactional save entry points: a call through `this` from one to the other would skip the proxy.
    private ActionUnitDTO saveWithTemplate(UserInfo info, ActionUnitDTO actionUnit, ConceptDTO typeConcept,
                                           String templateId) throws ActionUnitAlreadyExistsException {
        boolean isCreation = actionUnit.getId() == null;
        assertWritePermission(info, actionUnit);
        if (isCreation && (actionUnit.getIdentifier() == null || actionUnit.getIdentifier().isBlank())) {
            actionUnit.setIdentifier(projectIdentifierService.generate(info).value());
            actionUnit.setFullIdentifier(null);
        }
        ActionUnitDTO savedDTO = actionUnitMapper.convert(saveNotTransactional(info, actionUnit, typeConcept));
        if (isCreation) {
            assignRoles(info, savedDTO);
            applyTemplate(info, templateId, savedDTO.getId());
        }
        return savedDTO;
    }

    /** Whether a project of the institution already carries this name (names are unique per institution). */
    @Transactional(readOnly = true)
    public boolean projectNameExists(Long institutionId, String name) {
        return actionUnitRepository.findByNameAndCreatedByInstitutionId(name, institutionId).isPresent();
    }

    /** The identifier the next project of the institution would get, without reserving it. */
    @Transactional(readOnly = true)
    public Optional<String> previewNextProjectIdentifier(UserInfo info) {
        return projectIdentifierService.preview(info);
    }

    private void applyTemplate(UserInfo info, String templateId, Long projectId) {
        ProjectTemplateApplier applier = templateAppliers.stream()
                .filter(candidate -> candidate.supports(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown project template: " + templateId));
        if (applier.transactional()) {
            applier.apply(info, templateId, projectId);
            return;
        }
        // the default configuration is seeded after commit and never cancels the creation
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            applier.apply(info, templateId, projectId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applier.apply(info, templateId, projectId);
            }
        });
    }

    private void assertWritePermission(UserInfo info, ActionUnitDTO actionUnit) {
        boolean allowed = actionUnit.getId() == null
                ? profilePermissionService.hasActionUnitCreatePermission(info)
                : profilePermissionService.hasActionUnitWritePermission(info, actionUnit);
        if (!allowed) {
            throw new ForbiddenOperationException("You are not allowed to save this action unit");
        }
    }

    private void assignRoles(UserInfo info, ActionUnitDTO savedDTO) {
        Map<String, Profile> profiles = createProjectProfiles(savedDTO);
        assignAsOrganisationMember(info, savedDTO);
        assignAsProjectMember(info, savedDTO, profiles);
    }

    private void assignAsProjectMember(UserInfo info, ActionUnitDTO actionUnit, Map<String, Profile> profiles) {
        List<ProfileDTO> profileDTOS = new ArrayList<>();
        profileDTOS.add(profileMapper.convert(profiles.get(ProfileConstants.PROJECT_MANAGER)));
        profileDTOS.add(profileMapper.convert(profiles.get(ProfileConstants.PROJECT_MEMBER)));
        personProfileAssignmentService.addToProjectMembers(actionUnit, info.getUser(), profileDTOS);
    }

    private void assignAsOrganisationMember(UserInfo info, ActionUnitDTO actionUnit) {
        Profile institutionMember = profileService.createOrGetOrganizationMemberProfile(actionUnit.getCreatedByInstitution());
        List<ProfileDTO> institutionMemberProfileDTOS = new ArrayList<>();
        institutionMemberProfileDTOS.add(profileMapper.convert(institutionMember));
        personProfileAssignmentService.addToInstitution(actionUnit.getCreatedByInstitution(), info.getUser(), institutionMemberProfileDTOS);
    }

    /**
     * Find an ActionUnit by its ARK.
     *
     * @param ark The ARK of the ActionUnit to find
     * @return An Optional containing the ActionUnit if found, or empty if not found
     */
    public Optional<ActionUnit> findByArk(Ark ark) {
        return actionUnitRepository.findByArk(ark);
    }

    /**
     * Find all ActionUnits that do not have an ARK associated with them.
     *
     * @param institution The institution to filter ActionUnits by
     * @return A list of ActionUnits that do not have an ARK associated with them
     */
    @Override
    public List<ActionUnit> findWithoutArk(Institution institution) {
        return actionUnitRepository.findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    /**
     * Save an ActionUnit.
     *
     * @param toSave The ActionUnit to save
     * @return The saved ActionUnit
     */
    @Override
    @Caching(evict = {
            @CacheEvict({
                    "InstitutionHasRootChildrenAU",
                    "InstitutionHasRootChildrenSU",
                    "ActionUnitHasChildrenInInstitution"
            }),
            @CacheEvict(value = "MyActionUnits", allEntries = true)
    })
    public AbstractEntityDTO save(AbstractEntityDTO toSave) {
        UserInfo info = ExecutionContextHolder.get();
        if (info == null) {
            throw new ForbiddenOperationException("You are not allowed to save this action unit");
        }
        assertWritePermission(info, (ActionUnitDTO) toSave);
        try {
            return actionUnitMapper.convert(
                    actionUnitRepository.save(Objects.requireNonNull(
                            actionUnitMapper.invertConvert((ActionUnitDTO) toSave))));
        } catch (DataIntegrityViolationException e) {
            throw new FailedActionUnitSaveException(e.getMessage());
        }
    }

    public boolean fullIdentifierAlreadyExistInInstitution(ActionUnitDTO actionUnit) {
        if (actionUnit.getCreatedByInstitution() == null) {
            return false;
        }
        return actionUnitRepository.findByFullIdentifier(actionUnit.getFullIdentifier())
                .filter(existing -> existing.getCreatedByInstitution() != null && Objects.equals(
                        existing.getCreatedByInstitution().getId(),
                        actionUnit.getCreatedByInstitution().getId()))
                .filter(existing -> !Objects.equals(existing.getId(), actionUnit.getId()))
                .isPresent();
    }

    /**
     * Count the number of ActionUnits created by a specific institution.
     *
     * @param institutionId The institution to count ActionUnits for
     * @return The count of ActionUnits created by the institution
     */
    public long countByInstitutionId(Long institutionId) {
        return actionUnitRepository.countByCreatedByInstitutionId(institutionId);
    }

    public Integer countBySpatialContext(SpatialUnitDTO spatialUnit) {
        return actionUnitRepository.countBySpatialContext(spatialUnit.getId());
    }

    public int countByLocation(SpatialUnitDTO spatialUnit) {
        return actionUnitRepository.countByLocation(spatialUnit.getId());
    }

    /**
     * Find all ActionUnits created by a specific institution.
     *
     * @param institution The institution to find ActionUnits for
     * @return A set of ActionUnits created by the institution
     */
    public Set<ActionUnitDTO> findAllByInstitution(InstitutionDTO institution) {
        return actionUnitRepository.findByCreatedByInstitutionId(institution.getId())
                .stream()
                .map(actionUnitMapper::convert)
                .collect(Collectors.toSet());
    }

    /**
     * Find the next ActionUnit created by a specific institution after the given one.
     * If there is no next, returns the oldest one (wraps around).
     *
     * @param institution The institution to find ActionUnits for
     * @param current     The current ActionUnit to find the next one from
     * @return The next ActionUnitDTO, or the oldest one if there is no next
     */
    public ActionUnitDTO findNextByInstitution(InstitutionDTO institution, ActionUnitDTO current) {
        return actionUnitRepository
                .findNext(
                        institution.getId(), current.getCreationTime(), current.getId())
                .map(actionUnitMapper::convert)
                .orElseGet(() -> actionUnitRepository
                        .findFirst(institution.getId())
                        .map(actionUnitMapper::convert)
                        .orElse(current)
                );
    }

    /**
     * Find the previous ActionUnit created by a specific institution before the given one.
     * If there is no previous, returns the most recent one (wraps around).
     *
     * @param institution The institution to find ActionUnits for
     * @param current     The current ActionUnit to find the previous one from
     * @return The previous ActionUnitDTO, or the most recent one if there is no previous
     */
    public ActionUnitDTO findPreviousByInstitution(InstitutionDTO institution, ActionUnitDTO current) {
        return actionUnitRepository
                .findPrevious(
                        institution.getId(), current.getCreationTime(), current.getId())
                .map(actionUnitMapper::convert)
                .orElseGet(() -> actionUnitRepository
                        .findLast(institution.getId())
                        .map(actionUnitMapper::convert)
                        .orElse(current)
                );
    }

    /**
     * Toggle the validated status of an ActionUnit.
     *
     * @param actionUnitId The id of the ActionUnit to toggle
     * @return The updated ActionUnitDTO
     */
    public ActionUnitDTO toggleValidated(Long actionUnitId) {
        ActionUnit actionUnit = actionUnitRepository.findById(actionUnitId)
                .orElseThrow(() -> new ActionUnitNotFoundException("ActionUnit not found with id: " + actionUnitId));

        actionUnit.setValidationStatus(actionUnit.getValidationStatus().nextInCycle());


        return actionUnitMapper.convert(actionUnitRepository.save(actionUnit));
    }


    /**
     * Verify if the action is still active
     *
     * @param actionUnit The action
     * @return True if the action is open
     */
    public boolean isActionUnitStillOngoing(ActionUnitSummaryDTO actionUnit) {
        OffsetDateTime beginDate = actionUnit.getBeginDate();
        OffsetDateTime endDate = actionUnit.getEndDate();

        // If no begin date, we consider the action has not started yet
        if (beginDate == null) {
            return false;
        }

        // If begin date but no end date, action is still on going
        if (endDate == null) {
            return true;
        }

        OffsetDateTime now = OffsetDateTime.now();
        return !now.isBefore(beginDate) && !now.isAfter(endDate);
    }


    /**
     * Checks if a person is a manager of a given action
     *
     * @param action the action to check
     * @param person the person to check
     * @return true if the person is a manager of the institution, false otherwise
     */
    public boolean isManagerOf(ActionUnitSummaryDTO action, PersonDTO person) {
        // For now only the author is the manager, but we might need to extend it.
        return Objects.equals(action.getCreatedBy().getId(), person.getId());
    }

    /**
     * Get all the roots ActionUnit in the institution
     *
     * @param institutionId the institution id
     * @return The list of ActionUnit associated with the institution
     */
    public List<ActionUnitDTO> findAllWithoutParentsByInstitution(Long institutionId) {
        List<ActionUnit> res = actionUnitRepository.findRootsByInstitution(institutionId, 50L);
        return res.stream()
                .map(actionUnitMapper::convert)
                .toList();
    }

    /**
     * Get all ActionUnit in the institution that are the children of a given parent
     *
     * @param parentId      the parent id
     * @param institutionId the institution id
     * @return The list of ActionUnit associated with the institution and that are the children of a given parent
     */
    public List<ActionUnitDTO> findChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        List<ActionUnit> res = actionUnitRepository.findChildrenByParentAndInstitution(parentId, institutionId);
        return res.stream()
                .map(actionUnitMapper::convert)
                .toList();
    }


    @Cacheable(value = "MyActionUnits", key = "#member.id + '-' + #institution.id + '-' + #limit")
    public List<ActionUnitDTO> findByTeamMember(PersonDTO member, InstitutionDTO institution, long limit) {
        Specification<ActionUnit> specs = prepareSpecs(institution, new FilterDTO());
        Pageable pageLimit = PageRequest.of(0, Math.toIntExact(limit));

        specs = specs.and(ActionUnitSpec.hasProjectMembership(member.getId()));

        Page<ActionUnit> actionUnits = actionUnitRepository.findAll(specs, pageLimit);
        return actionUnits.stream()
                .map(this::convertWithCount)
                .toList();
    }


    /**
     * Does this unit has children?
     *
     * @param parentId      The parent ID
     * @param institutionId the institution ID
     * @return True if they are children
     */
    @Cacheable("ActionUnitHasChildrenInInstitution")
    public boolean existsChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        return actionUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId);
    }


    /**
     * Does the institution have action units?
     *
     * @param institutionId the institution ID
     * @return True if they are children
     */
    @Cacheable("InstitutionHasRootChildrenAU")
    public boolean existsRootChildrenByInstitution(Long institutionId) {
        return actionUnitRepository.existsRootChildrenByInstitution(institutionId);
    }

    @Cacheable("InstitutionHasRootChildrenSU")
    public boolean existsRootChildrenByRelatedSpatialUnit(Long spatialUnitId) {
        return actionUnitRepository.existsRootChildrenByRelatedSpatialUnit(spatialUnitId);
    }

    public int countRootsInInstitution(Long institutionId) {
        return actionUnitRepository.countRootsInInstitution(institutionId);
    }

    public List<ActionUnit> findRootsByInstitution(Long institutionId, int first, int pageSize) {
        return actionUnitRepository.findRootsByInstitution(institutionId, first, pageSize);
    }

    public List<ActionUnit> findRootsByInstitutionAndName(Long institutionId, String name, int first, int pageSize) {
        return actionUnitRepository.findRootsByInstitutionAndName(institutionId, name, first, pageSize);
    }

    public int countRootsByInstitutionAndName(Long institutionId, String name) {
        return actionUnitRepository.countRootsByInstitutionAndName(institutionId, name);
    }

    public boolean isRoot(Long actionUnitId, Long institutionId) {
        return actionUnitRepository.isRoot(actionUnitId, institutionId);
    }

    public Page<ActionUnitDTO> searchActionUnits(InstitutionDTO institutionDTO, FilterDTO filters, Pageable pageable) {
        Specification<ActionUnit> specs = prepareSpecs(institutionDTO, filters);
        specs = applyCountSort(specs, pageable.getSort());
        Page<ActionUnit> res = actionUnitRepository.findAll(specs, stripCountSort(pageable));
        return res.map(this::convertWithCount);
    }

    /**
     * Composes a count-based ordering {@link Specification} when the requested sort targets a
     * synthetic (non-JPA-path) count key, e.g. {@link ActionUnitSpec#RECORDING_UNIT_COUNT_SORT}.
     */
    private Specification<ActionUnit> applyCountSort(Specification<ActionUnit> specs, Sort sort) {
        for (Sort.Order order : sort) {
            if (ActionUnitSpec.RECORDING_UNIT_COUNT_SORT.equals(order.getProperty())) {
                return specs.and(ActionUnitSpec.orderByRecordingUnitCount(order.getDirection()));
            }
        }
        return specs;
    }

    /**
     * Strips the synthetic count sort key from the {@link Pageable} passed to the repository,
     * since it is not a real JPA-mapped path (the ordering is applied via {@link #applyCountSort}
     * as a {@link Specification} side effect instead).
     */
    private Pageable stripCountSort(Pageable pageable) {
        boolean hasCountSort = pageable.getSort().stream()
                .anyMatch(order -> ActionUnitSpec.RECORDING_UNIT_COUNT_SORT.equals(order.getProperty()));
        if (!hasCountSort) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }


    public int countSearchResultsInSpatialUnit(InstitutionDTO institutionDTO, SpatialUnitDTO spatialUnitDTO,
                                               FilterDTO filters) {
        Specification<ActionUnit> specs = prepareSpecs(institutionDTO, filters);
        specs = specs.and(ActionUnitSpec.actionUnitInSpatialUnit(spatialUnitDTO.getId()));
        return Math.toIntExact(actionUnitRepository.count(specs));
    }

    private ActionUnitDTO convertWithCount(ActionUnit au) {
        ActionUnitDTO dto = actionUnitMapper.convert(au);
        assert dto != null;
        Integer count = recordingUnitRepository.countByActionContext(au.getId());
        dto.setRecordingUnitCount(count != null ? count : 0);
        return dto;
    }


    public int countSearchResults(InstitutionDTO institutionDTO, FilterDTO filters) {
        Specification<ActionUnit> specs = prepareSpecs(institutionDTO, filters);
        return Math.toIntExact(actionUnitRepository.count(specs));
    }

    private Specification<ActionUnit> prepareSpecs(@NonNull InstitutionDTO institutionDTO, @NonNull FilterDTO filters) {
        Specification<ActionUnit> base = ActionUnitSpec.belongsToInstitution(institutionDTO.getId());

        if (filters.isRootOnly()) {
            // Scope filters (e.g. "action units of this spatial unit") are always part of the
            // fixed query context, even with no active user search — they must never be dropped
            // in root-mode, otherwise roots from other contexts leak into the tree.
            Specification<ActionUnit> scoped = base.and(scopeFilterSpecs(filters));
            if (filters.hasUserFilters()) {
                Collection<Long> closure = resolveAncestorClosure(institutionDTO, filters);
                if (closure.isEmpty()) {
                    return scoped.and((root, q, cb) -> cb.disjunction());
                }
                return scoped.and(ActionUnitSpec.unitIsRoot()).and(ActionUnitSpec.idIn(closure));
            }
            return scoped.and(ActionUnitSpec.unitIsRoot());
        }

        return base.and(userFilterSpecs(filters));
    }

    private Specification<ActionUnit> userFilterSpecs(FilterDTO filters) {
        Specification<ActionUnit> specs = Specification.where(null);

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo nameFilter = filters.filterOf(ActionUnitSpec.NAME_FILTER);

        if (nameFilter != null && nameFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(ActionUnitSpec.nameContaining(nameFilter.valueAsString()));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(ActionUnitSpec.nameContaining(globalFilter.valueAsString()));
        }

        if (filters.containsColumn(ActionUnitSpec.SPATIAL_UNIT_FILTER)) {
            specs = specs.and(ActionUnitSpec.isInSpatialUnit(filters.valueAsIdListOf(ActionUnitSpec.SPATIAL_UNIT_FILTER)));
        }

        if (filters.containsColumn(ActionUnitSpec.FULL_IDENTIFIER_FILTER)) {
            specs = specs.and(ActionUnitSpec.fullIdentifierContaining(filters.valueOfAsString(ActionUnitSpec.FULL_IDENTIFIER_FILTER)));
        }

        return specs;
    }

    private Specification<ActionUnit> scopeFilterSpecs(FilterDTO filters) {
        Specification<ActionUnit> specs = Specification.where(null);
        Set<String> scopeKeys = filters.getScopeFilterKeys();

        if (scopeKeys.contains(ActionUnitSpec.SPATIAL_UNIT_FILTER) && filters.containsColumn(ActionUnitSpec.SPATIAL_UNIT_FILTER)) {
            specs = specs.and(ActionUnitSpec.isInSpatialUnit(filters.valueAsIdListOf(ActionUnitSpec.SPATIAL_UNIT_FILTER)));
        }

        if (scopeKeys.contains(ActionUnitSpec.FULL_IDENTIFIER_FILTER) && filters.containsColumn(ActionUnitSpec.FULL_IDENTIFIER_FILTER)) {
            specs = specs.and(ActionUnitSpec.fullIdentifierContaining(filters.valueOfAsString(ActionUnitSpec.FULL_IDENTIFIER_FILTER)));
        }

        return specs;
    }

    private Collection<Long> resolveAncestorClosure(InstitutionDTO institutionDTO, FilterDTO filters) {
        if (filters.getAncestorClosure() != null) {
            return filters.getAncestorClosure();
        }
        Specification<ActionUnit> matchSpecs = ActionUnitSpec.belongsToInstitution(institutionDTO.getId())
                .and(userFilterSpecs(filters));
        List<Long> matchIds = actionUnitRepository.findAll(matchSpecs).stream()
                .map(ActionUnit::getId)
                .toList();
        Set<Long> closure = matchIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(actionUnitRepository.findAncestorClosure(matchIds.toArray(Long[]::new)));
        filters.setAncestorClosure(closure);
        filters.setMatchIds(new HashSet<>(matchIds));
        return closure;
    }

    public Set<Long> computeAncestorClosure(InstitutionDTO institutionDTO, FilterDTO filters) {
        if (!filters.isRootOnly() || !filters.hasUserFilters()) {
            return Collections.emptySet();
        }
        return new HashSet<>(resolveAncestorClosure(institutionDTO, filters));
    }

    public List<ActionUnitDTO> findMatchingInInstitutionByName(InstitutionDTO institution, String query, int limit) {
        Specification<ActionUnit> specs = ActionUnitSpec.belongsToInstitution(institution.getId());
        specs = specs.and(TextSearchSpec.matching("name", query, FilterDTO.FilterType.START_WITH));

        return ExactMatchFirst.findAll(actionUnitRepository, specs, PageRequest.ofSize(limit), "name", query)
                .stream()
                .map(actionUnitMapper::convert)
                .toList();
    }

    /**
     * Projects (action units) visible in the API: units the person is allowed to display
     * through the profile permission system ({@link ActionUnitSpec#visibleToPerson(Long)}),
     * restricted to the given institutions.
     *
     * @param personId       the person whose display permissions filter the results
     * @param organizationId when non-null, restrict to this institution (caller must ensure it is allowed for the person)
     */
    // TODO [ARCH] Définir avec Julien si on décide que les service n'expose que des DTOs domaine et si c'est le rôle des package ui de mapper vers le DTO API
    @Transactional(readOnly = true)
    public Page<AccessibleProjectForApi> findAccessibleProjects(
            Long personId,
            Set<Long> accessibleInstitutionIds,
            Long organizationId,
            String search,
            Pageable pageable) {
        return pageAccessibleProjects(personId, accessibleInstitutionIds, organizationId,
                AccessibleProjectsQuery.of(search, pageable));
    }

    /**
     * Same, with what the list asks beyond the search and the page ({@link AccessibleProjectsQuery}): how the
     * search matches, the sort by recording-unit count, the per-column filters and the form-field query.
     * Either way the projects whose name, identifier or full identifier is exactly the search come first.
     */
    @Transactional(readOnly = true)
    public Page<AccessibleProjectForApi> findAccessibleProjects(
            Long personId,
            Set<Long> accessibleInstitutionIds,
            Long organizationId,
            AccessibleProjectsQuery query) {
        return pageAccessibleProjects(personId, accessibleInstitutionIds, organizationId, query);
    }

    /**
     * The page {@link #findAccessibleProjects} returns — not itself transactional, so the public
     * overloads share it without calling one another through {@code this}.
     */
    private Page<AccessibleProjectForApi> pageAccessibleProjects(
            Long personId,
            Set<Long> accessibleInstitutionIds,
            Long organizationId,
            AccessibleProjectsQuery query) {
        Pageable pageable = query.pageable();
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Specification<ActionUnit> spec = accessibleProjectsSpec(personId, accessibleInstitutionIds, organizationId,
                ActionUnitSpec.projectSearch(query.search(), query.searchType()), query.filter())
                .and(query.fieldQuery().specificationFor());
        if (query.recordingUnitCountOrder() != null) {
            spec = spec.and(ActionUnitSpec.orderByRecordingUnitCount(query.recordingUnitCountOrder()));
        }

        return toAccessibleProjects(ExactMatchFirst.findAll(actionUnitRepository, spec, pageable,
                ActionUnitSpec.PROJECT_SEARCH_ATTRIBUTES, query.search()), pageable);
    }

    /** A page of projects with what the API lists of each: its recording-unit and child counts, its forms warmed. */
    private Page<AccessibleProjectForApi> toAccessibleProjects(Page<ActionUnit> page, Pageable pageable) {
        List<Long> ids = page.getContent().stream().map(ActionUnit::getId).toList();
        warmFormCollections(ids);
        Map<Long, Long> ruByProject = ids.isEmpty()
                ? Map.of()
                : countingMap(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(ids));
        Map<Long, Long> childrenByProject = ids.isEmpty()
                ? Map.of()
                : countingMap(actionUnitRepository.countChildActionUnitsByParentIds(ids));

        List<AccessibleProjectForApi> mapped = page.getContent().stream()
                .map(au -> new AccessibleProjectForApi(
                        actionUnitMapper.convert(au),
                        ruByProject.getOrDefault(au.getId(), 0L),
                        childrenByProject.getOrDefault(au.getId(), 0L)))
                .toList();

        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    /**
     * The tree view of the accessible projects: the projects none of whose parents is accessible (a project
     * under one the person cannot see is a root for them), or with {@code parentId} that project's accessible
     * children. A search or a filter keeps the matches and their accessible ancestors
     * ({@link HierarchyTreeQuery}); the page carries the tree flags of its rows and the match count.
     */
    @Transactional(readOnly = true)
    public TreePage<AccessibleProjectForApi> findAccessibleProjectsTree(ProjectTreeQuery query) {
        ProjectTreeQuery.Selection selection = query.selection();
        Pageable pageable = selection.pageable();
        Set<Long> institutions = query.accessibleInstitutionIds();
        if (institutions == null || institutions.isEmpty()) {
            return new TreePage<>(List.of(), pageable, 0, 0);
        }
        Collection<Long> institutionScope = query.organizationId() != null ? List.of(query.organizationId()) : institutions;
        Specification<ActionUnit> scope = Specification.where(ActionUnitSpec.institutionIdIn(institutionScope))
                .and(ActionUnitSpec.visibleToPerson(query.personId()));
        if (query.spatialContextId() != null) {
            scope = scope.and(ActionUnitSpec.actionUnitInSpatialUnit(query.spatialContextId()));
        }

        FieldQuery fieldQuery = selection.fieldQuery();
        String search = selection.search();
        ProjectListFilter filter = selection.filter();
        boolean narrowed = (search != null && !search.isBlank())
                || (filter != null && !filter.isEmpty())
                || fieldQuery.filtered();
        Specification<ActionUnit> matches = narrowed ? matchesOf(search, filter, fieldQuery) : null;
        Specification<ActionUnit> ordering = Specification.where(fieldQuery.<ActionUnit>orderingSpecificationFor());
        if (selection.recordingUnitCountOrder() != null) {
            ordering = ordering.and(ActionUnitSpec.orderByRecordingUnitCount(selection.recordingUnitCountOrder()));
        }
        return new HierarchyTreeQuery<>(actionUnitHierarchy()).page(scope, matches, ordering, query.parentId(), pageable);
    }

    private static Specification<ActionUnit> matchesOf(String search, ProjectListFilter filter, FieldQuery fieldQuery) {
        Specification<ActionUnit> matches = Specification.where(ActionUnitSpec.projectSearch(search));
        if (filter != null && !filter.isEmpty()) {
            matches = matches.and(ActionUnitFilterSpec.fromFilter(filter));
        }
        return matches.and(fieldQuery.specificationFor());
    }

    /** Projects as {@link HierarchyTreeQuery} sees a hierarchical type. */
    private HierarchyTreeQuery.Hierarchy<ActionUnit, AccessibleProjectForApi> actionUnitHierarchy() {
        return new HierarchyTreeQuery.Hierarchy<>() {
            @Override
            public Specification<ActionUnit> hasParent(long parentId) {
                return ActionUnitSpec.hasParent(parentId);
            }

            @Override
            public Specification<ActionUnit> hasNoParentIn(Specification<ActionUnit> scope) {
                return ActionUnitSpec.hasNoParentIn(scope);
            }

            @Override
            public Specification<ActionUnit> idIn(Collection<Long> ids) {
                return ActionUnitSpec.idIn(ids);
            }

            @Override
            public List<Object[]> parentEdges(Collection<Long> childIds) {
                return actionUnitRepository.findParentEdges(childIds);
            }

            @Override
            public List<Object[]> childEdges(Collection<Long> parentIds) {
                return actionUnitRepository.findChildEdges(parentIds);
            }

            @Override
            public List<Long> ids(Specification<ActionUnit> specs) {
                return actionUnitRepository.findAll(specs).stream().map(ActionUnit::getId).toList();
            }

            @Override
            public long count(Specification<ActionUnit> specs) {
                return actionUnitRepository.count(specs);
            }

            @Override
            public Page<AccessibleProjectForApi> page(Specification<ActionUnit> specs, Pageable page) {
                return toAccessibleProjects(actionUnitRepository.findAll(specs, page), page);
            }

            @Override
            public Long idOf(AccessibleProjectForApi row) {
                return row.actionUnit().getId();
            }
        };
    }

    /**
     * The base "accessible projects" filter — institution scope, display permission, free-text
     * search, per-column filters — shared by {@link #findAccessibleProjects} (a page of it) and
     * {@link #findSiblingProject} (one cursored row past/before a given one). Factored out so the
     * sibling lookup can never drift from what the list itself considers visible: reusing
     * {@code findNextByInstitution}-style institution-only filtering here would let a sibling leak
     * past a person's actual permissions.
     */
    @NonNull
    /**
     * Ids of the institution's projects on which the person holds a PROJECT-scoped profile — what a
     * person without institution-wide access ({@code ProfilePermissionService#canViewInstitutionData})
     * may see, same rule as {@link ActionUnitSpec#visibleToPerson}'s project branch.
     */
    @Transactional(readOnly = true)
    public List<Long> findMemberProjectIds(Long personId, Long institutionId) {
        Specification<ActionUnit> spec = Specification.where(ActionUnitSpec.institutionIdIn(List.of(institutionId)))
                .and(ActionUnitSpec.hasProjectMembership(personId));
        return actionUnitRepository.findAll(spec).stream().map(ActionUnit::getId).toList();
    }

    private Specification<ActionUnit> accessibleProjectsSpec(
            Long personId,
            Set<Long> accessibleInstitutionIds,
            Long organizationId,
            String search,
            ProjectListFilter filter) {
        return accessibleProjectsSpec(personId, accessibleInstitutionIds, organizationId,
                ActionUnitSpec.projectSearch(search), filter);
    }

    /**
     * The projects the person can see in the scope, matching {@code search} and {@code filter}.
     */
    private Specification<ActionUnit> accessibleProjectsSpec(
            Long personId,
            Set<Long> accessibleInstitutionIds,
            Long organizationId,
            Specification<ActionUnit> search,
            ProjectListFilter filter) {
        Collection<Long> institutionScope = organizationId != null
                ? List.of(organizationId)
                : accessibleInstitutionIds;

        Specification<ActionUnit> spec = Specification.where(ActionUnitSpec.institutionIdIn(institutionScope))
                .and(ActionUnitSpec.visibleToPerson(personId))
                .and(search);
        if (filter != null && !filter.isEmpty()) {
            spec = spec.and(ActionUnitFilterSpec.fromFilter(filter));
        }
        return spec;
    }

    /**
     * The project strictly {@code forward}/backward of {@code currentId} in the caller's own
     * accessible list order (same spec as {@link #findAccessibleProjects}, plus a cursor on
     * {@code sortField}/{@code currentValue}/{@code currentId} — see {@link ActionUnitSpec#cursor}).
     * Wraps around to the opposite end when there is no such neighbour, matching
     * {@code AbstractSingleEntityPanel.goToNext/goToPrevious}'s own JSF behaviour. Returns
     * {@link Optional#empty()} when the caller's accessible set has no OTHER project at all
     * (the wrapped-around candidate would just be {@code currentId} itself) — deliberately not
     * JSF's own "loops back to itself" behaviour, since a self-link is not a useful sibling.
     *
     * @param cursor where the current project stands: its {@code sortField} is a real, non-null JPA path
     *               — {@code recordingUnitCount} and other Specification-only synthetic sorts have no
     *               cursor and must be rejected by the caller before reaching here (see
     *               {@code ProjectApiService#requireCursorableProjectSort}); its {@code currentValue} is
     *               already read off the current entity by the caller.
     */
    @Transactional(readOnly = true)
    public Optional<ActionUnitDTO> findSiblingProject(
            Long personId,
            Set<Long> accessibleInstitutionIds,
            Long organizationId,
            String search,
            ProjectListFilter filter,
            ProjectCursor cursor,
            boolean forward) {
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            return Optional.empty();
        }

        Specification<ActionUnit> baseSpec =
                accessibleProjectsSpec(personId, accessibleInstitutionIds, organizationId, search, filter);
        String sortField = cursor.sortField();
        Sort.Direction sortDirection = cursor.sortDirection();
        Long currentId = cursor.currentId();

        // Walking backwards reverses both the sort field's own direction and the id tie-break —
        // ActionUnitSpec.cursor's own "forward" flag already accounts for the field, id here just
        // has to match the direction cursor() compares it in.
        Sort.Direction reversedDirection = sortDirection == Sort.Direction.ASC ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort.Direction walkFieldDirection = forward ? sortDirection : reversedDirection;
        Sort.Direction idDirection = forward ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable onePage = PageRequest.of(0, 1, Sort.by(walkFieldDirection, sortField).and(Sort.by(idDirection, "id")));

        Specification<ActionUnit> cursored = baseSpec.and(
                ActionUnitSpec.cursor(sortField, sortDirection, cursor.currentValue(), currentId, forward));
        List<ActionUnit> page = actionUnitRepository.findAll(cursored, onePage).getContent();

        if (page.isEmpty()) {
            // Wrap-around: same order, no cursor, first row of that order is the opposite end.
            page = actionUnitRepository.findAll(baseSpec, onePage).getContent();
        }

        return page.stream()
                .filter(au -> !au.getId().equals(currentId))
                .findFirst()
                .map(actionUnitMapper::convert);
    }

    /**
     * Initialise en trois requêtes bornées les collections que {@code ActionUnitMapper} mappe pour chaque
     * ligne ({@code periods}, {@code subjects}, {@code spatialContext}) : sans cela, mapper une page de 25
     * projets déclenche 75 requêtes supplémentaires. Les entités de la page étant déjà gérées par le même
     * contexte de persistance, le résultat de ces requêtes est ignoré — seul l'effet d'initialisation
     * compte.
     */
    private void warmFormCollections(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        actionUnitRepository.findWithPeriodsByIdIn(ids);
        actionUnitRepository.findWithSubjectsByIdIn(ids);
        actionUnitRepository.findWithSpatialContextByIdIn(ids);
    }

    /**
     * Détail d'un projet (action unit) si son institution est dans {@code accessibleInstitutionIds}.
     * <ul>
     *     <li>{@code idOrKey} est la clé primaire {@code action_unit_id}, en chiffres décimaux : c'est la seule
     *     clé de l'API (ni identifiant complet, ni identifiant court).</li>
     * </ul>
     *
     * @throws ActionUnitNotFoundException clé inconnue, ou projet hors périmètre (comportement type 404)
     */
    // TODO [ARCH] Définir avec Julien si on décide que les service n'expose que des DTOs domaine et si c'est le rôle des package ui de mapper vers le DTO API
    @Transactional(readOnly = true)
    public AccessibleProjectForApi findAccessibleProjectByKey(String idOrKey, Set<Long> accessibleInstitutionIds) {
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            throw new ActionUnitNotFoundException("No institution scope for current user");
        }
        ActionUnitDTO dto = loadProjectDtoForLookupKey(idOrKey);
        InstitutionDTO inst = dto.getCreatedByInstitution();
        if (inst == null || inst.getId() == null || !accessibleInstitutionIds.contains(inst.getId())) {
            throw new ActionUnitNotFoundException("Project not found or not accessible");
        }
        long projectId = dto.getId();
        List<Long> ids = List.of(projectId);
        Map<Long, Long> ruByProject = countingMap(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(ids));
        Map<Long, Long> childrenByProject = countingMap(actionUnitRepository.countChildActionUnitsByParentIds(ids));
        return new AccessibleProjectForApi(
                dto,
                ruByProject.getOrDefault(projectId, 0L),
                childrenByProject.getOrDefault(projectId, 0L));
    }

    /**
     * Supprime une unité d'action (projet) et tout son contenu (unités d'enregistrement, mobiliers, documents,
     * phases, contenants, études, configuration du projet, membres).
     * Refuse s'il existe des sous-projets ou si du contenu est lié à un autre projet.
     *
     * @throws IllegalStateException si le projet n'est pas supprimable
     */
    @Caching(evict = {
            @CacheEvict({
                    "InstitutionHasRootChildrenAU",
                    "InstitutionHasRootChildrenSU",
                    "ActionUnitHasChildrenInInstitution"
            }),
            @CacheEvict(value = "MyActionUnits", allEntries = true)
    })
    @Transactional
    public void deleteProjectWithContent(long actionUnitId) {
        UserInfo info = ExecutionContextHolder.get();
        if (!profilePermissionService.hasDeleteProjectPermission(info, actionUnitId)) {
            throw new ForbiddenOperationException("You are not allowed to delete this action unit");
        }
        Map<Long, Long> childrenByProject = countingMap(
                actionUnitRepository.countChildActionUnitsByParentIds(List.of(actionUnitId)));
        if (childrenByProject.getOrDefault(actionUnitId, 0L) > 0) {
            throw new IllegalStateException("Impossible de supprimer : le projet contient des sous-projets");
        }
        ActionUnit project = actionUnitRepository.findById(actionUnitId)
                .orElseThrow(() -> new ActionUnitNotFoundException(NOT_FOUND_BY_ID + actionUnitId));
        Long projectArkId = project.getArk() != null ? project.getArk().getInternalId() : null;

        List<Long> arkIds = new ArrayList<>(projectDeletionService.deleteContent(actionUnitId));
        if (projectArkId != null) {
            arkIds.add(projectArkId);
        }

        personProfileAssignmentRepository.deleteAllByProfileActionUnitId(actionUnitId);
        profileRepository.deleteAllByActionUnitId(actionUnitId);
        recordingUnitIdLabelRepository.deleteAllByActionUnitId(actionUnitId);
        actionUnitRepository.deleteHierarchyLinksForActionUnit(actionUnitId);
        actionUnitRepository.deleteSpatialContextLinksForActionUnit(actionUnitId);
        actionUnitRepository.deleteById(actionUnitId);
        projectDeletionService.deleteArks(arkIds);
    }

    /** The project of a database id; anything else (a full identifier, a short one) is not a key of the API. */
    private ActionUnitDTO loadProjectDtoForLookupKey(String id) {
        String key = id == null ? "" : id.trim();
        if (key.isEmpty() || !key.chars().allMatch(Character::isDigit)) {
            throw new ActionUnitNotFoundException(NOT_FOUND_BY_ID + key);
        }
        long projectId;
        try {
            projectId = Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new ActionUnitNotFoundException(NOT_FOUND_BY_ID + key);
        }
        ActionUnit actionUnit = actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new ActionUnitNotFoundException(NOT_FOUND_BY_ID + projectId));
        return convertWithCount(actionUnit);
    }

    private static Map<Long, Long> countingMap(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> m = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                m.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        return m;
    }
    public Set<ActionUnitDTO> findAllByActionManager(PersonDTO user) {
        if (user == null || user.getId() == null) {
            return Collections.emptySet();
        }

        Set<ActionUnit> actionUnits = actionUnitRepository.findAllByCreatedById(user.getId());

        return actionUnits.stream()
                .map(actionUnitMapper::convert)
                .collect(Collectors.toSet());
    }

    /**
     * Every action unit the person is a PROJECT-scoped member of, across every institution — no
     * institution restriction and no limit, unlike {@link #findByTeamMember}. Used to filter the global
     * project list by an arbitrary member.
     *
     * @param member the person whose projects to find
     * @return the action units the person is a member of
     */
    public Set<ActionUnitDTO> findAllByTeamMember(PersonDTO member) {
        if (member == null || member.getId() == null) {
            return Collections.emptySet();
        }
        List<ActionUnit> actionUnits = actionUnitRepository.findAll(ActionUnitSpec.hasProjectMembership(member.getId()));
        return actionUnits.stream()
                .map(this::convertWithCount)
                .collect(Collectors.toSet());
    }

    /**
     * @param personId the person whose projects to count
     * @return how many action units, across every institution, the person is a PROJECT-scoped member of
     */
    public long countByTeamMember(Long personId) {
        if (personId == null) {
            return 0;
        }
        return actionUnitRepository.count(ActionUnitSpec.hasProjectMembership(personId));
    }

    /**
     * All action units of every institution visible to the person, regardless of their role there —
     * whether they may manage any given one of them is a separate, per-action-unit permission check
     * (see {@code ProfilePermissionService#hasActionUnitWritePermission}), not a listing filter.
     *
     * @param user the person whose visible action units to find
     * @return the action units the person can see
     */
    public Set<ActionUnitDTO> findAllEditableByPerson(PersonDTO user) {
        if (user == null || user.getId() == null) {
            return Collections.emptySet();
        }

        Set<ActionUnitDTO> actionUnits = new HashSet<>();

        for (InstitutionDTO institutionDTO : institutionService.findInstitutionsOfPerson(user)) {
            List<ActionUnit> institutionActionUnits = actionUnitRepository.findAllByCreatedByInstitutionId(institutionDTO.getId());
            actionUnits.addAll(institutionActionUnits.stream().map(actionUnitMapper::convert).collect(Collectors.toSet()));
        }

        return actionUnits;
    }

    public List<ActionUnitDTO> findAllByPersonInInstitutionByNameCompletionWithEditPerm(String nameInput, int limit) {
        UserInfo info = ExecutionContextHolder.getNonNull();

        Specification<ActionUnit> spec = Specification.where(ActionUnitSpec.belongsToInstitution(info.getInstitution().getId()));
        spec = spec.and(ActionUnitSpec.nameStartsWith(nameInput));
        spec = spec.and(ActionUnitSpec.withEditPermission(info.getInstitution(), info.getUser()));
        Page<ActionUnit> page = actionUnitRepository.findAll(spec, PageRequest.of(0, limit));

        return page.getContent()
                .stream()
                .map(actionUnitMapper::convert)
                .toList();
    }
}
