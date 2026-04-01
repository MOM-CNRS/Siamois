package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.actionunit.ActionUnitResolveConfig;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import fr.siamois.mapper.ActionUnitSummaryMapper;
import fr.siamois.mapper.RecordingUnitMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fr.siamois.domain.models.ValidationStatus.*;

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
    private final PersonRepository personRepository;
    private final InstitutionService institutionService;
    private final ActionUnitService actionUnitService;
    private final TeamMemberRepository teamMemberRepository;
    private final RecordingUnitIdCounterRepository recordingUnitIdCounterRepository;
    private final RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;
    private final RecordingUnitMapper recordingUnitMapper;
    private final ConversionService conversionService;
    private final ApplicationContext applicationContext;
    private final ActionUnitSummaryMapper actionUnitSummaryMapper;

    /**
     * Bulk update the type of multiple recording units.
     *
     * @param ids  The list of IDs of the recording units to update.
     * @param type The new type to set for the recording units.
     * @return The number of recording units updated.
     */
    @Transactional
    public int bulkUpdateType(List<Long> ids, ConceptDTO type) {
        return recordingUnitRepository.updateTypeByIds(type.getId(), ids);
    }

    public boolean fullIdentifierAlreadyExistInAction(RecordingUnitDTO unit) {
        List<RecordingUnit> existing = findByActionIdAndFullId(unit.getActionUnit().getId(), unit.getFullIdentifier());
        return existing.stream()
                .anyMatch(r -> Objects.equals(r.getFullIdentifier(), unit.getFullIdentifier()) && !Objects.equals(r.getId(),
                        unit.getId()));
    }

    /**
     * Save a recording unit with its associated concept and related units.
     *
     * @param recordingUnitDTO    The recording unit to save.
     * @return The saved RecordingUnit instance.
     */
    @Transactional
    @CacheEvict({
            "InstitutionHasRootChildrenRU",
            "ActionHasRootChildrenRU"
    })
    public RecordingUnitDTO save(RecordingUnitDTO recordingUnitDTO) {

        try {

            RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
            return recordingUnitMapper.convert(save(recordingUnit));

        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }

    }

    @Transactional
    public void updateStratigraphicRel(RecordingUnitDTO recordingUnitDTO) {

        RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
        assert recordingUnit != null;
        RecordingUnit managedRecordingUnit = newOrGetRecordingUnit(recordingUnit);
        setupStratigraphicRelationships(recordingUnit, managedRecordingUnit);

    }


    private RecordingUnit save(RecordingUnit recordingUnit) {

        try {

            RecordingUnit managedRecordingUnit;

            assert recordingUnit != null;
            managedRecordingUnit = newOrGetRecordingUnit(recordingUnit);

            setupStratigraphicRelationships(recordingUnit, managedRecordingUnit);
            setupSpatialUnit(recordingUnit, managedRecordingUnit);
            managedRecordingUnit.setActionUnit(recordingUnit.getActionUnit());
            managedRecordingUnit.setCreatedByInstitution(recordingUnit.getCreatedByInstitution());
            managedRecordingUnit.setFullIdentifier(recordingUnit.getFullIdentifier());
            setupOtherFields(recordingUnit, managedRecordingUnit);


            RecordingUnit toReturn = recordingUnitRepository.save(managedRecordingUnit);

            setupParents(recordingUnit, managedRecordingUnit);
            setupChilds(recordingUnit, managedRecordingUnit);

            return toReturn;

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

            // Sauvegarde du parent
            recordingUnitRepository.save(parent);
        }
    }



    private static void setupOtherFields(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {

        managedRecordingUnit.setAltitude(recordingUnit.getAltitude());
        managedRecordingUnit.setArk(recordingUnit.getArk());
        managedRecordingUnit.setDescription(recordingUnit.getDescription());
        managedRecordingUnit.setType(recordingUnit.getType());
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

    private void setupStratigraphicRelationships(RecordingUnit source, RecordingUnit target) {
        syncRelationships(target, source.getRelationshipsAsUnit1(), true);
        syncRelationships(target, source.getRelationshipsAsUnit2(), false);
    }

    private void syncRelationships(
            RecordingUnit managed,
            Set<StratigraphicRelationship> relationships,
            boolean isUnit1
    ) {
        Map<StratigraphicRelationship.StratRelKey, StratigraphicRelationship> existing =
                (isUnit1 ? managed.getRelationshipsAsUnit1() : managed.getRelationshipsAsUnit2()).stream()
                        .collect(Collectors.toMap(StratigraphicRelationship::keyOf, Function.identity()));

        for (StratigraphicRelationship rel : relationships) {
            if (isUnit1) {
                rel.setUnit1(managed);
                rel.setUnit2(recordingUnitRepository.findById(rel.getUnit2().getId()).orElse(null));
            } else {
                rel.setUnit2(managed);
                rel.setUnit1(recordingUnitRepository.findById(rel.getUnit1().getId()).orElse(null));
            }

            StratigraphicRelationship.StratRelKey key = StratigraphicRelationship.keyOf(rel);
            StratigraphicRelationship managedRel = existing.remove(key);

            if (managedRel != null) {
                // Update existing
                managedRel.setConcept(rel.getConcept());
                managedRel.setUncertain(rel.getUncertain());
                managedRel.setIsAsynchronous(rel.getIsAsynchronous());
                managedRel.setConceptDirection(rel.getConceptDirection());
            } else {
                // Add new
                if (isUnit1) {
                    managed.addRelationshipAsUnit1(rel);
                } else {
                    managed.addRelationshipAsUnit2(rel);
                }
            }
        }

        // Remove deleted
        existing.values().forEach(rel -> {
            if (isUnit1) {
                managed.removeRelationshipAsUnit1(rel);
            } else {
                managed.removeRelationshipAsUnit2(rel);
            }
        });
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


    /**
     * Find a recording unit by its ID
     *
     * @param id The ID of the recording unit
     * @return The RecordingUnit having the given ID
     * @throws RecordingUnitNotFoundException If no recording unit are found for the given id
     * @throws RuntimeException               If the repository method returns a RuntimeException
     */
    @Transactional(readOnly = true)
    public RecordingUnitDTO findById(long id) {
        try {
            RecordingUnit recordingUnit = recordingUnitRepository.findById(id)
                    .orElseThrow(() -> new RecordingUnitNotFoundException("RecordingUnit not found with ID: " + id));
            return recordingUnitMapper.convert(recordingUnit);
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
    public RecordingUnitDTO save(AbstractEntityDTO toSave) {
        RecordingUnit toReturn = recordingUnitRepository.save(Objects.requireNonNull(recordingUnitMapper.invertConvert((RecordingUnitDTO) toSave)));
        return recordingUnitMapper.convert(toReturn);
    }

    /**
     * Count the number of recording units created by a specific institution.
     *
     * @param institutionId The institution for which to count the recording units.
     * @return The count of recording units created by the specified institution.
     */
    @Transactional(readOnly = true)
    public long countByInstitutionId(Long institutionId) {
        return recordingUnitRepository.countByCreatedByInstitutionId(institutionId);
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
    public Page<RecordingUnitDTO> findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
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

        return res.map(recordingUnitMapper::convert);
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
    public Page<RecordingUnitDTO> findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
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

        return res.map(recordingUnitMapper::convert);
    }

    /**
     * Verify if the user has the permission to create specimen in the context of a recording unit
     *
     * @param user The user to check the permission on
     * @param ru   The context
     * @return True if the user has sufficient permissions
     */
    public boolean canCreateSpecimen(UserInfo user, RecordingUnitDTO ru) {
        ActionUnitSummaryDTO action = ru.getActionUnit();
        return institutionService.isManagerOf(action.getCreatedByInstitution(), user.getUser()) ||
                actionUnitService.isManagerOf(action, user.getUser()) ||
                (teamMemberRepository.existsByActionUnitIdAndPerson(action.getId(), user.getUser())
                        && actionUnitService.isActionUnitStillOngoing(action));
    }

    public Page<RecordingUnitDTO> findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long recordingUnitId,
            String fullIdentifierFilter,
            Long[] categoryIds,
            String globalFilter,
            String languageCode,
            Pageable pageable) {

        Page<RecordingUnit> res = recordingUnitRepository.findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                recordingUnitId, fullIdentifierFilter, categoryIds, globalFilter, languageCode, pageable
        );

        return res
                .map(recordingUnitMapper::convert);
    }

    public Page<RecordingUnitDTO> findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(Long childId,
                                                                                                               String fullIdentifierFilter,
                                                                                                               Long[] categoryIds,
                                                                                                               String globalFilter, String languageCode, Pageable pageable) {
        Page<RecordingUnit> res = recordingUnitRepository.findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                childId, fullIdentifierFilter, categoryIds, globalFilter, languageCode, pageable
        );

        return res.map(recordingUnitMapper::convert);
    }

    public Page<RecordingUnitDTO> findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(Long spatialUnitId,
                                                                                                                     String fullIdentifierFilter,
                                                                                                                     Long[] categoryIds,
                                                                                                                     String globalFilter, String languageCode, Pageable pageable

    ) {
        Page<RecordingUnit> res = recordingUnitRepository.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                spatialUnitId, fullIdentifierFilter, categoryIds, globalFilter, languageCode, pageable
        );

        return res.map(recordingUnitMapper::convert);
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
    public Integer countByActionContext(ActionUnitDTO actionUnit) {
        return recordingUnitRepository.countByActionContext(actionUnit.getId());
    }

    /**
     * Get all the roots recording unit in the institution
     *
     * @param institutionId the institution id
     * @return The list of RecordingUnit associated with the institution
     */
    @Transactional(readOnly = true)
    public List<RecordingUnitDTO> findAllWithoutParentsByInstitution(Long institutionId) {
        List<RecordingUnit> recordingUnits = recordingUnitRepository.findRootsByInstitution(institutionId);
        return recordingUnits.stream()
                .map(recordingUnitMapper::convert)
                .toList();
    }


    /**
     * Get all recording unit in the institution that are the children of a given parent
     *
     * @param parentId      the parent id
     * @param institutionId the institution id
     * @return The list of RecordingUnit associated with the institution and that are the children of a given parent
     */
    public List<RecordingUnitDTO> findChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        List<RecordingUnit> res = recordingUnitRepository.findChildrenByParentAndInstitution(parentId, institutionId);

        return res.stream()
                .map(recordingUnitMapper::convert)
                .toList();
    }

    /**
     * Does this unit has children?
     * @param parentId The parent ID
     * @param institutionId the institution ID
     * @return True if they are children
     */
    public boolean existsChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        return recordingUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId);
    }

    /**
     * Does the institution have recording units?
     * @param institutionId the institution ID
     * @return True if they are children
     */
    @Cacheable("InstitutionHasRootChildrenRU")
    public boolean existsRootChildrenByInstitution(Long institutionId) {
        return recordingUnitRepository.existsRootChildrenByInstitution(institutionId);
    }

    /**
     * Does the action have recording units?
     * @param actionId the action ID
     * @return True if they are children
     */
    @Cacheable("ActionHasRootChildrenRU")
    public boolean existsRootChildrenByAction(Long actionId) {
        return recordingUnitRepository.existsRootChildrenByAction(actionId);
    }

    /**
     * Get all recording unit that are the roots for a given action
     *
     * @param actionId the action id
     * @return The list of RecordingUnit that are the roots for a given action
     */
    public List<RecordingUnitDTO> findAllWithoutParentsByAction(Long actionId) {
        List<RecordingUnit> res = recordingUnitRepository.findRootsByAction(actionId);
        return res.stream()
                .map(recordingUnitMapper::convert)
                .toList();
    }

    public int generatedNextIdentifier(@NonNull ActionUnit actionUnit, @Nullable Concept unitType, @Nullable RecordingUnit parentRu) {
        ActionUnitResolveConfig config = actionUnit.resolveConfig();

        switch (config) {
            case UNIQUE, NONE -> {
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

    public RecordingUnit findByFullIdentifierAndInstitutionId(
            String fullIdentifier,
            Long institutionIdentifier) {

        return recordingUnitRepository
                .findByFullIdentifierAndInstitutionId(fullIdentifier, institutionIdentifier)
                .orElseThrow(() ->
                        new RecordingUnitNotFoundException(
                                "RecordingUnit not found with fullIdentifier="
                                        + fullIdentifier +
                                        " and institutionIdentifier="
                                        + institutionIdentifier));
    }

    public RecordingUnitDTO findByFullIdentifierAndInstitutionIdDTO(
            String fullIdentifier,
            Long institutionId,
            List<String> counts) {

        RecordingUnit entity = recordingUnitRepository
                .findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId)
                .orElseThrow(() ->
                        new RecordingUnitNotFoundException(
                                "RecordingUnit not found with fullIdentifier="
                                        + fullIdentifier +
                                        " and institutionId=" + institutionId));



        RecordingUnitDTO dto = recordingUnitMapper.convert(entity);

        // If "specimen" is in counts, fetch and set the specimen count
        if (counts != null && counts.contains("specimen") && dto != null) {
            Long specimenCount = recordingUnitRepository.countSpecimensByRecordingUnitId(entity.getId());
            dto.setSpecimenCount(specimenCount);
        }

        return dto;
    }

    public Page<RecordingUnitDTO> findByInstitutionId(Long institutionId,
                                                      int limit,
                                                      int offset) {
        Pageable pageable = PageRequest.of(offset, limit);
        Page<RecordingUnit> page = recordingUnitRepository.findByCreatedByInstitutionId(institutionId, pageable);
        return page.map(recordingUnitMapper::convert);
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
     * @param actionUnitDTO The action unit containing the identifier configuration.
     * @param recordingUnitDTO The recording unit to generate the identifier for.
     * @return The generated identifier.
     */
    public String generateFullIdentifier(@NonNull ActionUnitSummaryDTO actionUnitDTO, @NonNull RecordingUnitDTO recordingUnitDTO) {

        RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
        ActionUnit actionUnit = actionUnitSummaryMapper.invertConvert(actionUnitDTO);
        log.trace("Generating full identifier for recording unit");
        assert actionUnit != null;
        String format = actionUnit.getRecordingUnitIdentifierFormat();
        assert recordingUnit != null;
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
    public List<RecordingUnit> findByActionIdAndFullId(@NotNull Long actionUnitId, @NotNull String fullIdentifier) {
        return recordingUnitRepository.findByFullIdentifierAndActionUnitId(fullIdentifier, actionUnitId);
    }

    public List<RecordingUnitSummaryDTO> findAllByActionUnit(@NotNull Long actionUnitId) {

        return recordingUnitRepository
                .findAllByActionUnitId(actionUnitId)
                .stream()
                .map(unit -> conversionService.convert(unit, RecordingUnitSummaryDTO.class))
                .toList();
    }

    /**
     * Find all direct parents of a given SpatialUnit
     *
     * @param id The ID of the SpatialUnit to find parents for
     * @return A list of direct parents SpatialUnit of the given SpatialUnit
     */
    @Transactional(readOnly = true)
    public List<RecordingUnitDTO> findDirectParentsOf(Long id) {
        return recordingUnitRepository.findParentsOf(id).stream()
                .map(unit -> conversionService.convert(unit, RecordingUnitDTO.class))
                .toList();
    }

    /**
     * Find the next Recordingunit created by a specific action after the given one.
     * If there is no next, returns the oldest one (wraps around).
     *
     * @param action The action to find ActionUnits for
     * @param current The current ActionUnit to find the next one from
     * @return The next ActionUnitDTO, or the oldest one if there is no next
     */
    public RecordingUnitDTO findNextByActionUnit(ActionUnitSummaryDTO action, RecordingUnitDTO current) {
        return recordingUnitRepository
                .findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(
                        action.getId(), current.getCreationTime())
                .map(recordingUnitMapper::convert)
                .orElseGet(() -> recordingUnitRepository
                        .findFirstByActionUnitIdOrderByCreationTimeAsc(action.getId())
                        .map(recordingUnitMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + action.getId()))
                );
    }

    /**
     * Find the previous Recordingunit created by a specific action before the given one.
     * If there is no previous, returns the most recent one (wraps around).
     *
     * @param action The institution to find ActionUnits for
     * @param current The current ActionUnit to find the previous one from
     * @return The previous ActionUnitDTO, or the most recent one if there is no previous
     */
    public RecordingUnitDTO findPreviousByActionUnit(ActionUnitSummaryDTO action,
                                                      RecordingUnitDTO current) {
        return recordingUnitRepository
                .findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(
                        action.getId(), current.getCreationTime())
                .map(recordingUnitMapper::convert)
                .orElseGet(() -> recordingUnitRepository
                        .findFirstByActionUnitIdOrderByCreationTimeDesc(action.getId())
                        .map(recordingUnitMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + action.getId()))
                );
    }

    /**
     * Toggle the validated status of an RecordingUnit.
     *
     * @param id The id of the Recording unit to toggle
     * @return The updated RecordingUnitDTO
     */
    public RecordingUnitDTO toggleValidated(Long id) {
        RecordingUnit unit = recordingUnitRepository.findById(id)
                .orElseThrow(() -> new RecordingUnitNotFoundException("Recording not found with id: " + id));

        // Cycle through the enum values
        switch (unit.getValidated()) {
            case INCOMPLETE:
                unit.setValidated(COMPLETE);
                break;
            case COMPLETE:
                unit.setValidated(VALIDATED);
                break;
            case VALIDATED:
                unit.setValidated(INCOMPLETE);
                break;
            default:
                throw new IllegalStateException("Unknown status: " + unit.getValidated());
        }

        return recordingUnitMapper.convert(recordingUnitRepository.save(unit));
    }



}
