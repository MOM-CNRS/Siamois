package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.MaxRecordingUnitIdentifierReached;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.CustomFormResponseService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service to manage RecordingUnit
 *
 * @author Grégory Bliault
 */
@Slf4j
@Service
public class RecordingUnitService implements ArkEntityService {

    private final RecordingUnitRepository recordingUnitRepository;
    private final ConceptService conceptService;
    private final CustomFormResponseService customFormResponseService;
    private final PersonRepository personRepository;
    private final InstitutionService institutionService;
    private final ActionUnitService actionUnitService;
    private final TeamMemberRepository teamMemberRepository;


    public RecordingUnitService(RecordingUnitRepository recordingUnitRepository,
                                ConceptService conceptService,
                                CustomFormResponseService customFormResponseService,
                                PersonRepository personRepository, InstitutionService institutionService, ActionUnitService actionUnitService, TeamMemberRepository teamMemberRepository) {
        this.recordingUnitRepository = recordingUnitRepository;
        this.conceptService = conceptService;
        this.customFormResponseService = customFormResponseService;
        this.personRepository = personRepository;
        this.institutionService = institutionService;
        this.actionUnitService = actionUnitService;
        this.teamMemberRepository = teamMemberRepository;
    }

    /**
     * Generate the next identifier for a recording unit.
     *
     * @param recordingUnit The recording unit for which to generate the next identifier.
     * @return The next identifier for the recording unit.
     */
    public int generateNextIdentifier(RecordingUnit recordingUnit) {
        // Generate next identifier
        Integer currentMaxIdentifier = recordingUnitRepository.findMaxUsedIdentifierByAction(recordingUnit.getActionUnit().getId());
        int nextIdentifier = (currentMaxIdentifier == null) ? recordingUnit.getActionUnit().getMinRecordingUnitCode() : currentMaxIdentifier + 1;
        if (nextIdentifier > recordingUnit.getActionUnit().getMaxRecordingUnitCode() || nextIdentifier < 0) {
            throw new MaxRecordingUnitIdentifierReached("Max recording unit code reached; Please ask administrator to increase the range");
        }
        return (nextIdentifier);
    }

    /**
     * Bulk update the type of multiple recording units.
     *
     * @param ids  The list of IDs of the recording units to update.
     * @param type The new type to set for the recording units.
     * @return The number of recording units updated.
     */
    @Transactional
    public int bulkUpdateType(List<Long> ids, Concept type) {
        return recordingUnitRepository.updateTypeByIds(type.getId(), ids);
    }

