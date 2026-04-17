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
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ConceptService conceptService;
    private final ActionCodeRepository actionCodeRepository;
    private final ActionUnitMapper actionUnitMapper;
    private final PersonMapper personMapper;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ConceptMapper conceptMapper;

    /**
     * Find all Action Units by institution, name, categories, persons, and global search.
     *
     * @param institutionId The ID of the institution to filter by
     * @param name          The name to search for in Action Units
     * @param categoryIds   The IDs of categories to filter by
     * @param personIds     The IDs of persons to filter by
     * @param global        The global search term to filter by
     * @param langCode      The language code to filter by
     * @param pageable      The pagination information
     * @return A page of Action Units matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<ActionUnitDTO> findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {

        Page<ActionUnit> res = actionUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId, name, categoryIds, personIds, global, langCode, pageable);

        //wireChildrenAndParents(res.getContent());  // Load and attach spatial hierarchy relationships


        return res.map(actionUnitMapper::convert
        );
    }

    /**
     * Find all Action Units by institution, spatial unit, name, categories, persons, and global search.
     *
     * @param institutionId The ID of the institution to filter by
     * @param spatialUnitId The ID of the spatial unit to filter by
     * @param name          The name to search for in Action Units
     * @param categoryIds   The IDs of categories to filter by
     * @param personIds     The IDs of persons to filter by
     * @param global        The global search term to filter by
     * @param langCode      The language code to filter by
     * @param pageable      The pagination information
     * @return A page of Action Units matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<ActionUnitDTO> findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId, Long spatialUnitId,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {

        Page<ActionUnit> res = actionUnitRepository.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId, spatialUnitId, name, categoryIds, personIds, global, langCode, pageable);

        //wireChildrenAndParents(res.getContent());  // Load and attach spatial hierarchy relationships




        return res.map(actionUnitMapper::convert
        );
    }

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
            return actionUnitMapper.convert(actionUnit);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Save an ActionUnit without a transaction.
     *
     * @param info        User information containing the user and institution
     * @param actionUnitDTO  The ActionUnit to save
     * @param typeConceptDTO The concept type of the ActionUnit
     * @return The saved ActionUnit
     */
    public ActionUnit saveNotTransactional(UserInfo info, ActionUnitDTO actionUnitDTO, ConceptDTO typeConceptDTO)
            throws ActionUnitAlreadyExistsException {




        Optional<ActionUnit> opt = actionUnitRepository.findByNameAndCreatedByInstitutionId(actionUnitDTO.getName(), info.getInstitution().getId());
        if (opt.isPresent())
            throw new ActionUnitAlreadyExistsException(
                    "name",
                    String.format("Action unit with name %s already exist in institution %s", actionUnitDTO.getName(), info.getInstitution().getName()));

        opt = actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(actionUnitDTO.getIdentifier(), info.getInstitution().getId());
        if (opt.isPresent())
            throw new ActionUnitAlreadyExistsException(
                    "identifier",
                    String.format("Action unit with identifier %s already exist in institution %s", actionUnitDTO.getIdentifier(), info.getInstitution().getName()));




        actionUnitDTO.setCreatedByInstitution(info.getInstitution());
        if(actionUnitDTO.getCreationTime() == null) {
            actionUnitDTO.setCreationTime(OffsetDateTime.now(ZoneId.systemDefault()));
        }

        // Generate unique identifier if not presents
        if (actionUnitDTO.getFullIdentifier() == null) {
            if(actionUnitDTO.getIdentifier() == null) {
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

        if(actionUnitDTO.getMainLocation() != null && actionUnitDTO.getMainLocation().getId() == null) {
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
    public ActionUnitDTO save(UserInfo info, ActionUnitDTO actionUnit, ConceptDTO typeConcept)
            throws ActionUnitAlreadyExistsException {
        return actionUnitMapper.convert(saveNotTransactional(info, actionUnit, typeConcept));
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
    @CacheEvict({
            "InstitutionHasRootChildrenAU",
            "InstitutionHasRootChildrenSU",
            "ActionUnitHasChildrenInInstitution"
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
     * @param current The current ActionUnit to find the next one from
     * @return The next ActionUnitDTO, or the oldest one if there is no next
     */
    public ActionUnitDTO findNextByInstitution(InstitutionDTO institution, ActionUnitDTO current) {
        return actionUnitRepository
                .findNext(
                        institution.getId(), current.getCreationTime(),current.getId())
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
     * @param current The current ActionUnit to find the previous one from
     * @return The previous ActionUnitDTO, or the most recent one if there is no previous
     */
    public ActionUnitDTO findPreviousByInstitution(InstitutionDTO institution, ActionUnitDTO current) {
        return actionUnitRepository
                .findPrevious(
                        institution.getId(), current.getCreationTime(),current.getId())
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
     * @param spatialId     the spatial unit id
     * @return The list of ActionUnit
     */
    public List<ActionUnitDTO> findBySpatialContext(Long spatialId) {
        List<ActionUnit> res = actionUnitRepository.findBySpatialContext(spatialId);
        return res.stream()
                .map(actionUnitMapper::convert)
                .toList();
    }


    public List<ActionUnitDTO> findByTeamMember(PersonDTO member, InstitutionDTO institution, long limit) {
        List<ActionUnit> actionUnits = actionUnitRepository.findByTeamMemberOrCreatorAndInstitutionLimit(member.getId(), institution.getId(), limit);
        return actionUnits.stream()
                .map(actionUnitMapper::convert)
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
}
