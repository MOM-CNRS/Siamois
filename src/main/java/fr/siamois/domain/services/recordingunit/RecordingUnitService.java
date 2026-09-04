package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitIdentifierAlreadyExistsException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.ArkRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.mapper.*;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.utils.CodeUtils;
import fr.siamois.utils.context.ExecutionContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    static final String RECORDING_UNIT_NOT_FOUND_WITH_ID = "RecordingUnit not found with ID: ";
    private final RecordingUnitRepository recordingUnitRepository;
    private final PersonRepository personRepository;
    private final ActionUnitService actionUnitService;
    private final ProfilePermissionService profilePermissionService;
    private final RecordingUnitMapper recordingUnitMapper;
    private final StratigraphicRelationshipRepository stratigraphicRelationshipRepository;
    private final StatigraphicRelationshipMapper stratigraphicRelationshipMapper;
    private final RecordingUnitSummaryMapper recordingUnitSummaryMapper;
    private final RecordingUnitSortFilterService recordingUnitSortFilterService;
    private final ConversionService conversionService;
    private final ActionUnitSummaryMapper actionUnitSummaryMapper;
    private final DocumentRepository documentRepository;
    private final ArkRepository arkRepository;
    private final PhaseRepository phaseRepository;
    private final PhaseMapper phaseMapper;
    private final UnitDefinitionService unitDefinitionService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final EntityIdentifierGenerator entityIdentifierGenerator;


    /**
     * Controls flushing before the native generic counter allocation,
     * qui sinon forcent un flush Hibernate sur des instances transient issues d'{@code invertConvert}.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Self-reference resolved through the Spring proxy, so that calls made from within this same
     * class (e.g. {@link #duplicateStructure}'s per-node work calling {@link #save(RecordingUnitDTO)})
     * still go through AOP-backed behavior such as {@code @CacheEvict} — a direct {@code this.save(...)}
     * call bypasses the proxy entirely and silently skips it. {@code @Lazy} avoids a circular
     * dependency at construction time.
     */
    @Lazy
    @Autowired
    private RecordingUnitService self;

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
        if (unit.getActionUnit() == null) {
            return false;
        }
        List<RecordingUnit> existing = findByActionIdAndFullId(unit.getActionUnit().getId(), unit.getFullIdentifier());
        return existing.stream()
                .anyMatch(r -> Objects.equals(r.getFullIdentifier(), unit.getFullIdentifier()) && !Objects.equals(r.getId(),
                        unit.getId()));
    }

    /**
     * Save a recording unit with its associated concept and related units.
     *
     * @param recordingUnitDTO The recording unit to save.
     * @return The saved RecordingUnit instance.
     */
    @CacheEvict({
            "InstitutionHasRootChildrenRU",
            "ActionHasRootChildrenRU"
    })
    public RecordingUnitDTO save(RecordingUnitDTO recordingUnitDTO) {
        assertWritePermission(recordingUnitDTO);
        try {
            RecordingUnit recordingUnit =
                    recordingUnitMapper.invertConvert(recordingUnitDTO);

            return recordingUnitMapper.convert(save(recordingUnit));
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
    }

    /**
     * Save a recording unit along with the answers to its additional (non-system) fields.
     *
     * @param recordingUnitDTO      The recording unit to save.
     * @param additionalFieldAnswers Answers to additional (non-system) fields, keyed by CustomField.
     * @return The saved RecordingUnit instance.
     */
    @CacheEvict({
            "InstitutionHasRootChildrenRU",
            "ActionHasRootChildrenRU"
    })
    @Transactional
    public RecordingUnitDTO save(RecordingUnitDTO recordingUnitDTO, Map<CustomField, CustomFieldAnswerViewModel> additionalFieldAnswers) {
        RecordingUnitDTO saved = save(recordingUnitDTO);
        try {
            customFieldAnswerService.saveAdditionalFieldAnswers(saved, additionalFieldAnswers);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
        return saved;
    }

    /**
     * Record that a measurement field was created from this recording unit's form, so the unit stays
     * the field's origin and keeps offering it among its existing measurement fields.
     *
     * @param recordingUnitId The recording unit the field was created from.
     * @param field           The freshly created measurement field.
     */
    @Transactional
    public void addMeasurementField(Long recordingUnitId, CustomFieldMeasurement field) {
        RecordingUnit recordingUnit = recordingUnitRepository.findById(recordingUnitId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + recordingUnitId));
        recordingUnit.getOnTheFlyFields().add(field);
        recordingUnitRepository.save(recordingUnit);
    }

    @Transactional
    public void updateStratigraphicRel(RecordingUnitDTO recordingUnitDTO) {
        RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
        assert recordingUnit != null;
        RecordingUnit managedRecordingUnit = newOrGetRecordingUnit(recordingUnit);
        setupStratigraphicRelationships(recordingUnit, managedRecordingUnit);
    }


    protected RecordingUnit save(RecordingUnit recordingUnit) {
        try {
            RecordingUnit managedRecordingUnit;

            assert recordingUnit != null;
            managedRecordingUnit = newOrGetRecordingUnit(recordingUnit);

            setupStratigraphicRelationships(recordingUnit, managedRecordingUnit);
            setupSpatialUnit(recordingUnit, managedRecordingUnit);
            managedRecordingUnit.setActionUnit(recordingUnit.getActionUnit());
            managedRecordingUnit.setCreatedByInstitution(recordingUnit.getCreatedByInstitution());
            managedRecordingUnit.setFullIdentifier(recordingUnit.getFullIdentifier());
            if (managedRecordingUnit.getFullIdentifier() == null) {
                // Temporary code for the save
                managedRecordingUnit.setFullIdentifier(CodeUtils.generateCode(20));
            }
            setupOtherFields(recordingUnit, managedRecordingUnit);
            synchronizeCollection(managedRecordingUnit.getPhases(), recordingUnit.getPhases());

            // IMPORTANT: keep working with the instance save() RETURNS, never with the one passed in.
            // syncRevision is a @Version wrapper initialised to 0L, so Spring Data's isNew() check
            // sees a non-null version and treats even a brand-new unit as detached: it calls
            // em.merge(), which returns a *different* managed copy and leaves the argument transient
            // with a null id forever. Handing that transient instance to setupParents() below planted
            // it inside an existing parent's children collection, and the flush then blew up with
            // "references an unsaved transient instance" - only ever for units that have a parent.
            RecordingUnit savedRecordingUnit = recordingUnitRepository.save(managedRecordingUnit);

            setupParents(recordingUnit, savedRecordingUnit);
            setupChilds(recordingUnit, savedRecordingUnit);
            return savedRecordingUnit;

        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw new FailedRecordingUnitSaveException(e.getMessage());
        }
    }

    private <T> void synchronizeCollection(java.util.Collection<T> managed, java.util.Collection<T> incoming) {
        if (managed == null) return;
        if (incoming == null || incoming.isEmpty()) {
            managed.clear();
            return;
        }
        managed.retainAll(incoming);
        for (T item : incoming) {
            if (!managed.contains(item)) managed.add(item);
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

    private void setupChilds(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        if (recordingUnit.getChildren() == null) {
            return;
        }

        Set<Long> incomingIds = recordingUnit.getChildren().stream()
                .filter(c -> c != null && c.getId() != null)
                .map(RecordingUnit::getId)
                .collect(Collectors.toSet());

        managedRecordingUnit.getChildren().removeIf(child -> !incomingIds.contains(child.getId()));

        Set<Long> presentIds = managedRecordingUnit.getChildren().stream()
                .map(RecordingUnit::getId)
                .collect(Collectors.toSet());
        Set<Long> missingIds = incomingIds.stream()
                .filter(id -> !presentIds.contains(id))
                .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            List<RecordingUnit> missing = (List<RecordingUnit>) recordingUnitRepository.findAllById(missingIds);
            if (missing.size() != missingIds.size()) {
                throw new IllegalArgumentException("Some children not found: " + missingIds);
            }
            managedRecordingUnit.getChildren().addAll(missing);
        }
    }

    private void setupParents(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
        if (recordingUnit.getParents() == null) {
            return;
        }

        Set<RecordingUnit> currentParents = new HashSet<>(managedRecordingUnit.getParents());

        List<Long> newParentIds = recordingUnit.getParents().stream()
                .filter(p -> p != null && p.getId() != null)
                .map(RecordingUnit::getId)
                .toList();
        List<RecordingUnit> loadedParents = (List<RecordingUnit>) recordingUnitRepository.findAllById(newParentIds);
        if (loadedParents.size() != newParentIds.size()) {
            throw new IllegalArgumentException("Some parents not found: " + newParentIds);
        }
        Set<RecordingUnit> newParents = new HashSet<>(loadedParents);

        List<RecordingUnit> toSave = new ArrayList<>();

        for (RecordingUnit parent : newParents) {
            if (!currentParents.contains(parent)) {
                parent.getChildren().add(managedRecordingUnit);
                toSave.add(parent);
            }
        }
        for (RecordingUnit parent : currentParents) {
            if (!newParents.contains(parent)) {
                parent.getChildren().remove(managedRecordingUnit);
                toSave.add(parent);
            }
        }
        recordingUnitRepository.saveAll(toSave);

        managedRecordingUnit.getParents().clear();
        managedRecordingUnit.getParents().addAll(newParents);
    }


    private void setupOtherFields(RecordingUnit recordingUnit, RecordingUnit managedRecordingUnit) {
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
        managedRecordingUnit.setComments(recordingUnit.getComments());

        // altimetry
        managedRecordingUnit.setZInf(mergeMeasurementAnswer(
                recordingUnit.getZInf(), managedRecordingUnit.getZInf()));
        managedRecordingUnit.setZSup(mergeMeasurementAnswer(
                recordingUnit.getZSup(), managedRecordingUnit.getZSup()));

        if (managedRecordingUnit.getCreatedBy() == null) {
            managedRecordingUnit.setCreatedBy(recordingUnit.getCreatedBy());
        }


        managedRecordingUnit.setChronologicalAttribution(recordingUnit.getChronologicalAttribution());
        managedRecordingUnit.setGeomorphologicalAgent(recordingUnit.getGeomorphologicalAgent());

    }

    @Nullable
    private MeasurementAnswer mergeMeasurementAnswer(@Nullable MeasurementAnswer incoming,
                                                     @Nullable MeasurementAnswer managed) {
        if (incoming == null) {
            return null;
        }
        MeasurementAnswer target = managed != null ? managed : new MeasurementAnswer();
        target.setNumericValue(incoming.getNumericValue());
        target.setComment(incoming.getComment());
        target.setNormalizedValue(incoming.getNormalizedValue());
        target.setUnit(unitDefinitionService.resolve(incoming.getUnit()));
        return target;
    }

    private void setupStratigraphicRelationships(RecordingUnit source, RecordingUnit target) {
        syncRelationships(target, source.getRelationshipsAsUnit1(), true);
        syncRelationships(target, source.getRelationshipsAsUnit2(), false);
    }

    void syncRelationships(
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
                    .orElseThrow(() -> new RecordingUnitNotFoundException(RECORDING_UNIT_NOT_FOUND_WITH_ID + id));
            RecordingUnitDTO dto = recordingUnitMapper.convert(recordingUnit);
            dto.setSpecimenCount(recordingUnitRepository.countSpecimensByRecordingUnitId(id));
            return dto;
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * UE résolue avec l'entité JPA (réponses formulaire persistées) et le DTO métier.
     */
    public record AccessibleRecordingUnit(RecordingUnit entity, RecordingUnitDTO dto) {
    }

    /**
     * Relations exposées pour l'API (stratigraphie + hiérarchie parent/enfant).
     */
    public record RecordingUnitRelationsBundle(List<StratigraphicRelationshipDTO> stratigraphicRelationships,
                                               List<RecordingUnitSummaryDTO> parents,
                                               List<RecordingUnitSummaryDTO> children) {
    }

    /**
     * Résout une UE pour les institutions accessibles à l'utilisateur (API OpenAPI).
     * <ul>
     *     <li>Si la clé est composée uniquement de chiffres : recherche par {@code recording_unit_id} ;
     *     si aucune ligne, repli sur {@link #resolveRecordingUnitEntityByFullIdentifier(String, Set)}.</li>
     *     <li>Sinon : recherche par {@code full_identifier} dans les institutions accessibles (ordre d'id croissant).</li>
     * </ul>
     *
     * @param counts si la liste contient {@code "specimen"}, charge le nombre de spécimens associés.
     */
    @Transactional(readOnly = true)
    public AccessibleRecordingUnit findAccessibleRecordingUnitWithEntity(String idOrKey,
                                                                         Set<Long> accessibleInstitutionIds,
                                                                         List<String> counts) {
        return resolveAccessibleRecordingUnit(idOrKey, accessibleInstitutionIds, counts);
    }

    private AccessibleRecordingUnit resolveAccessibleRecordingUnit(String idOrKey,
                                                                   Set<Long> accessibleInstitutionIds,
                                                                   List<String> counts) {
        RecordingUnit entity = resolveRecordingUnitEntityByKey(idOrKey, accessibleInstitutionIds);
        RecordingUnitDTO dto = recordingUnitMapper.convert(entity);
        if (counts != null && counts.contains("specimen") && dto.getId() != null) {
            dto.setSpecimenCount(recordingUnitRepository.countSpecimensByRecordingUnitId(dto.getId()));
        }
        return new AccessibleRecordingUnit(entity, dto);
    }

    /**
     * Résout une UE pour les institutions accessibles à l'utilisateur (API OpenAPI).
     *
     * @param counts si la liste contient {@code "specimen"}, charge le nombre de spécimens associés.
     */
    @Transactional(readOnly = true)
    public RecordingUnitDTO findAccessibleRecordingUnitByKey(String idOrKey,
                                                             Set<Long> accessibleInstitutionIds,
                                                             List<String> counts) {
        return resolveAccessibleRecordingUnit(idOrKey, accessibleInstitutionIds, counts).dto();
    }

    /**
     * UE identifiée par sa clé primaire {@code recording_unit_id}, si l'institution de l'UE est dans le périmètre.
     * (Contrairement à {@link #findAccessibleRecordingUnitByKey}, aucun repli sur un identifiant métier numérique.)
     */
    @Transactional(readOnly = true)
    public RecordingUnitDTO requireAccessibleRecordingUnitByPrimaryKey(long recordingUnitId,
                                                                       Set<Long> accessibleInstitutionIds) {
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            throw new RecordingUnitNotFoundException("No institution scope for current user");
        }
        RecordingUnit entity = recordingUnitRepository.findById(recordingUnitId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + recordingUnitId));
        RecordingUnitDTO dto = recordingUnitMapper.convert(entity);
        Long instId = dto.getCreatedByInstitution() == null ? null : dto.getCreatedByInstitution().getId();
        if (instId == null || !accessibleInstitutionIds.contains(instId)) {
            throw new RecordingUnitNotFoundException("Recording unit not found or not accessible: " + recordingUnitId);
        }
        return dto;
    }

    /**
     * Supprime une UE sans mobiliers, sans études liées et sans UE filles en hiérarchie.
     * Nettoie les liaisons (stratigraphie, documents, identifiants, formulaire, ARK).
     *
     * @throws IllegalStateException si la suppression est interdite (contenu bloquant)
     */
    @Transactional
    @CacheEvict({
            "InstitutionHasRootChildrenRU",
            "ActionHasRootChildrenRU"
    })
    public void deleteRecordingUnitById(long recordingUnitId) {
        RecordingUnit ru = recordingUnitRepository.findById(recordingUnitId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + recordingUnitId));

        UserInfo info = ExecutionContextHolder.get();
        Long actionUnitId = ru.getActionUnit() != null ? ru.getActionUnit().getId() : null;
        if (info == null || !profilePermissionService.hasProjectPermission(info, actionUnitId, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)) {
            throw new ForbiddenOperationException("You are not allowed to delete this recording unit");
        }

        validateDeletable(ru, recordingUnitId);
        unlinkParentChildRelations(ru);
        clearStratigraphicRelationships(ru, recordingUnitId);
        deleteLinkedData(recordingUnitId, ru);

        Long arkId = ru.getArk() != null ? ru.getArk().getInternalId() : null;

        recordingUnitRepository.save(ru);

        recordingUnitRepository.delete(ru);
        if (arkId != null) {
            arkRepository.deleteById(arkId);
        }
    }

    private void validateDeletable(RecordingUnit ru, long recordingUnitId) {
        Long specimenCount = recordingUnitRepository.countSpecimensByRecordingUnitId(recordingUnitId);
        if (specimenCount != null && specimenCount > 0) {
            throw new IllegalStateException("Impossible de supprimer : l'unité d'enregistrement contient des mobiliers");
        }
        if (ru.getChildren() != null && !ru.getChildren().isEmpty()) {
            throw new IllegalStateException("Impossible de supprimer : l'unité d'enregistrement possède des unités filles");
        }
    }

    private void unlinkParentChildRelations(RecordingUnit ru) {
        if (ru.getParents() != null) {
            for (RecordingUnit parent : new HashSet<>(ru.getParents())) {
                if (parent.getChildren() != null) {
                    parent.getChildren().remove(ru);
                }
            }
            ru.getParents().clear();
        }
        if (ru.getChildren() != null) {
            ru.getChildren().clear();
        }
    }

    private void clearStratigraphicRelationships(RecordingUnit ru, long recordingUnitId) {
        List<StratigraphicRelationship> rels = stratigraphicRelationshipRepository.findAllInvolvingRecordingUnitId(recordingUnitId);
        if (!rels.isEmpty()) {
            stratigraphicRelationshipRepository.deleteAll(rels);
        }
        if (ru.getRelationshipsAsUnit1() != null) {
            ru.getRelationshipsAsUnit1().clear();
        }
        if (ru.getRelationshipsAsUnit2() != null) {
            ru.getRelationshipsAsUnit2().clear();
        }
    }

    private void deleteLinkedData(long recordingUnitId, RecordingUnit ru) {
        recordingUnitRepository.deleteContributorLinksForRecordingUnit(recordingUnitId);
        documentRepository.deleteAllRecordingUnitDocumentLinksByRecordingUnitId(recordingUnitId);
        if (ru.getContributors() != null) {
            ru.getContributors().clear();
        }
        if (ru.getDocuments() != null) {
            ru.getDocuments().clear();
        }
    }

    /**
     * Relations liées à une UE : stratigraphie (unit1 ou unit2) et liens hiérarchiques
     * ({@link RecordingUnit#getParents()} / {@link RecordingUnit#getChildren()}), dans le périmètre
     * d'institutions accessibles pour la résolution de l'UE cible.
     */
    @Transactional(readOnly = true)
    public RecordingUnitRelationsBundle findRelationsForAccessibleRecordingUnit(String idOrKey,
                                                                                Set<Long> accessibleInstitutionIds) {
        RecordingUnit unit = resolveAccessibleRecordingUnit(idOrKey, accessibleInstitutionIds, null).entity();
        Long id = unit.getId();
        List<StratigraphicRelationshipDTO> stratigraphic = stratigraphicRelationshipRepository
                .findAllInvolvingRecordingUnitId(id).stream()
                .map(stratigraphicRelationshipMapper::convert)
                .toList();
        List<RecordingUnitSummaryDTO> parents = toAdjacentUnitSummaries(unit.getParents());
        List<RecordingUnitSummaryDTO> children = toAdjacentUnitSummaries(unit.getChildren());
        return new RecordingUnitRelationsBundle(stratigraphic, parents, children);
    }

    /**
     * Unités d'enregistrement filles directes (table {@code recording_unit_hierarchy}) pour une UE accessible.
     * Même résolution de clé et périmètre institutionnel que {@link #findRelationsForAccessibleRecordingUnit(String, Set)}.
     */
    @Transactional(readOnly = true)
    public List<RecordingUnitSummaryDTO> findChildrenForAccessibleRecordingUnit(String idOrKey,
                                                                                Set<Long> accessibleInstitutionIds) {
        RecordingUnit unit = resolveAccessibleRecordingUnit(idOrKey, accessibleInstitutionIds, null).entity();
        return toAdjacentUnitSummaries(unit.getChildren());
    }

    /**
     * Lie une UE existante comme enfant direct d'une autre (table {@code recording_unit_hierarchy}).
     */
    @Transactional
    @CacheEvict({
            "InstitutionHasRootChildrenRU",
            "ActionHasRootChildrenRU"
    })
    public void addHierarchyChild(long parentId, long childId) {
        if (parentId == childId) {
            throw new IllegalArgumentException("Une unité d'enregistrement ne peut pas être son propre enfant");
        }

        RecordingUnit parent = recordingUnitRepository.findById(parentId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + parentId));
        RecordingUnit child = recordingUnitRepository.findById(childId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + childId));

        assertSameActionUnit(parent, child);

        if (parent.getChildren() != null && parent.getChildren().contains(child)) {
            throw new IllegalStateException("Cette relation parent/enfant existe déjà");
        }

        if (wouldCreateHierarchyCycle(parentId, childId)) {
            throw new IllegalStateException("Impossible de créer la relation : cycle hiérarchique détecté");
        }

        parent.getChildren().add(child);
        child.getParents().add(parent);
        recordingUnitRepository.save(parent);
    }

    /**
     * Supprime le lien hiérarchique direct parent → enfant.
     */
    @Transactional
    @CacheEvict({
            "InstitutionHasRootChildrenRU",
            "ActionHasRootChildrenRU"
    })
    public void removeHierarchyChild(long parentId, long childId) {
        RecordingUnit parent = recordingUnitRepository.findById(parentId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + parentId));
        RecordingUnit child = recordingUnitRepository.findById(childId)
                .orElseThrow(() -> new RecordingUnitNotFoundException(
                        RECORDING_UNIT_NOT_FOUND_WITH_ID + childId));

        if (parent.getChildren() == null || !parent.getChildren().contains(child)) {
            throw new IllegalStateException("Aucune relation parent/enfant directe entre ces unités");
        }

        parent.getChildren().remove(child);
        child.getParents().remove(parent);
        recordingUnitRepository.save(parent);
    }

    private void assertSameActionUnit(RecordingUnit first, RecordingUnit second) {
        Long firstActionId = first.getActionUnit() == null ? null : first.getActionUnit().getId();
        Long secondActionId = second.getActionUnit() == null ? null : second.getActionUnit().getId();
        if (firstActionId == null || secondActionId == null || !Objects.equals(firstActionId, secondActionId)) {
            throw new IllegalArgumentException("Les unités d'enregistrement doivent appartenir au même projet");
        }
    }

    private boolean wouldCreateHierarchyCycle(long parentId, long childId) {
        List<Long> ancestorsOfParent = recordingUnitRepository.findAncestorClosure(new Long[]{parentId});
        return ancestorsOfParent.contains(childId);
    }

    private List<RecordingUnitSummaryDTO> toAdjacentUnitSummaries(Set<RecordingUnit> adjacent) {
        if (adjacent == null || adjacent.isEmpty()) {
            return List.of();
        }
        return adjacent.stream()
                .map(recordingUnitSummaryMapper::convert)
                .sorted(Comparator.comparing(RecordingUnitSummaryDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private RecordingUnit resolveRecordingUnitEntityByKey(String idOrKey, Set<Long> accessibleInstitutionIds) {
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            throw new RecordingUnitNotFoundException("No institution scope for current user");
        }
        String key = idOrKey == null ? "" : idOrKey.trim();
        if (key.isEmpty()) {
            throw new RecordingUnitNotFoundException("Recording unit key must not be empty");
        }
        if (key.chars().allMatch(Character::isDigit)) {
            return resolveByNumericKey(key, accessibleInstitutionIds);
        }
        return resolveRecordingUnitEntityByFullIdentifier(key, accessibleInstitutionIds);
    }

    private RecordingUnit resolveByNumericKey(String key, Set<Long> accessibleInstitutionIds) {
        try {
            long numericId = Long.parseLong(key);
            Optional<RecordingUnit> byPk = recordingUnitRepository.findById(numericId);
            if (byPk.isEmpty()) {
                return resolveRecordingUnitEntityByFullIdentifier(key, accessibleInstitutionIds);
            }
            RecordingUnit entity = byPk.get();
            RecordingUnitDTO converted = recordingUnitMapper.convert(entity);
            Long instId = converted.getCreatedByInstitution() == null ? null
                    : converted.getCreatedByInstitution().getId();
            if (instId != null && accessibleInstitutionIds.contains(instId)) {
                return entity;
            }
            throw new RecordingUnitNotFoundException("Recording unit not found with key: " + key);
        } catch (NumberFormatException e) {
            return resolveRecordingUnitEntityByFullIdentifier(key, accessibleInstitutionIds);
        }
    }

    private RecordingUnit resolveRecordingUnitEntityByFullIdentifier(String fullIdentifier,
                                                                     Set<Long> accessibleInstitutionIds) {
        return recordingUnitRepository.findFirstByFullIdentifierAndInstitutionIdIn(fullIdentifier, accessibleInstitutionIds)
                .orElseThrow(() -> new RecordingUnitNotFoundException("Recording unit not found with key: " + fullIdentifier));
    }

    @Override
    public List<? extends ArkEntity> findWithoutArk(Institution institution) {
        return recordingUnitRepository.findAllWithoutArkOfInstitution(institution.getId());
    }

    @Override
    public RecordingUnitDTO save(AbstractEntityDTO toSave) {
        assertWritePermission((RecordingUnitDTO) toSave);
        RecordingUnit toReturn = recordingUnitRepository.save(Objects.requireNonNull(recordingUnitMapper.invertConvert((RecordingUnitDTO) toSave)));
        return recordingUnitMapper.convert(toReturn);
    }

    private void assertWritePermission(RecordingUnitDTO recordingUnitDTO) {
        UserInfo info = ExecutionContextHolder.get();
        if (info == null || !profilePermissionService.hasRecordingUnitWritePermission(info, recordingUnitDTO)) {
            throw new ForbiddenOperationException("You are not allowed to edit this recording unit");
        }
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
     * Bulk version of {@link #countByInstitutionId} : recording unit count for every institution id in the
     * collection, in one query instead of one per institution — used to render an institution list's
     * recording unit count column without an N+1.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> countByInstitutionIds(Collection<Long> institutionIds) {
        if (institutionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : recordingUnitRepository.countByCreatedByInstitutionIds(institutionIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
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
        return profilePermissionService.hasOrganizationPermission(
                user.getUser(), action.getCreatedByInstitution(), PermissionConstants.ORGANIZATION_MANAGE_SETTINGS) ||
                actionUnitService.isManagerOf(action, user.getUser()) ||
                (profilePermissionService.hasProjectPermission(user, action.getId(), PermissionConstants.PROJECT_EDIT_FINDS)
                        && actionUnitService.isActionUnitStillOngoing(action));
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
     *
     * @param parentId      The parent ID
     * @param institutionId the institution ID
     * @return True if they are children
     */
    public boolean existsChildrenByParentAndInstitution(Long parentId, Long institutionId) {
        return recordingUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId);
    }

    /**
     * Does the institution have recording units?
     *
     * @param institutionId the institution ID
     * @return True if they are children
     */
    @Cacheable("InstitutionHasRootChildrenRU")
    public boolean existsRootChildrenByInstitution(Long institutionId) {
        return recordingUnitRepository.existsRootChildrenByInstitution(institutionId);
    }

    /**
     * Does the action have recording units?
     *
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

    public RecordingUnit findByFullIdentifierAndInstitutionIdentifier(String identifier, String institutionIdentifier) {
        return recordingUnitRepository.findByFullIdentifierAndInstitutionIdentifier(identifier, institutionIdentifier).orElse(null);
    }

    public Page<RecordingUnitDTO> findByActionUnitId(Long actionUnitId, int limit, int offset, Sort sort) {
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit, sort);
        Specification<RecordingUnit> specs = Specification.where(RecordingUnitSpec.recordingUnitInActionUnit(actionUnitId));
        return pageAndEnrich(specs, pageable, false);
    }


    /**
     * Generates the identifier for a recording unit that has no parent.
     * Its creation parameters therefore refer directly to the action unit.
     *
     * @param actionUnitDTO    The action unit containing the identifier configuration.
     * @param recordingUnitDTO The recording unit to generate the identifier for.
     * @return The generated identifier.
     */
    @Transactional
    public String generateFullIdentifier(@NonNull ActionUnitSummaryDTO actionUnitDTO, @NonNull RecordingUnitDTO recordingUnitDTO) {
        Long ruId = recordingUnitDTO.getId();
        if (ruId == null) {
            // Création non encore persistée (cas UI rares) : mapping DTO → entité.
            RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
            ActionUnit actionUnit = actionUnitSummaryMapper.invertConvert(actionUnitDTO);
            String generated = generateFullIdentifier(actionUnit, recordingUnit);
            recordingUnitDTO.setIdentifier(String.valueOf(recordingUnit.getIdentifier()));
            recordingUnitDTO.setFullIdentifier(generated);
            return generated;
        }

        // Ne pas passer par invertConvert : cela crée une RecordingUnit transient
        // (même id) qui ferait échouer le flush auto déclenché par le compteur natif.
        jakarta.persistence.FlushModeType previousFlushMode = null;
        if (entityManager != null) {
            previousFlushMode = entityManager.getFlushMode();
            entityManager.setFlushMode(jakarta.persistence.FlushModeType.COMMIT);
        }
        try {
            RecordingUnit recordingUnit = recordingUnitRepository.findById(ruId)
                    .orElseThrow(() -> new RecordingUnitNotFoundException(RECORDING_UNIT_NOT_FOUND_WITH_ID + ruId));
            ActionUnit actionUnit = recordingUnit.getActionUnit();
            if (actionUnit == null && actionUnitDTO.getId() != null) {
                actionUnit = actionUnitSummaryMapper.invertConvert(actionUnitDTO);
            }
            if (actionUnit == null) {
                throw new ActionUnitNotFoundException(
                        "Action unit missing for recording unit " + ruId);
            }
            String generated = generateFullIdentifier(actionUnit, recordingUnit);
            recordingUnitDTO.setIdentifier(String.valueOf(recordingUnit.getIdentifier()));
            recordingUnitDTO.setFullIdentifier(generated);
            return generated;
        } finally {
            if (entityManager != null && previousFlushMode != null) {
                entityManager.setFlushMode(previousFlushMode);
            }
        }
    }

    public String generateFullIdentifier(@NonNull ActionUnit actionUnit, @NonNull RecordingUnit recordingUnit) {
        log.trace("Generating full identifier for recording unit");
        return entityIdentifierGenerator.generateIdentifierIfRequired(
                        recordingUnit, recordingUnitIdentifierSpec(actionUnit))
                .orElseThrow(() -> new IllegalStateException("Recording-unit identifier generation was skipped"))
                .value();
    }

    private IdentifierGenerationSpec<RecordingUnit> recordingUnitIdentifierSpec(ActionUnit actionUnit) {
        return IdentifierGenerationSpec.<RecordingUnit>builder()
                .table(ConfigurableTable.UE)
                .entityName("recording unit")
                .generationRequired(recordingUnit -> true)
                .actionUnit(recordingUnit -> actionUnit)
                .typeId(recordingUnit -> recordingUnit.getType() == null
                        ? null : recordingUnit.getType().getId())
                .displayValue("NUM_PARENT", recordingUnit -> {
                    RecordingUnit parent = deterministicParent(recordingUnit);
                    return parent == null ? null : parent.getIdentifier();
                })
                .displayValue("ID_PARENT", recordingUnit -> {
                    RecordingUnit parent = deterministicParent(recordingUnit);
                    return parent == null ? null : parent.getFullIdentifier();
                })
                .displayValue("NUM_USPATIAL", recordingUnit -> recordingUnit.getSpatialUnit() == null
                        ? null : recordingUnit.getSpatialUnit().getPlaceNumber())
                .displayValue("ID_UA", recordingUnit -> actionUnit.getFullIdentifier())
                .partitionValue("PARENT_RU", recordingUnit -> {
                    RecordingUnit parent = deterministicParent(recordingUnit);
                    return parent == null ? null : parent.getId();
                })
                .partitionValue("SPATIAL_PLACE", recordingUnit -> recordingUnit.getSpatialUnit() == null
                        ? null : recordingUnit.getSpatialUnit().getPlaceNumber())
                .identifierAlreadyUsed((recordingUnit, candidate) ->
                        identifierBelongsToAnotherUnit(actionUnit.getId(), recordingUnit.getId(), candidate))
                .numberSetter(RecordingUnit::setIdentifier)
                .identifierSetter(RecordingUnit::setFullIdentifier)
                .build();
    }

    private static RecordingUnit deterministicParent(RecordingUnit recordingUnit) {
        if (recordingUnit.getParents() == null) return null;
        return recordingUnit.getParents().stream()
                .filter(Objects::nonNull)
                .filter(parent -> parent.getId() != null)
                .min(Comparator.comparing(RecordingUnit::getId))
                .orElse(null);
    }

    private boolean identifierBelongsToAnotherUnit(Long actionUnitId, Long recordingUnitId, String fullIdentifier) {
        return recordingUnitRepository.findByFullIdentifierAndActionUnitId(fullIdentifier, actionUnitId).stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), recordingUnitId));
    }

    /**
     * Duplicates {@code root} and, level by level, the descendants of the hierarchy under it whose
     * id is in {@code selectedIds} — {@code count} independent exemplars of the whole selected
     * structure. Processing one full depth of the tree before the next guarantees that a child's
     * new parent (whose identifier and id the child's own identifier generation may depend on,
     * e.g. {@code NUM_PARENT}/{@code ID_PARENT}) always exists and is fully persisted first.
     * <p>
     * A duplicated descendant is attached only to the copy of its parent within this structure —
     * any other parent it had in the original hierarchy is not reproduced.
     *
     * @param root        the entity to duplicate; must already be persisted
     * @param selectedIds ids (within {@code root}'s descendants, {@code root}'s own id is implicit)
     *                    to include in the duplicated structure
     * @param count       how many independent copies of the structure to create (at least 1)
     */
    @Transactional
    public RecordingUnitStructureDuplicationResult duplicateStructure(
            @NonNull RecordingUnitDTO root, @NonNull Set<Long> selectedIds, int count) {

        if (root.getId() == null) {
            throw new IllegalArgumentException("Root recording unit must be persisted");
        }
        int copies = Math.max(1, count);

        UserInfo info = ExecutionContextHolder.get();
        Long actionUnitId = root.getActionUnit() != null ? root.getActionUnit().getId() : null;
        if (info == null || !profilePermissionService.hasProjectPermission(info, actionUnitId, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)) {
            throw new ForbiddenOperationException("You are not allowed to duplicate this recording unit");
        }

        List<RecordingUnitDTO> allCreated = new ArrayList<>();
        Map<Long, List<RecordingUnitDTO>> copiesByOriginalId = new LinkedHashMap<>();

        List<RecordingUnitDTO> rootCopies = new ArrayList<>(copies);
        for (int i = 0; i < copies; i++) {
            rootCopies.add(duplicateSingleRecordingUnit(root, root.getParents(), info));
        }
        allCreated.addAll(rootCopies);
        copiesByOriginalId.put(root.getId(), rootCopies);

        Deque<RecordingUnitDTO> queue = new ArrayDeque<>();
        queue.add(root);
        Set<Long> visited = new HashSet<>();
        visited.add(root.getId());

        while (!queue.isEmpty()) {
            RecordingUnitDTO originalParent = queue.poll();
            List<RecordingUnitDTO> parentCopies = copiesByOriginalId.get(originalParent.getId());

            for (RecordingUnitDTO originalChild : findAllByParentRecordingUnit(originalParent.getId())) {
                if (!selectedIds.contains(originalChild.getId()) || !visited.add(originalChild.getId())) {
                    continue;
                }

                List<RecordingUnitDTO> childCopies = new ArrayList<>(copies);
                for (int i = 0; i < copies; i++) {
                    Set<RecordingUnitSummaryDTO> parentForCopy =
                            new HashSet<>(Set.of(new RecordingUnitSummaryDTO(parentCopies.get(i))));
                    childCopies.add(duplicateSingleRecordingUnit(originalChild, parentForCopy, info));
                }
                allCreated.addAll(childCopies);
                copiesByOriginalId.put(originalChild.getId(), childCopies);
                queue.add(originalChild);
            }
        }

        return new RecordingUnitStructureDuplicationResult(rootCopies, allCreated);
    }

    private RecordingUnitDTO duplicateSingleRecordingUnit(
            RecordingUnitDTO source, Set<RecordingUnitSummaryDTO> parents, UserInfo info) {

        RecordingUnitDTO copy = new RecordingUnitDTO(source);
        copy.setParents(parents == null ? new HashSet<>() : new HashSet<>(parents));
        copy.setAuthor(info.getUser());
        copy.setCreatedBy(info.getUser());

        // A node is built over two save() calls with an identifier lookup in between, so it is only
        // fully consistent at the end. Hold back Hibernate's automatic pre-query flush for the whole
        // sequence (same guard as generateFullIdentifier()'s native counter lookup) and flush once,
        // deliberately, when the node is complete - before the next node in the structure references
        // it as its parent.
        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            copy = self.save(copy);
            copy.setFullIdentifier(self.generateFullIdentifier(copy.getActionUnit(), copy));
            if (self.fullIdentifierAlreadyExistInAction(copy)) {
                throw new RecordingUnitIdentifierAlreadyExistsException(copy.getFullIdentifier());
            }
            copy = self.save(copy);
            entityManager.flush();
        } finally {
            entityManager.setFlushMode(previousFlushMode);
        }
        return copy;
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
     * The recording units of an institution whose full identifier matches the query — what the
     * table's parents/children filters autocomplete on, where the scope is the whole institution
     * rather than one action unit.
     *
     * @param institution the institution to search in
     * @param query       the text the full identifier must contain, null or blank matching everything
     * @param limit       how many units to return at most
     */
    @Transactional(readOnly = true)
    public List<RecordingUnitSummaryDTO> findMatchingInInstitutionByFullIdentifier(InstitutionDTO institution, String query, int limit) {
        Specification<RecordingUnit> specs = RecordingUnitSpec.recordingUnitInInstitution(institution.getId())
                .and(RecordingUnitSpec.fullIdentifierContains(query));

        return recordingUnitRepository.findAll(specs, PageRequest.ofSize(limit))
                .stream()
                .map(recordingUnitSummaryMapper::convert)
                .toList();
    }

    public List<RecordingUnitSummaryDTO> autocompleteInActionUnit(@NotNull Long actionUnitId, String query, int limit) {
        return recordingUnitRepository
                .findByActionUnitIdAndFullIdentifierStartingWithLimited(actionUnitId, query == null ? "" : query, limit)
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
     * @param action  The action to find ActionUnits for
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
     * @param action  The institution to find ActionUnits for
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


    public List<RecordingUnitDTO> findAllByParentRecordingUnit(Long parentRecordingUnitId) {
        List<RecordingUnit> results = recordingUnitRepository.findChildrensOf(parentRecordingUnitId);

        return results
                .stream()
                .map(recordingUnitMapper::convert)
                .toList();
    }

    public Page<RecordingUnitDTO> searchRecordingUnit(InstitutionDTO institution, FilterDTO filters, Pageable pageable) {
        return searchRecordingUnit(institution, filters, pageable, true);
    }

    public Page<RecordingUnitDTO> searchRecordingUnit(InstitutionDTO institution, FilterDTO filters, Pageable pageable,
                                                       boolean includeFullRelations) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institution, filters);
        return pageAndEnrich(specs, pageable, includeFullRelations);
    }

    /**
     * Pages recording units for a given {@link Specification} and enriches each row with either the
     * full {@code parents}/{@code children}/{@code phases} collections (JSF list, which renders them as
     * clickable chips) or plain counts (REST API, which only ever reads
     * {@code parentsCount}/{@code childrenCount}/{@code specimenCount}) — batched for the whole page
     * (a handful of queries total) rather than once per row.
     */
    private Page<RecordingUnitDTO> pageAndEnrich(Specification<RecordingUnit> specs, Pageable pageable,
                                                 boolean includeFullRelations) {
        specs = recordingUnitSortFilterService.applySyntheticSort(specs, pageable.getSort());
        Page<RecordingUnitDTO> page = recordingUnitRepository.findAll(specs, recordingUnitSortFilterService.stripSyntheticSort(pageable))
                .map(recordingUnitMapper::toLightDto);

        List<Long> ids = page.getContent().stream()
                .map(RecordingUnitDTO::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return page;
        }

        hydrateCounts(page.getContent(), ids);
        if (includeFullRelations) {
            hydrateFullRelations(page.getContent(), ids);
        }

        return page;
    }


    private void hydrateFullRelations(List<RecordingUnitDTO> rows, List<Long> ids) {
        Map<Long, Set<RecordingUnitSummaryDTO>> parentsByOwner = groupRecordingUnitsByOwner(
                recordingUnitRepository.findParentEdges(ids));
        Map<Long, Set<RecordingUnitSummaryDTO>> childrenByOwner = groupRecordingUnitsByOwner(
                recordingUnitRepository.findChildEdges(ids));
        Map<Long, Set<PhaseDTO>> phasesByOwner = groupPhasesByOwner(phaseRepository.findPhaseEdges(ids));

        for (RecordingUnitDTO dto : rows) {
            dto.setParents(parentsByOwner.getOrDefault(dto.getId(), Set.of()));
            dto.setChildren(childrenByOwner.getOrDefault(dto.getId(), Set.of()));
            dto.setPhases(phasesByOwner.getOrDefault(dto.getId(), Set.of()));
        }
    }

    private Map<Long, Set<RecordingUnitSummaryDTO>> groupRecordingUnitsByOwner(List<Object[]> edges) {
        List<Long> relatedIds = edges.stream()
                .map(row -> ((Number) row[1]).longValue())
                .distinct()
                .toList();
        if (relatedIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecordingUnitSummaryDTO> relatedById = new HashMap<>();
        for (RecordingUnit related : recordingUnitRepository.findAllById(relatedIds)) {
            relatedById.put(related.getId(), recordingUnitSummaryMapper.convert(related));
        }

        Map<Long, Set<RecordingUnitSummaryDTO>> byOwner = new HashMap<>();
        for (Object[] edge : edges) {
            Long ownerId = ((Number) edge[0]).longValue();
            Long relatedId = ((Number) edge[1]).longValue();
            RecordingUnitSummaryDTO related = relatedById.get(relatedId);
            if (related != null) {
                byOwner.computeIfAbsent(ownerId, k -> new HashSet<>()).add(related);
            }
        }
        return byOwner;
    }

    private Map<Long, Set<PhaseDTO>> groupPhasesByOwner(List<Object[]> edges) {
        List<Long> phaseIds = edges.stream()
                .map(row -> ((Number) row[1]).longValue())
                .distinct()
                .toList();
        if (phaseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PhaseDTO> phaseById = new HashMap<>();
        for (var phase : phaseRepository.findAllById(phaseIds)) {
            phaseById.put(phase.getId(), phaseMapper.convert(phase));
        }

        Map<Long, Set<PhaseDTO>> byOwner = new HashMap<>();
        for (Object[] edge : edges) {
            Long ownerId = ((Number) edge[0]).longValue();
            Long phaseId = ((Number) edge[1]).longValue();
            PhaseDTO phase = phaseById.get(phaseId);
            if (phase != null) {
                byOwner.computeIfAbsent(ownerId, k -> new HashSet<>()).add(phase);
            }
        }
        return byOwner;
    }

    private void hydrateCounts(List<RecordingUnitDTO> rows, List<Long> ids) {
        Map<Long, Integer> parentsCount = toCountMap(recordingUnitRepository.countParentsByIds(ids));
        Map<Long, Integer> childrenCount = toCountMap(recordingUnitRepository.countChildrenByIds(ids));
        Map<Long, Long> specimenCount = recordingUnitRepository.countSpecimensByIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));
        Map<Long, Long> relationshipCount = recordingUnitRepository.countRelationshipsByIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));

        for (RecordingUnitDTO dto : rows) {
            dto.setParentsCount(parentsCount.getOrDefault(dto.getId(), 0));
            dto.setChildrenCount(childrenCount.getOrDefault(dto.getId(), 0));
            dto.setSpecimenCount(specimenCount.getOrDefault(dto.getId(), 0L));
            dto.setRelationshipCount(relationshipCount.getOrDefault(dto.getId(), 0L));
        }
    }

    private static Map<Long, Integer> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).intValue()));
    }

    public Page<RecordingUnitDTO> searchRecordingUnitInActionUnit(InstitutionDTO institutionDTO, @NonNull ActionUnitDTO actionUnitDTO, FilterDTO filters, Pageable pageable) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institutionDTO, filters);
        specs = specs.and(RecordingUnitSpec.recordingUnitInActionUnit(actionUnitDTO.getId()));
        Page<RecordingUnit> results = recordingUnitRepository.findAll(specs, pageable);
        return results.map(recordingUnitMapper::convert);
    }

    public Page<RecordingUnitDTO> searchRecordingUnitInRecordingUnit(InstitutionDTO institutionDTO,
                                                                     @NonNull RecordingUnitDTO recordingUnitDTO,
                                                                     FilterDTO filters, Pageable pageable) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institutionDTO, filters);
        specs = specs.and(RecordingUnitSpec.recordingUnitInRecordingUnit(recordingUnitDTO.getId()));
        Page<RecordingUnit> results = recordingUnitRepository.findAll(specs, pageable);
        return results.map(recordingUnitMapper::convert);
    }

    public Page<RecordingUnitDTO> searchRecordingUnitInSpatialUnit(InstitutionDTO institutionDTO,
                                                                   @NonNull SpatialUnitDTO spatialUnitDTO,
                                                                   FilterDTO filters, Pageable pageable) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institutionDTO, filters);
        specs = specs.and(RecordingUnitSpec.recordingUnitInSpatialUnit(spatialUnitDTO.getId()));
        Page<RecordingUnit> results = recordingUnitRepository.findAll(specs, pageable);
        return results.map(recordingUnitMapper::convert);
    }

    public int countSearchResultsInActionUnit(InstitutionDTO institutionDTO, @NonNull ActionUnitDTO actionUnitDTO, FilterDTO filters) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institutionDTO, filters);
        specs = specs.and(RecordingUnitSpec.recordingUnitInActionUnit(actionUnitDTO.getId()));
        return Math.toIntExact(recordingUnitRepository.count(specs));
    }

    public int countSearchResultsInRecordingUnit(InstitutionDTO institutionDTO,
                                                 @NonNull RecordingUnitDTO recordingUnitDTO, FilterDTO filters) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institutionDTO, filters);
        specs = specs.and(RecordingUnitSpec.recordingUnitInRecordingUnit(recordingUnitDTO.getId()));
        return Math.toIntExact(recordingUnitRepository.count(specs));
    }

    public int countSearchResultsInSpatialUnit(InstitutionDTO institutionDTO,
                                               @NonNull SpatialUnitDTO spatialUnitDTO, FilterDTO filters) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institutionDTO, filters);
        specs = specs.and(RecordingUnitSpec.recordingUnitInSpatialUnit(spatialUnitDTO.getId()));
        return Math.toIntExact(recordingUnitRepository.count(specs));
    }

    public int countSearchResults(InstitutionDTO institution, FilterDTO filters) {
        Specification<RecordingUnit> specs = recordingUnitSortFilterService.prepareSpecs(institution, filters);
        return Math.toIntExact(recordingUnitRepository.count(specs));
    }


    @Transactional(readOnly = true)
    public void initializeHierarchy(RecordingUnitDTO unit) {
        Set<RecordingUnitSummaryDTO> parents = recordingUnitRepository
                .findParentsOf(unit.getId())
                .stream()
                .map(recordingUnitSummaryMapper::convert)
                .collect(Collectors.toSet());

        Set<RecordingUnitSummaryDTO> childrens = recordingUnitRepository
                .findChildrensOf(unit.getId())
                .stream()
                .map(recordingUnitSummaryMapper::convert)
                .collect(Collectors.toSet());
        unit.setParents(parents);
        unit.setChildren(childrens);
    }
}
