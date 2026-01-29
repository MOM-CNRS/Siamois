package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.actionunit.ActionUnitResolveConfig;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.CustomFormResponseService;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service to manage RecordingUnit
 *
 * @author Grégory Bliault
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingUnitService implements ArkEntityService {

    private final RecordingUnitRepository recordingUnitRepository;
    private final ConceptService conceptService;
    private final CustomFormResponseService customFormResponseService;
    private final PersonRepository personRepository;
    private final InstitutionService institutionService;
    private final ActionUnitService actionUnitService;
    private final TeamMemberRepository teamMemberRepository;
    private final RecordingUnitIdCounterRepository recordingUnitIdCounterRepository;
    private final RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;
    private final ApplicationContext applicationContext;

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

    public boolean fullIdentifierAlreadyExistInAction(RecordingUnit unit) {
        List<RecordingUnit> existing = findByActionAndFullId(unit.getActionUnit(), unit.getFullIdentifier());
        return existing.stream()
                .anyMatch(r -> Objects.equals(r.getFullIdentifier(), unit.getFullIdentifier()) && !Objects.equals(r.getId(), unit.getId()));
    }

    /**
     * Save a recording unit with its associated concept and related units.
     *
     * @param recordingUnit    The recording unit to save.
     * @param concept          The concept associated with the recording unit.
     * @return The saved RecordingUnit instance.
     */
    @Transactional
    public RecordingUnit save(RecordingUnit recordingUnit, Concept concept) {

        try {

            RecordingUnit managedRecordingUnit;

            managedRecordingUnit = newOrGetRecordingUnit(recordingUnit);

            setupActionUnitInfo(recordingUnit, managedRecordingUnit);
            setupStratigraphicRelationships(recordingUnit, managedRecordingUnit);
            setupConcept(concept, managedRecordingUnit);
            setupSpatialUnit(recordingUnit, managedRecordingUnit);
            setupOtherFields(recordingUnit, managedRecordingUnit);
            managedRecordingUnit = setupAdditionalAnswers(recordingUnit, managedRecordingUnit);
            setupParents(recordingUnit, managedRecordingUnit);
            setupChilds(recordingUnit, managedRecordingUnit);

            return recordingUnitRepository.save(managedRecordingUnit);

        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
    }

    private RecordingUnit newOrGetRecordingUnit(RecordingUnit recordingUnit) {
        RecordingUnit managedRecordingUnit;
        if (recordingUnit.getId() != null) {
            Optional<RecordingUnit> optRecordingUnit = recordingUnitRepository.findById(recordingUnit.getId());
            managedRecordingUnit = optRecordingUnit.orElseGet(RecordingUnit::new);
        } else {
            managedRecordingUnit = new RecordingUnit();
        }
        return managedRecordingUnit;
    }

    private static void setupChilds(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        for (RecordingUnit child : recordingUnit.getChildren()) {
            managedRecordingUnit.getChildren().add(child);
        }
    }

    private void setupParents(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        // Gestion des parents
        for (RecordingUnit parentRef : recordingUnit.getParents()) {
            RecordingUnit parent = recordingUnitRepository.findById(parentRef.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent not found: " + parentRef.getId()));

            // Ajout bidirectionnel
            parent.getChildren().add(managedRecordingUnit);
            managedRecordingUnit.getParents().add(parent);

            // Sauvegarde du parent
            recordingUnitRepository.save(parent);
        }
    }

    private RecordingUnit setupAdditionalAnswers(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {

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


        managedRecordingUnit = recordingUnitRepository.save(managedRecordingUnit);
        return managedRecordingUnit;
    }

    private static void setupOtherFields(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        managedRecordingUnit.setAltitude(recordingUnit.getAltitude());
        managedRecordingUnit.setArk(recordingUnit.getArk());
        managedRecordingUnit.setDescription(recordingUnit.getDescription());
        managedRecordingUnit.setFullIdentifier(recordingUnit.getFullIdentifier());
        managedRecordingUnit.setAuthor(recordingUnit.getAuthor());
        managedRecordingUnit.setClosingDate(recordingUnit.getClosingDate());
        managedRecordingUnit.setOpeningDate(recordingUnit.getOpeningDate());
        managedRecordingUnit.setSize(recordingUnit.getSize());
        managedRecordingUnit.setGeomorphologicalCycle(recordingUnit.getGeomorphologicalCycle());
        managedRecordingUnit.setNormalizedInterpretation(recordingUnit.getNormalizedInterpretation());
        managedRecordingUnit.setValidated(recordingUnit.getValidated());
        managedRecordingUnit.setValidatedAt(recordingUnit.getValidatedAt());
        managedRecordingUnit.setValidatedBy(recordingUnit.getValidatedBy());
        managedRecordingUnit.setTaq(recordingUnit.getTaq());
        managedRecordingUnit.setTpq(recordingUnit.getTpq());
        managedRecordingUnit.setMatrixColor(recordingUnit.getMatrixColor());
        managedRecordingUnit.setMatrixComposition(recordingUnit.getMatrixComposition());
        managedRecordingUnit.setMatrixTexture(recordingUnit.getMatrixTexture());
        managedRecordingUnit.setErosionOrientation(recordingUnit.getErosionOrientation());
        managedRecordingUnit.setErosionProfile(recordingUnit.getErosionProfile());
        managedRecordingUnit.setErosionShape(recordingUnit.getErosionShape());

        if (managedRecordingUnit.getCreatedBy() == null) {
            managedRecordingUnit.setCreatedBy(recordingUnit.getCreatedBy());
        }

        managedRecordingUnit.setChronologicalPhase(recordingUnit.getChronologicalPhase());
        managedRecordingUnit.setGeomorphologicalAgent(recordingUnit.getGeomorphologicalAgent());
    }

    private void setupStratigraphicRelationships(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {

        syncAsUnit1(managedRecordingUnit, recordingUnit.getRelationshipsAsUnit1());
        syncAsUnit2(managedRecordingUnit, recordingUnit.getRelationshipsAsUnit2());

    }

    private void syncAsUnit1(RecordingUnit managed, Set<StratigraphicRelationship> incoming) {
        // Index des relations existantes côté unit1
        Map<StratigraphicRelationship.StratRelKey, StratigraphicRelationship> existing =
                managed.getRelationshipsAsUnit1().stream()
                        .collect(Collectors.toMap(StratigraphicRelationship::keyOf, Function.identity()));

        // Parcours des relations entrantes
        for (StratigraphicRelationship incomingRel : incoming) {
            // ✅ Setter le côté propriétaire
            incomingRel.setUnit1(managed);

            RecordingUnit managedUnit2 = recordingUnitRepository.findById(incomingRel.getUnit2().getId()).orElse(null);
            incomingRel.setUnit2(managedUnit2);

            StratigraphicRelationship.StratRelKey key = StratigraphicRelationship.keyOf(incomingRel);
            StratigraphicRelationship managedRel = existing.remove(key);

            if (managedRel != null) {
                // 🔄 UPDATE de la relation existante
                managedRel.setConcept(incomingRel.getConcept());
                managedRel.setUncertain(incomingRel.getUncertain());
                managedRel.setIsAsynchronous(incomingRel.getIsAsynchronous());
                managedRel.setConceptDirection(incomingRel.getConceptDirection());
            } else {
                // ➕ INSERT nouvelle relation
                managed.addRelationshipAsUnit1(incomingRel);
            }
        }

        // 🗑 DELETE des relations supprimées
        existing.values().forEach(managed::removeRelationshipAsUnit1);
    }


    private void syncAsUnit2(RecordingUnit managed, Set<StratigraphicRelationship> incoming) {
        Map<StratigraphicRelationship.StratRelKey, StratigraphicRelationship> existing =
                managed.getRelationshipsAsUnit2().stream()
                        .collect(Collectors.toMap(StratigraphicRelationship::keyOf, Function.identity()));

        for (StratigraphicRelationship incomingRel : incoming) {
            // ✅ Owner side
            incomingRel.setUnit2(managed);

            RecordingUnit managedUnit1 = recordingUnitRepository.findById(incomingRel.getUnit1().getId()).orElse(null);
            incomingRel.setUnit1(managedUnit1);

            StratigraphicRelationship.StratRelKey key = StratigraphicRelationship.keyOf(incomingRel);
            StratigraphicRelationship managedRel = existing.remove(key);

            if (managedRel != null) {
                // UPDATE existant
                managedRel.setConcept(incomingRel.getConcept());
                managedRel.setUncertain(incomingRel.getUncertain());
                managedRel.setIsAsynchronous(incomingRel.getIsAsynchronous());
                managedRel.setConceptDirection(incomingRel.getConceptDirection());
            } else {
                // INSERT
                managed.addRelationshipAsUnit2(incomingRel);
            }
        }

        // DELETE les supprimés
        existing.values().forEach(managed::removeRelationshipAsUnit2);
    }





    private void setupSpatialUnit(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        managedRecordingUnit.setSpatialUnit(recordingUnit.getSpatialUnit());

        managedRecordingUnit.setContributors(personRepository.findAllById(
                        recordingUnit.getContributors().stream()
                                .map(Person::getId)
                                .toList()
                )
        );
    }

    private void setupConcept(Concept concept, RecordingUnit managedRecordingUnit) {
        Concept type = conceptService.saveOrGetConcept(concept);
        managedRecordingUnit.setType(type);
    }

    private void setupActionUnitInfo(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        managedRecordingUnit.setActionUnit(recordingUnit.getActionUnit());
        managedRecordingUnit.setCreatedByInstitution(recordingUnit.getCreatedByInstitution());
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
    public RecordingUnit save(ArkEntity toSave) {
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
        return institutionService.isManagerOf(action.getCreatedByInstitution(), user.getUser()) ||
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

    /**
     * Get all the roots recording unit in the institution
     *
     * @param institutionId the institution id
     * @return The list of RecordingUnit associated with the institution
     */
    public List<RecordingUnit> findAllWithoutParentsByInstitution(Long institutionId) {
        List<RecordingUnit> res = recordingUnitRepository.findRootsByInstitution(institutionId);
        initializeRecordingUnitCollections(res);
        return res;
    }

    /**
     * Get all recording unit in the institution that are the children of a given parent
     *
     * @param parentId      the parent id
     * @param institutionId the institution id
     * @return The list of RecordingUnit associated with the institution and that are the children of a given parent
     */
    public List<RecordingUnit> findChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        List<RecordingUnit> res = recordingUnitRepository.findChildrenByParentAndInstitution(parentId, institutionId);
        initializeRecordingUnitCollections(res);
        return res;
    }

    /**
     * Get all recording unit that are the roots for a given action
     *
     * @param actionId the action id
     * @return The list of RecordingUnit that are the roots for a given action
     */
    public List<RecordingUnit> findAllWithoutParentsByAction(Long actionId) {
        List<RecordingUnit> res = recordingUnitRepository.findRootsByAction(actionId);
        initializeRecordingUnitCollections(res);
        return res;
    }

    // Reusable method to initialize collections
    private void initializeRecordingUnitCollections(List<RecordingUnit> recordingUnits) {
        recordingUnits.forEach(ru -> {
            Hibernate.initialize(ru.getParents());
            Hibernate.initialize(ru.getChildren());
        });
    }

    public int generatedNextIdentifier(@NonNull ActionUnit actionUnit, @Nullable Concept unitType, @Nullable RecordingUnit parentRu) {
        ActionUnitResolveConfig config = actionUnit.resolveConfig();

        switch (config) {
            case UNIQUE -> {
                return recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId());
            }
            case PARENT -> {
                if (parentRu == null) {
                    return recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId());
                }
                return recordingUnitIdCounterRepository.ruNextValParent(parentRu.getId());
            }
            case TYPE_UNIQUE -> {
                Long unitTypeId = unitType == null ? null : unitType.getId();
                return recordingUnitIdCounterRepository.ruNextValTypeUnique(actionUnit.getId(), unitTypeId);
            }
            case PARENT_TYPE -> {
                if (parentRu == null) {
                    return recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId());
                }
                Long unitTypeId = unitType == null ? null : unitType.getId();
                return recordingUnitIdCounterRepository.ruNextValTypeParent(parentRu.getId(), unitTypeId);
            }
            default -> {
                return 0;
            }
        }
    }
    public RecordingUnit findByFullIdentifierAndInstitutionIdentifier(String identifier, String institutionIdentifier) {
        return recordingUnitRepository.findByFullIdentifierAndInstitutionIdentifier(identifier, institutionIdentifier).orElse(null);
    }

    public RecordingUnitIdInfo createOrGetInfoOf(@NonNull RecordingUnit recordingUnit, @Nullable RecordingUnit parentRecordingUnit) {
        Optional<RecordingUnitIdInfo> opt = recordingUnitIdInfoRepository.findById(recordingUnit.getId());
        if (opt.isPresent()) return opt.get();
        RecordingUnitIdInfo info = new RecordingUnitIdInfo();
        info.setRecordingUnitId(recordingUnit.getId());
        info.setRecordingUnit(recordingUnit);
        if (recordingUnit.getSpatialUnit() != null) {
            info.setSpatialUnitNumber(Math.toIntExact(recordingUnit.getSpatialUnit().getId()));
        }
        info.setActionUnit(recordingUnit.getActionUnit());
        if (parentRecordingUnit != null) {
            info.setParent(parentRecordingUnit);
            info.setRuParentType(parentRecordingUnit.getType());
        }
        return recordingUnitIdInfoRepository.save(info);
    }

    /**
     * Generates the identifier for a recording unit that has no parent.
     * Its creation parameters therefore refer directly to the action unit.
     *
     * @param actionUnit The action unit containing the identifier configuration.
     * @param recordingUnit The recording unit to generate the identifier for.
     * @return The generated identifier.
     */
    public String generateFullIdentifier(@NonNull ActionUnit actionUnit, @NonNull RecordingUnit recordingUnit) {
        log.trace("Generating full identifier for recording unit");
        String format = actionUnit.getRecordingUnitIdentifierFormat();
        RecordingUnit parent = recordingUnit
                .getParents()
                .stream()
                .findFirst()
                .orElse(null);

        int numericalId = generatedNextIdentifier(actionUnit, recordingUnit.getType(), parent);
        RecordingUnitIdInfo info = createOrGetInfoOf(recordingUnit, parent);

        info.setRuNumber(numericalId);

        if (format == null) {
            recordingUnitIdInfoRepository.save(info);
            return String.valueOf(numericalId);
        }

        info.setRuType(recordingUnit.getType());

        for (RuIdentifierResolver resolver : findAllIdentifierResolver().values()) {
            if (resolver.formatUsesThisResolver(format)) {
                format = resolver.resolve(format, info);
            }
        }

        return format;
    }

    private static final Map<String, RuIdentifierResolver> IDENTIFIER_RESOLVERS = new HashMap<>();

    public Map<String, RuIdentifierResolver> findAllIdentifierResolver() {
        if (!IDENTIFIER_RESOLVERS.isEmpty())
            return IDENTIFIER_RESOLVERS;

        Reflections reflections = new Reflections("fr.siamois.domain.services.recordingunit.identifier");
        Set<Class<? extends RuIdentifierResolver>> classes = reflections.getSubTypesOf(RuIdentifierResolver.class);

        for (Class<? extends RuIdentifierResolver> clazz : classes) {
            if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                try {
                    RuIdentifierResolver resolver = applicationContext.getBean(clazz);
                    IDENTIFIER_RESOLVERS.put(resolver.getCode(), resolver);
                } catch (BeansException e) {
                    log.error("Error while scanning identifiers resolver of RecordingUnits");
                    log.error(e.getMessage(), e);
                }
            }
        }

        return IDENTIFIER_RESOLVERS;
    }

    /**
     * Returns all available codes for recording units identifiers
     *
     * @return The list of available codes
     */
    public List<String> findAllIdentifiersCode() {
        return findAllIdentifierResolver()
                .keySet()
                .stream()
                .toList();
    }

    public List<String> findAllNumericalIdentifiersCode() {
        return findAllIdentifierResolver()
                .values()
                .stream()
                .filter(r -> RuNumericalIdentifierResolver.class.isAssignableFrom(r.getClass()))
                .map(RuIdentifierResolver::getCode)
                .toList();
    }

    @NonNull
    public List<RecordingUnit> findByActionAndFullId(@NotNull ActionUnit actionUnit, @NotNull String fullIdentifier) {
        return recordingUnitRepository.findByFullIdentifierAndActionUnit(fullIdentifier, actionUnit);
    }

    public List<RecordingUnit> findAllByActionUnit(@NotNull ActionUnit actionUnit) {
        return recordingUnitRepository.findAllByActionUnit(actionUnit);
    }
}