    /**
     * Save a recording unit with its associated concept and related units.
     *
     * @param recordingUnit    The recording unit to save.
     * @param concept          The concept associated with the recording unit.
     * @param anteriorUnits    List of recording units that are considered as "anterior" to the current one.
     * @param synchronousUnits List of recording units that are considered as "synchronous" to the current one.
     * @param posteriorUnits   List of recording units that are considered as "posterior" to the current one.
     * @return The saved RecordingUnit instance.
     */
    @Transactional
    public RecordingUnit save(RecordingUnit recordingUnit, Concept concept,
                              List<RecordingUnit> anteriorUnits,
                              List<RecordingUnit> synchronousUnits,
                              List<RecordingUnit> posteriorUnits) {

        try {

            RecordingUnit managedRecordingUnit;

            if (recordingUnit.getId() != null) {
                Optional<RecordingUnit> optRecordingUnit = recordingUnitRepository.findById(recordingUnit.getId());
                managedRecordingUnit = optRecordingUnit.orElseGet(RecordingUnit::new);
            } else {
                managedRecordingUnit = new RecordingUnit();
            }

            // Generate unique identifier
            managedRecordingUnit.setIdentifier(recordingUnit.getIdentifier());
            managedRecordingUnit.setActionUnit(recordingUnit.getActionUnit());
            managedRecordingUnit.setCreatedByInstitution(recordingUnit.getCreatedByInstitution());
            if (managedRecordingUnit.getIdentifier() == null) {
                managedRecordingUnit.setIdentifier(generateNextIdentifier(managedRecordingUnit));
            }
            // Set full identifier
            managedRecordingUnit.setFullIdentifier(null); // reseting so that displayFullIdentifier generated a new one
            managedRecordingUnit.setFullIdentifier(managedRecordingUnit.displayFullIdentifier());


            // Add concept
            Concept type = conceptService.saveOrGetConcept(concept);
            managedRecordingUnit.setType(type);

            // Spatial Unit
            managedRecordingUnit.setSpatialUnit(recordingUnit.getSpatialUnit());

            // many to many (need managed instances)
            managedRecordingUnit.setAuthors(personRepository.findAllById(
                            recordingUnit.getAuthors().stream()
                                    .map(Person::getId)
                                    .toList()
                    )
            );
            managedRecordingUnit.setExcavators(personRepository.findAllById(
                            recordingUnit.getExcavators().stream()
                                    .map(Person::getId)
                                    .toList()
                    )
            );

            // Add other fields
            managedRecordingUnit.setAltitude(recordingUnit.getAltitude());
            managedRecordingUnit.setArk(recordingUnit.getArk());
            managedRecordingUnit.setDescription(recordingUnit.getDescription());
            managedRecordingUnit.setAuthor(recordingUnit.getAuthor());
            managedRecordingUnit.setEndDate(recordingUnit.getEndDate());
            managedRecordingUnit.setStartDate(recordingUnit.getStartDate());
            managedRecordingUnit.setSize(recordingUnit.getSize());
            managedRecordingUnit.setGeomorphologicalCycle(recordingUnit.getGeomorphologicalCycle());
            managedRecordingUnit.setNormalizedInterpretation(recordingUnit.getNormalizedInterpretation());
            managedRecordingUnit.setValidated(recordingUnit.getValidated());
            managedRecordingUnit.setValidatedAt(recordingUnit.getValidatedAt());
            managedRecordingUnit.setValidatedBy(recordingUnit.getValidatedBy());

            CustomFormResponse managedFormResponse;


            if (recordingUnit.getFormResponse() != null && recordingUnit.getFormResponse().getForm() != null) {
                // Save the form response if there is one

                // Get the existing response or create a new one
                if (managedRecordingUnit.getFormResponse() == null) {
                    // Initialize the managed form response
                    managedFormResponse = new CustomFormResponse();
                    managedRecordingUnit.setFormResponse(managedFormResponse);
                } else {
                    managedFormResponse = managedRecordingUnit.getFormResponse();
                }
                // Process form response
                customFormResponseService
                        .saveFormResponse(managedFormResponse, recordingUnit.getFormResponse());
            } else {
                managedRecordingUnit.setFormResponse(null);
            }


            return recordingUnitRepository.save(managedRecordingUnit);

        } catch (RuntimeException e) {
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
    }

    /**
     * Find a recording unit by its ID
     *
     * @param id The ID of the recording unit
     * @return The RecordingUnit having the given ID
     * @throws RecordingUnitNotFoundException If no recording unit are found for the given id
     * @throws RuntimeException               If the repository method returns a RuntimeException
     */
    public RecordingUnit findById(long id) {
        try {
            return recordingUnitRepository.findById(id).orElseThrow(() -> new RecordingUnitNotFoundException("RecordingUnit not found with ID: " + id));
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<? extends ArkEntity> findWithoutArk(Institution institution) {
        return recordingUnitRepository.findAllWithoutArkOfInstitution(institution.getId());
    }

    @Override
    public ArkEntity save(ArkEntity toSave) {
        return recordingUnitRepository.save((RecordingUnit) toSave);
    }

    /**
     * Count the number of recording units created by a specific institution.
     *
     * @param institution The institution for which to count the recording units.
     * @return The count of recording units created by the specified institution.
     */
    public long countByInstitution(Institution institution) {
        return recordingUnitRepository.countByCreatedByInstitution(institution);
    }

    /**
     * Find all recording units by institution and filter by full identifier, categories, and global search.
     *
     * @param institutionId  The ID of the institution to filter by.
     * @param fullIdentifier The full identifier to search for (can be partial).
     * @param categoryIds    The IDs of categories to filter by (can be null).
     * @param global         The global search term to filter by (can be null).
     * @param langCode       The language code for localization (can be null).
     * @param pageable       The pagination information.
     * @return A page of RecordingUnit matching the criteria.
     */
    @Transactional
    public Page<RecordingUnit> findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        Page<RecordingUnit> res = recordingUnitRepository.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, fullIdentifier, categoryIds, global, langCode, pageable
        );


        // load related entities
        res.forEach(actionUnit -> {
            Hibernate.initialize(actionUnit.getParents());
            Hibernate.initialize(actionUnit.getChildren());

        });

        return res;
    }

    /**
     * Find all recording units by institution, action unit, full identifier, categories, and global search.
     *
     * @param institutionId  The ID of the institution to filter by.
     * @param actionId       The ID of the action unit to filter by.
     * @param fullIdentifier The full identifier to search for (can be partial).
     * @param categoryIds    The IDs of categories to filter by (can be null).
     * @param global         The global search term to filter by (can be null).
     * @param langCode       The language code for localization (can be null).
     * @param pageable       The pagination information.
     * @return A page of RecordingUnit matching the criteria.
     */
    @Transactional
    public Page<RecordingUnit> findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            Long actionId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            Pageable pageable
    ) {
        Page<RecordingUnit> res = recordingUnitRepository.findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, actionId, fullIdentifier, categoryIds, global, langCode, pageable
        );


