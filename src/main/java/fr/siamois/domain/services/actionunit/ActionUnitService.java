package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdLabelRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
@RequiredArgsConstructor
public class ActionUnitService implements ArkEntityService {

    private final ActionUnitRepository actionUnitRepository;
    private final RecordingUnitRepository recordingUnitRepository;
    private final ConceptService conceptService;
    private final ActionCodeRepository actionCodeRepository;
    private final ActionUnitMapper actionUnitMapper;
    private final PersonMapper personMapper;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ConceptMapper conceptMapper;
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileRepository profileRepository;
    private final DocumentRepository documentRepository;
    private final PendingActionUnitRepository pendingActionUnitRepository;
    private final RecordingUnitIdCounterRepository recordingUnitIdCounterRepository;
    private final RecordingUnitIdLabelRepository recordingUnitIdLabelRepository;
    private final ProfileService profileService;
    private final PersonProfileAssignmentService personProfileAssignmentService;
    private final ProfileMapper profileMapper;


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
                    .orElseThrow(() -> new ActionUnitNotFoundException("ActionUnit not found with ID: " + id));
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


        Optional<ActionUnit> existingName = actionUnitRepository.findByNameAndCreatedByInstitutionId(actionUnitDTO.getName(), info.getInstitution().getId());
        if (existingName.isPresent() && (actionUnitDTO.getId() == null || !existingName.get().getId().equals(actionUnitDTO.getId()))) {
            throw new ActionUnitAlreadyExistsException(
                    "name",
                    String.format("Action unit with name %s already exist", actionUnitDTO.getName()));
        }

        Optional<ActionUnit> existingId = actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(actionUnitDTO.getIdentifier(), info.getInstitution().getId());
        if (existingId.isPresent() && (actionUnitDTO.getId() == null || !existingId.get().getId().equals(actionUnitDTO.getId()))) {
            throw new ActionUnitAlreadyExistsException(
                    "identifier",
                    String.format("Action unit with identifier %s already exist", actionUnitDTO.getIdentifier()));
        }


        actionUnitDTO.setCreatedByInstitution(info.getInstitution());
        if (actionUnitDTO.getCreationTime() == null) {
            actionUnitDTO.setCreationTime(OffsetDateTime.now(ZoneId.systemDefault()));
        }

        // Generate unique identifier if not presents
        if (actionUnitDTO.getFullIdentifier() == null) {
            if (actionUnitDTO.getIdentifier() == null) {
                throw new NullActionUnitIdentifierException("ActionUnit identifier must be set");
            }
            actionUnitDTO.setFullIdentifier(actionUnitDTO.getIdentifier());
        }

        // Add concept
        ActionUnit actionUnit = actionUnitMapper.invertConvert(actionUnitDTO);
        Concept type = conceptService.saveOrGetConcept(typeConceptDTO);
        actionUnit.setType(type);
        Person user = personMapper.invertConvert(info.getUser());
        actionUnit.setCreatedBy(user);

        if (actionUnitDTO.getMainLocation() != null && actionUnitDTO.getMainLocation().getId() == null) {
            SpatialUnit toSave = new SpatialUnit();
            toSave.setCategory(actionUnit.getMainLocation().getCategory());
            toSave.setName(actionUnitDTO.getMainLocation().getName());
            toSave.setCreatedBy(actionUnit.getCreatedBy());
            toSave.setCode(actionUnitDTO.getMainLocation().getCode());
            toSave.setCreatedByInstitution(actionUnit.getCreatedByInstitution());
            toSave = spatialUnitRepository.save(toSave);
            actionUnit.setMainLocation(toSave);
        }
        if (actionUnitDTO.getSpatialContext() != null) {
            Set<SpatialUnit> persistentContext = new HashSet<>();

            for (SpatialUnitSummaryDTO summary : actionUnitDTO.getSpatialContext()) {
                if (summary.getId() == null) {
                    // CAS : Nouveau lieu (ex: issu de l'API INSEE)
                    SpatialUnit toSave = new SpatialUnit();
                    toSave.setName(summary.getName());
                    toSave.setCode(summary.getCode());
                    toSave.setCategory(conceptMapper.invertConvert(summary.getCategory()));

                    // On réutilise les métadonnées de l'unité parente
                    toSave.setCreatedBy(actionUnit.getCreatedBy());
                    toSave.setCreatedByInstitution(actionUnit.getCreatedByInstitution());

                    // Sauvegarde immédiate pour obtenir un ID
                    toSave = spatialUnitRepository.save(toSave);
                    persistentContext.add(toSave);
                } else {
                    // CAS : Lieu existant en base
                    spatialUnitRepository.findById(summary.getId())
                            .ifPresent(persistentContext::add);
                }
            }

            // Mise à jour de la relation ManyToMany ou OneToMany
            actionUnit.setSpatialContext(persistentContext);
        }


