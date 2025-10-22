package fr.siamois.domain.services.spatialunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.authorization.PermissionServiceImpl;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service to manage SpatialUnit
 *
 * @author Grégory Bliault
 */
@Slf4j
@Service
public class SpatialUnitService implements ArkEntityService {

    private final SpatialUnitRepository spatialUnitRepository;
    private final ConceptService conceptService;
    private final ArkService arkService;
    private final InstitutionService institutionService;
    private final PersonService personService;
    private final PermissionServiceImpl permissionService;

    public SpatialUnitService(SpatialUnitRepository spatialUnitRepository, ConceptService conceptService, ArkService arkService, InstitutionService institutionService, PersonService personService, PermissionServiceImpl permissionService) {
        this.spatialUnitRepository = spatialUnitRepository;
        this.conceptService = conceptService;
        this.arkService = arkService;
        this.institutionService = institutionService;
        this.personService = personService;
        this.permissionService = permissionService;
    }


    /**
     * Find a spatial unit by its ID
     *
     * @param id The ID of the spatial unit
     * @return The SpatialUnit having the given ID
     * @throws SpatialUnitNotFoundException If no spatial unit are found for the given id
     * @throws RuntimeException             If the repository method returns a RuntimeException
     */
    @Transactional(readOnly = true)
    public SpatialUnit findById(long id) {
        try {
            SpatialUnit spatialUnit = spatialUnitRepository.findById(id).orElseThrow(() -> new SpatialUnitNotFoundException("SpatialUnit not found with ID: " + id));
            Hibernate.initialize(spatialUnit);
            return spatialUnit;
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

    private Page<SpatialUnit> initializeSpatialUnitLazyAttributes(Page<SpatialUnit> list) {
        list.forEach(spatialUnit -> {
            Hibernate.initialize(spatialUnit.getRelatedActionUnitList());
            Hibernate.initialize(spatialUnit.getRecordingUnitList());
            Hibernate.initialize(spatialUnit.getChildren());
            Hibernate.initialize(spatialUnit.getParents());
        });

        return list;
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
    public Page<SpatialUnit> findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {

        Page<SpatialUnit> res = spatialUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId, name, categoryIds, personIds, global, langCode, pageable);

        return initializeSpatialUnitLazyAttributes(res);
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
    public Page<SpatialUnit> findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
            SpatialUnit parent,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {
        Page<SpatialUnit> res = spatialUnitRepository.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                parent.getId(), name, categoryIds, personIds, global, langCode, pageable);

        return initializeSpatialUnitLazyAttributes(res);
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
    public Page<SpatialUnit> findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
            SpatialUnit child,
            String name, Long[] categoryIds, Long[] personIds, String global, String langCode, Pageable pageable) {
        Page<SpatialUnit> res = spatialUnitRepository.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                child.getId(), name, categoryIds, personIds, global, langCode, pageable);

        return initializeSpatialUnitLazyAttributes(res);
    }

    /**
     * Find all spatial units of a given institution
     *
     * @param institution The institution to filter by
     * @return A list of SpatialUnit belonging to the given institution
     */
    public List<SpatialUnit> findAllOfInstitution(Institution institution) {
        return spatialUnitRepository.findAllOfInstitution(institution.getId());
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
    public SpatialUnit save(UserInfo info, SpatialUnit su) throws SpatialUnitAlreadyExistsException {
        String name = su.getName();
        Concept type = su.getCategory();
        Set<SpatialUnit> parents = su.getParents();
        Set<SpatialUnit> children = su.getChildren();

        Optional<SpatialUnit> optSpatialUnit = spatialUnitRepository.findByNameAndInstitution(name, info.getInstitution().getId());
        if (optSpatialUnit.isPresent())
            throw new SpatialUnitAlreadyExistsException(
                    "identifier",
                    String.format("Spatial Unit with name %s already exist in institution %s", name, info.getInstitution().getName()));


        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setName(name);
        spatialUnit.setCreatedByInstitution(institutionService.findById(info.getInstitution().getId()));
        spatialUnit.setAuthor(personService.findById(info.getUser().getId()));
        spatialUnit.setCategory(conceptService.saveOrGetConcept(type));
        spatialUnit.setCreationTime(OffsetDateTime.now(ZoneId.systemDefault()));

        InstitutionSettings settings = institutionService.createOrGetSettingsOf(info.getInstitution());
        if (settings.hasEnabledArkConfig()) {
            Ark ark = arkService.generateAndSave(settings);
            spatialUnit.setArk(ark);
        }

        spatialUnit = spatialUnitRepository.save(spatialUnit);

        for (SpatialUnit parent : parents) {
            spatialUnitRepository.addParentToSpatialUnit(spatialUnit.getId(), parent.getId());
        }

        for (SpatialUnit child : children) {
            spatialUnitRepository.addParentToSpatialUnit(child.getId(), spatialUnit.getId());
        }

        return spatialUnit;
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
    public ArkEntity save(ArkEntity toSave) {
        try {
            SpatialUnit managedSpatialUnit;
            SpatialUnit spatialUnit = (SpatialUnit) toSave;

            if (spatialUnit.getId() != null) {
                Optional<SpatialUnit> optUnit = spatialUnitRepository.findById(spatialUnit.getId());
                managedSpatialUnit = optUnit.orElseGet(SpatialUnit::new);
            } else {
                managedSpatialUnit = new SpatialUnit();
            }

            managedSpatialUnit.setName(spatialUnit.getName());
            managedSpatialUnit.setValidated(spatialUnit.getValidated());
            managedSpatialUnit.setArk(spatialUnit.getArk());
            managedSpatialUnit.setAuthor(spatialUnit.getAuthor());
            managedSpatialUnit.setGeom(spatialUnit.getGeom());
            managedSpatialUnit.setCreatedByInstitution(spatialUnit.getCreatedByInstitution());
            // Add concept
            Concept type = conceptService.saveOrGetConcept(spatialUnit.getCategory());
            managedSpatialUnit.setCategory(type);

            return spatialUnitRepository.save(managedSpatialUnit);

        } catch (RuntimeException e) {
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
    }

    /**
     * Count the number of SpatialUnits created by a specific institution
     *
     * @param institution The institution to filter by
     * @return The count of SpatialUnits created in the institution
     */
    public long countByInstitution(Institution institution) {
        return spatialUnitRepository.countByCreatedByInstitution(institution);
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
    public long countParentsByChild(SpatialUnit spatialUnit) {
        return spatialUnitRepository.countParentsByChildId(spatialUnit.getId());
    }

    /**
     * Find all root SpatialUnits of a given institution
     *
     * @param institution The institution to filter by
     * @return A list of root SpatialUnit that have no parents
     */
    public List<SpatialUnit> findRootsOf(Institution institution) {
        List<SpatialUnit> result = new ArrayList<>();
        for (SpatialUnit spatialUnit : findAllOfInstitution(institution)) {
            if (countParentsByChild(spatialUnit) == 0) {
                result.add(spatialUnit);
            }
        }
        return result;
    }

    /**
     * Find all direct children of a given SpatialUnit
     *
     * @param spatialUnit The SpatialUnit to find children for
     * @return A list of direct children SpatialUnit of the given SpatialUnit
     */
    public List<SpatialUnit> findDirectChildrensOf(SpatialUnit spatialUnit) {
        return spatialUnitRepository.findChildrensOf(spatialUnit.getId()).stream().toList();
    }

    /**
     * Find all direct parents of a given SpatialUnit
     *
     * @param id The ID of the SpatialUnit to find parents for
     * @return A list of direct parents SpatialUnit of the given SpatialUnit
     */
    public List<SpatialUnit> findDirectParentsOf(Long id) {
        return spatialUnitRepository.findParentsOf(id).stream().toList();
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
}
