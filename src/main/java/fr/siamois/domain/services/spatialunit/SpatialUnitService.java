package fr.siamois.domain.services.spatialunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.authorization.PermissionServiceImpl;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.RecordingUnitMapper;
import fr.siamois.mapper.SpatialUnitMapper;
import fr.siamois.mapper.SpatialUnitSummaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Service to manage SpatialUnit
 *
 * @author Grégory Bliault
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpatialUnitService implements ArkEntityService {

    private final SpatialUnitRepository spatialUnitRepository;
    private final ConceptService conceptService;
    private final ArkService arkService;
    private final InstitutionService institutionService;
    private final PersonService personService;
    private final PermissionServiceImpl permissionService;
    private final SpatialUnitMapper spatialUnitMapper;
    private final RecordingUnitMapper recordingUnitMapper;
    private final SpatialUnitSummaryMapper spatialUnitSummaryMapper;
    private final InstitutionMapper institutionMapper;
    private final ActionUnitRepository actionUnitRepository;

    /**
     * Find a spatial unit by its ID
     *
     * @param id The ID of the spatial unit
     * @return The SpatialUnit having the given ID
     * @throws SpatialUnitNotFoundException If no spatial unit are found for the given id
     * @throws RuntimeException             If the repository method returns a RuntimeException
     */
    @Transactional(readOnly = true)
    public SpatialUnitDTO findById(long id) {
        try {
            SpatialUnit spatialUnit = spatialUnitRepository.findById(id)
                    .orElseThrow(() -> new SpatialUnitNotFoundException("SpatialUnit not found with ID: " + id));
            Hibernate.initialize(spatialUnit);
            return spatialUnitMapper.convert(spatialUnit);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }


    /**
     * Restore a spatial unit from its history
     *
     * @param history The history of the spatial unit to restore
     */
    public void restore(RevisionWithInfo<SpatialUnit> history) {
        SpatialUnit revision = history.entity();
        spatialUnitRepository.save(revision);
    }

    /**
     * Find all spatial units by institution and by name containing and by categories and by global containing
     *
     * @param institutionId The ID of the institution to filter by
     * @param name          The name to filter by, can be null or empty
     * @param categoryIds   The IDs of the categories to filter by, can be null or empty
     * @param personIds     The IDs of the persons to filter by, can be null or empty
     * @param global        The global search term to filter by, can be null or empty
     * @param langCode      The language code to filter by, can be null or empty
     * @param pageable      The pageable object to control pagination
     * @return A page of SpatialUnit matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<SpatialUnitDTO> findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {

        Page<SpatialUnit> res = spatialUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId, name, categoryIds, personIds, global, langCode, pageable);

        return res.map(spatialUnitMapper::convert);
    }

    /**
     * Find all spatial units by parent and by name containing and by categories and by global containing
     *
     * @param parent      The parent spatial unit to filter by
     * @param name        The name to filter by, can be null or empty
     * @param categoryIds The IDs of the categories to filter by, can be null or empty
     * @param personIds   The IDs of the persons to filter by, can be null or empty
     * @param global      The global search term to filter by, can be null or empty
     * @param langCode    The language code to filter by, can be null or empty
     * @param pageable    The pageable object to control pagination
     * @return A page of SpatialUnit matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<SpatialUnitDTO> findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
            SpatialUnitDTO parent,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {
        Page<SpatialUnit> res = spatialUnitRepository.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                parent.getId(), name, categoryIds, personIds, global, langCode, pageable);

        return res.map(spatialUnitMapper::convert);
    }

    /**
     * Find all spatial units by child and by name containing and by categories and by global containing
     *
     * @param child       The child spatial unit to filter by
     * @param name        The name to filter by, can be null or empty
     * @param categoryIds The IDs of the categories to filter by, can be null or empty
     * @param personIds   The IDs of the persons to filter by, can be null or empty
     * @param global      The global search term to filter by, can be null or empty
     * @param langCode    The language code to filter by, can be null or empty
     * @param pageable    The pageable object to control pagination
     * @return A page of SpatialUnit matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<SpatialUnitDTO> findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
            SpatialUnitDTO child,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {
        Page<SpatialUnit> res = spatialUnitRepository.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                child.getId(), name, categoryIds, personIds, global, langCode, pageable);

        return res.map(spatialUnitMapper::convert);
    }

    /**
     * Find all spatial units of a given institution
     *
     * @param id The institution id to filter by
     * @return A list of SpatialUnit belonging to the given institution
     */
    public List<SpatialUnitDTO> findAllOfInstitution(Long id) {
        List<SpatialUnit> spatialUnits = spatialUnitRepository.findAllOfInstitution(id);
        return spatialUnits.stream()
                .map(spatialUnitMapper::convert)
                .toList();
    }

    /**
     * Find all spatial units of a given institution
     *
     * @param id The institution id to filter by
     * @return A list of SpatialUnit belonging to the given institution
     */
    public List<SpatialUnitSummaryDTO> findAllSummaryOfInstitution(Long id) {
        List<SpatialUnit> spatialUnits = spatialUnitRepository.findAllOfInstitution(id);
        return spatialUnits.stream()
                .map(spatialUnitSummaryMapper::convert)
                .toList();
    }


    /**
     * Save a new SpatialUnit
     *
     * @param info UserInfo containing user and institution information
     * @param su   The SpatialUnit to save
     * @return The saved SpatialUnit
     * @throws SpatialUnitAlreadyExistsException If a SpatialUnit with the same name already exists in the institution
     */
    @Transactional
    @CacheEvict({
            "InstitutionHasRootChildrenSU",
            "ParentHasRootChildrenSU"
    })
    public SpatialUnitDTO save(UserInfo info, SpatialUnitDTO su) throws SpatialUnitAlreadyExistsException {
        String name = su.getName();

        Optional<SpatialUnit> optSpatialUnit = spatialUnitRepository.findByNameAndInstitution(name, info.getInstitution().getId());
        if (optSpatialUnit.isPresent())
            throw new SpatialUnitAlreadyExistsException(
                    "identifier",
                    String.format("Spatial Unit with name %s already exist in institution %s", name, info.getInstitution().getName()));


        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setName(name);
        spatialUnit.setAddress(su.getAddress());
        spatialUnit.setCreatedByInstitution(institutionMapper.invertConvert(institutionService.findById(info.getInstitution().getId())));
        spatialUnit.setCreatedBy(personService.findById(info.getUser().getId()));
        spatialUnit.setCategory(conceptService.saveOrGetConcept(su.getCategory()));
        spatialUnit.setCreationTime(OffsetDateTime.now(ZoneId.systemDefault()));

        InstitutionSettings settings = institutionService.createOrGetSettingsOf(info.getInstitution());
        if (settings.hasEnabledArkConfig()) {
            Ark ark = arkService.generateAndSave(settings);
            spatialUnit.setArk(ark);
        }

        // Gestion des enfants et parents
        if (su.getChildren() != null && !su.getChildren().isEmpty()) {
            Set<SpatialUnit> children = new HashSet<>();
            for (SpatialUnitSummaryDTO childDTO : su.getChildren()) {
                SpatialUnit child = spatialUnitRepository.findById(childDTO.getId())
                        .orElseThrow(() -> new SpatialUnitNotFoundException("Child SpatialUnit not found with id: " + childDTO.getId()));
                children.add(child);
                // Ajouter l'unité spatiale courante comme parent de l'enfant
                child.getParents().add(spatialUnit);
            }
            spatialUnit.setChildren(children);
        }

        if (su.getParents() != null && !su.getParents().isEmpty()) {
            Set<SpatialUnit> parents = new HashSet<>();
            for (SpatialUnitSummaryDTO parentDTO : su.getParents()) {
                SpatialUnit parent = spatialUnitRepository.findById(parentDTO.getId())
                        .orElseThrow(() -> new SpatialUnitNotFoundException("Parent SpatialUnit not found with id: " + parentDTO.getId()));
                parents.add(parent);
                // Ajouter l'unité spatiale courante comme enfant du parent
                parent.getChildren().add(spatialUnit);
            }
            spatialUnit.setParents(parents);
        }



        spatialUnit = spatialUnitRepository.save(spatialUnit);

        return spatialUnitMapper.convert(spatialUnit);
    }

    /**
     * Find a SpatialUnit by its Ark
     *
     * @param ark The Ark to search for
     * @return An Optional containing the SpatialUnit if found, or empty if not found
     */
    public Optional<SpatialUnit> findByArk(Ark ark) {
        return spatialUnitRepository.findByArk(ark);
    }

    /**
     * Find all SpatialUnits that do not have an Ark assigned
     *
     * @param institution the institution to search within
     * @return A list of SpatialUnit that do not have an Ark assigned
     */
    @Override
    public List<SpatialUnit> findWithoutArk(Institution institution) {
        return spatialUnitRepository.findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    /**
     * Save a SpatialUnit entity
     *
     * @param toSave the {@link SpatialUnit} to save
     * @return the saved {@link SpatialUnit}
     */
    @Override
    @Transactional
    public AbstractEntityDTO save(AbstractEntityDTO toSave) {
        try {
            SpatialUnit managedSpatialUnit;
            SpatialUnit spatialUnit = spatialUnitMapper.invertConvert((SpatialUnitDTO) toSave);
            ConceptDTO conceptDTO = ((SpatialUnitDTO) toSave).getCategory();

            if (spatialUnit.getId() != null) {
                Optional<SpatialUnit> optUnit = spatialUnitRepository.findById(spatialUnit.getId());
                managedSpatialUnit = optUnit.orElseGet(SpatialUnit::new);
            } else {
                managedSpatialUnit = new SpatialUnit();
            }

            managedSpatialUnit.setName(spatialUnit.getName());
            managedSpatialUnit.setValidated(spatialUnit.getValidated());
            managedSpatialUnit.setArk(spatialUnit.getArk());
            managedSpatialUnit.setCreatedBy(spatialUnit.getCreatedBy());
            managedSpatialUnit.setGeom(spatialUnit.getGeom());
            managedSpatialUnit.setCreatedByInstitution(spatialUnit.getCreatedByInstitution());
            managedSpatialUnit.setAddress(spatialUnit.getAddress());
            // Add concept
            Concept type = conceptService.saveOrGetConcept(
                    conceptDTO);
            managedSpatialUnit.setCategory(type);

            return spatialUnitMapper.convert(spatialUnitRepository.save(managedSpatialUnit));

        } catch (RuntimeException e) {
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
    }

    /**
     * Count the number of SpatialUnits created by a specific institution
     *
     * @param id The institution to filter by
     * @return The count of SpatialUnits created in the institution
     */
    public long countByInstitutionId(Long id) {
        return spatialUnitRepository.countByCreatedByInstitutionId(id);
    }


    /**
     * Find all SpatialUnits in the system
     *
     * @return A list of all SpatialUnit
     */
    public List<SpatialUnit> findAll() {
        return new ArrayList<>(spatialUnitRepository.findAll());
    }

    /**
     * Count the number of children of a given SpatialUnit
     *
     * @param spatialUnit The SpatialUnit to count children for
     * @return The count of children for the given SpatialUnit
     */
    public long countChildrenByParent(SpatialUnit spatialUnit) {
        return spatialUnitRepository.countChildrenByParentId(spatialUnit.getId());
    }

    /**
     * Count the number of parents of a given SpatialUnit
     *
     * @param spatialUnit The SpatialUnit to count parents for
     * @return The count of parents for the given SpatialUnit
     */
    public long countParentsByChild(SpatialUnitDTO spatialUnit) {
        return spatialUnitRepository.countParentsByChildId(spatialUnit.getId());
    }

    /**
     * Find all root SpatialUnits of a given institution
     *
     * @param id The institution id to filter by
     * @return A list of root SpatialUnit that have no parents
     */
    public List<SpatialUnitDTO> findRootsOf(Long id) {
        List<SpatialUnit> result = new ArrayList<>();
        for (SpatialUnit spatialUnit : spatialUnitRepository.findAllOfInstitution(id)) {
            if (spatialUnitRepository.countParentsByChildId(spatialUnit.getId()) == 0) {
                result.add(spatialUnit);
            }
        }
        return result.stream()
                .map(spatialUnitMapper::convert)
                .toList();
    }

    public List<SpatialUnitSummaryDTO> findSummaryRootsOf(Long id) {
        List<SpatialUnit> result = new ArrayList<>();
        for (SpatialUnit spatialUnit : spatialUnitRepository.findAllOfInstitution(id)) {
            if (spatialUnitRepository.countParentsByChildId(spatialUnit.getId()) == 0) {
                result.add(spatialUnit);
            }
        }
        return result.stream()
                .map(spatialUnitSummaryMapper::convert)
                .toList();
    }

    /**
     * Find all direct children of a given SpatialUnit
     *
     * @param id The id of the SpatialUnit to find children for
     * @return A list of direct children SpatialUnitDTO of the given SpatialUnit
     */
    @Transactional(readOnly = true)
    public List<SpatialUnitDTO> findDirectChildrensOf(Long id) {
        return spatialUnitRepository.findChildrensOf(id).stream()
                .map(spatialUnitMapper::convert)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpatialUnitSummaryDTO> findDirectChildrensSummaryOf(Long id) {
        return spatialUnitRepository.findChildrensOf(id).stream()
                .map(spatialUnitSummaryMapper::convert)
                .toList();
    }


    /**
     * Find all direct parents of a given SpatialUnit
     *
     * @param id The ID of the SpatialUnit to find parents for
     * @return A list of direct parents SpatialUnitDTO of the given SpatialUnit
     */
    @Transactional(readOnly = true)
    public List<SpatialUnitDTO> findDirectParentsOf(Long id) {
        return spatialUnitRepository.findParentsOf(id).stream()
                .map(spatialUnitMapper::convert)
                .toList();
    }



    /**
     * Verify if the user has the permission to create spatial units
     *
     * @param user The user to check the permission on
     * @return True if the user has sufficient permissions
     */
    public boolean hasCreatePermission(UserInfo user) {
        return permissionService.isInstitutionManager(user)
                || permissionService.isActionManager(user);
    }

    public List<SpatialUnitSummaryDTO> getSpatialUnitOptionsFor(RecordingUnitDTO unitDTO) {
        RecordingUnit unit = recordingUnitMapper.invertConvert(unitDTO);
        assert unit != null;
        if (unit.getActionUnit() == null) return List.of();

        Optional<ActionUnit> au = actionUnitRepository.findById(unit.getActionUnit().getId());

        if(au.isEmpty()) {
            return List.of();
        }

        List<SpatialUnit> roots = new ArrayList<>(au.get().getSpatialContext());
        List<Long> rootIds = roots.stream()
                .map(SpatialUnit::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<SpatialUnit> descendants = rootIds.isEmpty()
                ? List.of()
                : spatialUnitRepository.findDescendantsUpToDepth(rootIds.toArray(Long[]::new), 10);

        LinkedHashMap<Long, SpatialUnitSummaryDTO> byId = new LinkedHashMap<>();
        roots.forEach(su -> byId.put(su.getId(), spatialUnitSummaryMapper.convert(su)));
        descendants.forEach(su -> byId.putIfAbsent(su.getId(), spatialUnitSummaryMapper.convert(su)));

        return new ArrayList<>(byId.values());
    }

    /**
     * Does this unit has children?
     * @param parentId The parent ID
     * @param institutionId the institution ID
     * @return True if they are children
     */
    public boolean existsChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        return spatialUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId);
    }

    /**
     * Does the institution have spatial units?
     * @param institutionId the institution ID
     * @return True if they are children
     */
    @Cacheable("InstitutionHasRootChildrenSU")
    public boolean existsRootChildrenByInstitution(Long institutionId) {
        return spatialUnitRepository.existsRootChildrenByInstitution(institutionId);
    }

    @Cacheable("ParentHasRootChildrenSU")
    public boolean existsRootChildrenByParent(Long spatialUnitId) {
        return spatialUnitRepository.existsRootChildrenByParent(spatialUnitId);
    }

    /**
     * Find the next place created by a specific institution after the given one.
     * If there is no next, returns the oldest one (wraps around).
     *
     * @param institution The institution to find place for
     * @param current The current place to find the next one from
     * @return The next SpatialUnitDTO, or the oldest one if there is no next
     */
    public SpatialUnitDTO findNextByInstitution(InstitutionDTO institution, SpatialUnitDTO current) {
        return spatialUnitRepository
                .findFirstByCreatedByInstitutionIdAndCreationTimeAfterOrderByCreationTimeAsc(
                        institution.getId(), current.getCreationTime())
                .map(spatialUnitMapper::convert)
                .orElseGet(() -> spatialUnitRepository
                        .findFirstByCreatedByInstitutionIdOrderByCreationTimeAsc(institution.getId())
                        .map(spatialUnitMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + institution.getId()))
                );
    }

    /**
     * Find the previous spatial unit created by a specific institution before the given one.
     * If there is no previous, returns the most recent one (wraps around).
     *
     * @param institution The institution to find spatial unit for
     * @param current The current spatial unit to find the previous one from
     * @return The previous SpatialUnitDTO, or the most recent one if there is no previous
     */
    public SpatialUnitDTO findPreviousByInstitution(InstitutionDTO institution, SpatialUnitDTO current) {
        return spatialUnitRepository
                .findFirstByCreatedByInstitutionIdAndCreationTimeBeforeOrderByCreationTimeDesc(
                        institution.getId(), current.getCreationTime())
                .map(spatialUnitMapper::convert)
                .orElseGet(() -> spatialUnitRepository
                        .findFirstByCreatedByInstitutionIdOrderByCreationTimeDesc(institution.getId())
                        .map(spatialUnitMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + institution.getId()))
                );
    }

    /**
     * Cycle the status of a spatial unit: INCOMPLETE -> COMPLETE -> VALIDATED -> INCOMPLETE.
     *
     * @param id The id of the SpatialUnit to update
     * @return The updated SpatialUnitDTO
     */
    public SpatialUnitDTO toggleValidated(Long id) {
        SpatialUnit unit = spatialUnitRepository.findById(id)
                .orElseThrow(() -> new ActionUnitNotFoundException("SpatialUnit not found with id: " + id));

        // Cycle through the enum values
        switch (unit.getValidated()) {
            case INCOMPLETE:
                unit.setValidated(ValidationStatus.COMPLETE);
                break;
            case COMPLETE:
                unit.setValidated(ValidationStatus.VALIDATED);
                break;
            case VALIDATED:
                unit.setValidated(ValidationStatus.INCOMPLETE);
                break;
            default:
                throw new IllegalStateException("Unknown status: " + unit.getValidated());
        }

        return spatialUnitMapper.convert(spatialUnitRepository.save(unit));
    }

}