        try {
            return actionUnitRepository.save(actionUnit);
        } catch (RuntimeException e) {
            throw new FailedActionUnitSaveException(e.getMessage());
        }
    }

    /**
     * Save an ActionUnit with a transaction.
     *
     * @param info        User information containing the user and institution
     * @param actionUnit  The ActionUnit to save
     * @param typeConcept The concept type of the ActionUnit
     * @return The saved ActionUnit
     */
    @Transactional
    @CacheEvict(value = "MyActionUnits", allEntries = true)
    public ActionUnitDTO save(UserInfo info, ActionUnitDTO actionUnit, ConceptDTO typeConcept)
            throws ActionUnitAlreadyExistsException {
        ActionUnitDTO savedDTO = actionUnitMapper.convert(saveNotTransactional(info, actionUnit, typeConcept));
        assignRoles(info, actionUnit, savedDTO);
        return savedDTO;
    }

    private void assignRoles(UserInfo info, ActionUnitDTO actionUnit, ActionUnitDTO savedDTO) {
        Map<String, Profile> profiles = createProjectProfiles(savedDTO);
        assignAsOrganisationMember(info, actionUnit);
        assignAsProjectMember(info, actionUnit, profiles);
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
     * Find all ActionCodes that contain the given query string in their code, ignoring case.
     *
     * @param query The query string to search for in ActionCodes
     * @return A list of ActionCodes that match the query
     */
    public List<ActionCode> findAllActionCodeByCodeIsContainingIgnoreCase(String query) {
        return actionCodeRepository.findAllByCodeIsContainingIgnoreCase(query);
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
        try {
            return actionUnitMapper.convert(
                    actionUnitRepository.save(Objects.requireNonNull(
                            actionUnitMapper.invertConvert((ActionUnitDTO) toSave))));
        } catch (DataIntegrityViolationException e) {
            throw new FailedActionUnitSaveException(e.getMessage());
        }
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

    /**
     * Count the number of ActionUnits associated with a specific SpatialUnit.
     *
     * @param spatialUnit The SpatialUnit to count ActionUnits for
     * @return The count of ActionUnits associated with the SpatialUnit
     */
    public Integer countBySpatialContext(SpatialUnitDTO spatialUnit) {
        return actionUnitRepository.countBySpatialContext(spatialUnit.getId());
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

        // Cycle through the enum values
        switch (actionUnit.getValidated()) {
            case INCOMPLETE:
                actionUnit.setValidated(ValidationStatus.COMPLETE);
                break;
            case COMPLETE:
                actionUnit.setValidated(ValidationStatus.VALIDATED);
                break;
            case VALIDATED:
                actionUnit.setValidated(ValidationStatus.INCOMPLETE);
                break;
            default:
                throw new IllegalStateException("Unknown status: " + actionUnit.getValidated());
        }


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

    /**
     * Get all ActionUnit in the institution that are linked to a spatial unit
     *
     * @param spatialId the spatial unit id
     * @return The list of ActionUnit
     */
    public List<ActionUnitDTO> findBySpatialContext(Long spatialId) {
        List<ActionUnit> res = actionUnitRepository.findBySpatialContext(spatialId);
        return res.stream()
                .map(actionUnitMapper::convert)
                .toList();
    }


    @Cacheable(value = "MyActionUnits", key = "#member.id + '-' + #institution.id + '-' + #limit")
    public List<ActionUnitDTO> findByTeamMember(PersonDTO member, InstitutionDTO institution, long limit) {
        Specification<ActionUnit> specs = prepareSpecs(institution, new FilterDTO());
        Pageable pageLimit = PageRequest.of(0, Math.toIntExact(limit));

        specs = specs.and(ActionUnitSpec.visibleToPerson(member.getId()));

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
        Page<ActionUnit> res = actionUnitRepository.findAll(specs, pageable);
        return res.map(this::convertWithCount);
    }


    public int countSearchResultsInSpatialUnit(InstitutionDTO institutionDTO, SpatialUnitDTO spatialUnitDTO,
                                               FilterDTO filters) {
        Specification<ActionUnit> specs = prepareSpecs(institutionDTO, filters);
        specs = specs.and(ActionUnitSpec.actionUnitInSpatialUnit(spatialUnitDTO.getId()));
        return Math.toIntExact(actionUnitRepository.count(specs));
    }

    public Page<ActionUnitDTO> searchActionUnitsInSpatialUnit(InstitutionDTO institutionDTO,
                                                              SpatialUnitDTO spatialUnitDTO, FilterDTO filters, Pageable pageable) {
        Specification<ActionUnit> specs = prepareSpecs(institutionDTO, filters);
        specs = specs.and(ActionUnitSpec.actionUnitInSpatialUnit(spatialUnitDTO.getId()));
        Page<ActionUnit> res = actionUnitRepository.findAll(specs, pageable);
        if (filters.containsColumn("name")) {
            log.trace("{} éléments trouvées pour {} (Page {}/{})", res.getTotalElements(), filters.valueOfAsString("name"), res.getNumber() + 1, res.getTotalPages());
        }
        return res.map(this::convertWithCount);
    }

    private ActionUnitDTO convertWithCount(ActionUnit au) {
        ActionUnitDTO dto = actionUnitMapper.convert(au);
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
            if (filters.hasUserFilters()) {
                Collection<Long> closure = resolveAncestorClosure(institutionDTO, filters);
                if (closure.isEmpty()) {
                    return base.and((root, q, cb) -> cb.disjunction());
                }
                return base.and(ActionUnitSpec.unitIsRoot()).and(ActionUnitSpec.idIn(closure));
            }
            return base.and(ActionUnitSpec.unitIsRoot());
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
        specs = specs.and(ActionUnitSpec.nameContaining(query));

        return actionUnitRepository.findAll(specs, PageRequest.ofSize(limit))
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
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Collection<Long> institutionScope = organizationId != null
                ? List.of(organizationId)
                : accessibleInstitutionIds;

        Specification<ActionUnit> spec = Specification.where(ActionUnitSpec.institutionIdIn(institutionScope))
                .and(ActionUnitSpec.visibleToPerson(personId))
                .and(ActionUnitSpec.projectSearch(search));

        Page<ActionUnit> page = actionUnitRepository.findAll(spec, pageable);

        List<Long> ids = page.getContent().stream().map(ActionUnit::getId).toList();
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
     * Détail d'un projet (action unit) si son institution est dans {@code accessibleInstitutionIds}.
     * <ul>
     *     <li>Si {@code idOrKey} n'est composé que de chiffres décimaux : clé primaire {@code action_unit_id}.</li>
     *     <li>Sinon : {@link ActionUnitRepository#findByFullIdentifier(String)} (identifiant métier complet).</li>
     *     <li>Sinon encore : {@link ActionUnitRepository#findByIdentifierAndCreatedByInstitutionId(String, Long)}
     *     pour chaque institution accessible (ex. identifiant court {@code C309_01} dans une org).</li>
     * </ul>
     * Les identifiants contenant des « / » doivent être correctement encodés dans l'URL (ex. {@code %2F}).
     *
     * @throws ActionUnitNotFoundException clé inconnue, ou projet hors périmètre (comportement type 404)
     */
    // TODO [ARCH] Définir avec Julien si on décide que les service n'expose que des DTOs domaine et si c'est le rôle des package ui de mapper vers le DTO API
    @Transactional(readOnly = true)
    public AccessibleProjectForApi findAccessibleProjectByKey(String idOrKey, Set<Long> accessibleInstitutionIds) {
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            throw new ActionUnitNotFoundException("No institution scope for current user");
        }
        ActionUnitDTO dto = loadProjectDtoForLookupKey(idOrKey, accessibleInstitutionIds);
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
     * Supprime une unité d'action (projet) vide : aucune unité d'enregistrement, aucun enfant dans la hiérarchie.
     * Nettoie les lignes dépendantes pour respecter les contraintes de clés étrangères.
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
    public void deleteProjectWhenEmpty(long actionUnitId) {
        if (recordingUnitRepository.countByActionUnit_Id(actionUnitId) > 0) {
            throw new IllegalStateException("Impossible de supprimer : le projet contient des unités d'enregistrement");
        }
        Map<Long, Long> childrenByProject = countingMap(
                actionUnitRepository.countChildActionUnitsByParentIds(List.of(actionUnitId)));
        if (childrenByProject.getOrDefault(actionUnitId, 0L) > 0) {
            throw new IllegalStateException("Impossible de supprimer : le projet contient des sous-projets");
        }
        personProfileAssignmentRepository.deleteAllByProfileActionUnitId(actionUnitId);
        profileRepository.deleteAllByActionUnitId(actionUnitId);
        pendingActionUnitRepository.deleteAllByActionUnitId(actionUnitId);
        recordingUnitIdCounterRepository.deleteAllByConfigActionUnitId(actionUnitId);
        recordingUnitIdLabelRepository.deleteAllByActionUnitId(actionUnitId);
        actionUnitRepository.deleteFormMappingsForActionUnit(actionUnitId);
        actionUnitRepository.deleteSecondaryActionCodeLinksForActionUnit(actionUnitId);
        actionUnitRepository.deleteHierarchyLinksForActionUnit(actionUnitId);
        actionUnitRepository.deleteSpatialContextLinksForActionUnit(actionUnitId);
        documentRepository.deleteAllActionUnitDocumentLinksByActionUnitId(actionUnitId);
        actionUnitRepository.deleteById(actionUnitId);
    }

    private ActionUnitDTO loadProjectDtoForLookupKey(String idOrKey, Set<Long> accessibleInstitutionIds) {
        String key = idOrKey == null ? "" : idOrKey.trim();
        if (key.isEmpty()) {
            throw new ActionUnitNotFoundException("Project key must not be empty");
        }
        if (key.chars().allMatch(Character::isDigit)) {
            long id = Long.parseLong(key);
            ActionUnit actionUnit = actionUnitRepository.findById(id)
                    .orElseThrow(() -> new ActionUnitNotFoundException("ActionUnit not found with ID: " + id));
            return convertWithCount(actionUnit);
        }
        Optional<ActionUnit> byFullId = actionUnitRepository.findByFullIdentifier(key);
        if (byFullId.isPresent()) {
            return actionUnitMapper.convert(byFullId.get());
        }
        for (Long institutionId : accessibleInstitutionIds) {
            Optional<ActionUnit> byShortId = actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(key, institutionId);
            if (byShortId.isPresent()) {
                return actionUnitMapper.convert(byShortId.get());
            }
        }
        throw new ActionUnitNotFoundException("ActionUnit not found with key: " + key);
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

}