        // load related entities
        res.forEach(actionUnit -> {
            Hibernate.initialize(actionUnit.getParents());
            Hibernate.initialize(actionUnit.getChildren());
        });

        return res;
    }

    /**
     * Verify if the user has the permission to create specimen in the context of a recording unit
     *
     * @param user The user to check the permission on
     * @param ru   The context
     * @return True if the user has sufficient permissions
     */
    public boolean canCreateSpecimen(UserInfo user, RecordingUnit ru) {
        ActionUnit action = ru.getActionUnit();
        return institutionService.isManagerOf(action.getCreatedByInstitution(),user.getUser()) ||
                actionUnitService.isManagerOf(action, user.getUser()) ||
                (teamMemberRepository.existsByActionUnitAndPerson(action, user.getUser()) && actionUnitService.isActionUnitStillOngoing(action));
    }

    public Page<RecordingUnit> findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long recordingUnitId,
            String fullIdentifierFilter,
            Long[] categoryIds,
            String globalFilter,
            String languageCode,
            Pageable pageable) {

        Page<RecordingUnit> res = recordingUnitRepository.findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                recordingUnitId, fullIdentifierFilter, categoryIds, globalFilter, languageCode, pageable
        );


        // load related entities
        res.forEach(actionUnit -> {
            Hibernate.initialize(actionUnit.getParents());
            Hibernate.initialize(actionUnit.getChildren());
        });

        return res;
    }

    public Page<RecordingUnit> findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(Long childId,
                                                                                                               String fullIdentifierFilter,
                                                                                                               Long[] categoryIds,
                                                                                                               String globalFilter, String languageCode, Pageable pageable) {
        Page<RecordingUnit> res = recordingUnitRepository.findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                childId, fullIdentifierFilter, categoryIds, globalFilter, languageCode, pageable
        );


        // load related entities
        res.forEach(actionUnit -> {
            Hibernate.initialize(actionUnit.getParents());
            Hibernate.initialize(actionUnit.getChildren());
        });

        return res;
    }

    public Page<RecordingUnit> findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(Long spatialUnitId,
                                                                                                                     String fullIdentifierFilter,
                                                                                                                     Long[] categoryIds,
                                                                                                                     String globalFilter, String languageCode, Pageable pageable

    ) {
        Page<RecordingUnit> res = recordingUnitRepository.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                spatialUnitId, fullIdentifierFilter, categoryIds, globalFilter, languageCode, pageable
        );


        // load related entities
        res.forEach(actionUnit -> {
            Hibernate.initialize(actionUnit.getParents());
            Hibernate.initialize(actionUnit.getChildren());
        });

        return res;
    }

    /**
     * Count the number of RecordingUnits associated with a specific SpatialUnit.
     *
     * @param spatialUnit The SpatialUnit to count RecordingUnits for
     * @return The count of RecordingUnit associated with the SpatialUnit
     */
    public Integer countBySpatialContext(SpatialUnit spatialUnit) {
        return recordingUnitRepository.countBySpatialContext(spatialUnit.getId());
    }

    /**
     * Count the number of RecordingUnits associated with a specific ActionUnit.
     *
     * @param actionUnit The ActionUnit to count RecordingUnits for
     * @return The count of RecordingUnit associated with the SpatialUnit
     */
    public Integer countByActionContext(ActionUnit actionUnit) {
        return recordingUnitRepository.countByActionContext(actionUnit.getId());
    }
}


