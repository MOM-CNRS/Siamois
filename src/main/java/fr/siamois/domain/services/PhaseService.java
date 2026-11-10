package fr.siamois.domain.services;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.PhaseSpec;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.TextSearchSpec;
import fr.siamois.infrastructure.database.repositories.specs.ExactMatchFirst;
import fr.siamois.mapper.PhaseMapper;
import fr.siamois.mapper.RecordingUnitSummaryMapper;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PhaseService {

    private final EntityLinkGuard entityLinkGuard;

    private final PhaseRepository phaseRepository;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final PhaseMapper phaseMapper;
    private final EntityIdentifierGenerator identifierGenerator;
    private final ProfilePermissionService profilePermissionService;
    private final RecordingUnitRepository recordingUnitRepository;
    private final RecordingUnitSummaryMapper recordingUnitSummaryMapper;

    private Specification<Phase> userFilterSpecs(FilterDTO filters) {
        // The list's per-field sort/filters (FieldQuery), then the named ones.
        Specification<Phase> specs = filters.getFieldQuery().specificationFor();

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo nameFilter = filters.filterOf(PhaseSpec.IDENTIFIER_FILTER);

        if (nameFilter != null && nameFilter.getType().isTextSearch()) {
            specs = specs.and(TextSearchSpec.matching(PhaseSpec.IDENTIFIER_FILTER, nameFilter));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(PhaseSpec.identifierContaining(globalFilter.valueAsString()));
        }

        if (filters.containsColumn(PhaseSpec.ACTION_UNIT_FILTER)) {
            specs = specs.and(PhaseSpec.isInActionUnit(filters.valueAsIdListOf(PhaseSpec.ACTION_UNIT_FILTER)));
        }

        return specs;
    }

    private Specification<Phase> prepareSpecs(InstitutionDTO institutionDTO, FilterDTO filters) {
        Specification<Phase> base = PhaseSpec.belongsToInstitution(institutionDTO.getId());
        return base.and(userFilterSpecs(filters));
    }

    public Page<PhaseDTO> searchPhases(InstitutionDTO institutionDTO, FilterDTO filters, Pageable pageable) {
        return ExactMatchFirst.findAll(phaseRepository, prepareSpecs(institutionDTO, filters), pageable,
                        PhaseSpec.IDENTIFIER_FILTER, filters.searchTextOf(PhaseSpec.IDENTIFIER_FILTER))
                .map(phaseMapper::convert);
    }

    public int countSearchResults(InstitutionDTO institutionDTO, FilterDTO filters) {
        return Math.toIntExact(phaseRepository.count(prepareSpecs(institutionDTO, filters)));
    }

    public int countByActionContext(ActionUnitDTO actionUnit) {
        return phaseRepository.countByActionUnitId(actionUnit.getId());
    }

    public boolean identifierAlreadyExistInAction(PhaseDTO phase) {
        if (phase.getActionUnit() == null) {
            return false;
        }
        return phaseRepository.findByIdentifierAndActionUnitId(phase.getIdentifier(), phase.getActionUnit().getId())
                .filter(existing -> !Objects.equals(existing.getId(), phase.getId()))
                .isPresent();
    }

    /**
     * Saves the phase, then its additional (non-system) field answers — the
     * counterpart of {@code RecordingUnitService.save(RecordingUnitDTO, Map)}.
     *
     * @param dto                    the entity to save
     * @param additionalFieldAnswers answers to the type's additional fields, keyed by field
     * @return the saved entity
     */
    @Transactional
    public PhaseDTO save(PhaseDTO dto, Map<CustomField, CustomFieldAnswerViewModel> additionalFieldAnswers) {
        PhaseDTO saved = save(dto);
        customFieldAnswerService.saveAdditionalFieldAnswers(saved, additionalFieldAnswers);
        return saved;
    }

    public PhaseDTO save(PhaseDTO dto) {
        Phase entity = phaseMapper.invertConvert(dto);
        Phase managed = phaseRepository.findById(entity.getId() != null ? entity.getId() : -1L)
                .orElse(entity);

        if (managed != entity) {
            managed.setIdentifier(entity.getIdentifier());
            managed.setActionUnit(entity.getActionUnit());
            managed.setType(entity.getType());
            managed.setTitle(entity.getTitle());
            managed.setDescription(entity.getDescription());
            managed.setOrderNumber(entity.getOrderNumber());
            managed.setLowerBound(entity.getLowerBound());
            managed.setUpperBound(entity.getUpperBound());
            synchronizeCollection(managed.getPeriods(), entity.getPeriods());
            synchronizeCollection(managed.getKeywords(), entity.getKeywords());
        }

        if (managed.getActionUnit() == null) {
            throw new IllegalArgumentException("An action unit is required to save a phase");
        }

        UserInfo info = ExecutionContextHolder.get();
        if (info == null || !profilePermissionService.hasProjectPermission(
                info, managed.getActionUnit().getId(), PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES)) {
            throw new ForbiddenOperationException("You are not allowed to edit this phase");
        }

        identifierGenerator.generateIdentifierIfRequired(managed, phaseIdentifierSpec());

        Phase saved = phaseRepository.save(managed);
        if (dto.getRecordingUnits() != null) {
            synchronizeRecordingUnits(saved, dto.getRecordingUnits());
        }
        return phaseMapper.convert(saved);
    }

    /**
     * The recording units attached to a phase (recording_unit_phase), in no particular order.
     *
     * @param phaseId the phase's id
     * @return the summaries of its recording units, empty when it has none
     */
    @Transactional(readOnly = true)
    public Set<RecordingUnitSummaryDTO> findRecordingUnits(long phaseId) {
        return recordingUnitRepository.findAll(RecordingUnitSpec.inPhase(phaseId)).stream()
                .map(recordingUnitSummaryMapper::convert)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Makes the phase's recording units exactly {@code wanted}. The relation is owned by
     * {@link RecordingUnit#getPhases()}, so it is written on each recording unit added or removed.
     * A recording unit of another project is refused.
     */
    private void synchronizeRecordingUnits(Phase phase, Set<RecordingUnitSummaryDTO> wanted) {
        Long phaseId = phase.getId();
        Long projectId = phase.getActionUnit().getId();
        Set<Long> wantedIds = wanted.stream()
                .map(RecordingUnitSummaryDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<RecordingUnit> changed = new ArrayList<>();
        Set<Long> alreadyLinkedIds = new HashSet<>();
        for (RecordingUnit linked : recordingUnitRepository.findAll(RecordingUnitSpec.inPhase(phaseId))) {
            if (wantedIds.contains(linked.getId())) {
                alreadyLinkedIds.add(linked.getId());
            } else if (linked.getPhases().removeIf(p -> Objects.equals(p.getId(), phaseId))) {
                changed.add(linked);
            }
        }

        Set<Long> toLinkIds = new HashSet<>(wantedIds);
        toLinkIds.removeAll(alreadyLinkedIds);
        if (!toLinkIds.isEmpty()) {
            for (RecordingUnit added : recordingUnitRepository.findAllById(toLinkIds)) {
                if (added.getActionUnit() == null || !Objects.equals(added.getActionUnit().getId(), projectId)) {
                    throw new IllegalArgumentException("Recording unit " + added.getId() + " is not in the phase's project");
                }
                added.getPhases().add(phase);
                changed.add(added);
            }
        }

        if (!changed.isEmpty()) {
            recordingUnitRepository.saveAll(changed);
        }
    }

    /**
     * The identifier a new phase like {@code dto} would be given, without consuming the counter —
     * the creation form's suggestion. Nothing is written.
     */
    @Transactional(readOnly = true)
    public String suggestIdentifier(PhaseDTO dto) {
        return identifierGenerator.suggestIdentifier(phaseMapper.invertConvert(dto), phaseIdentifierSpec()).value();
    }

    private IdentifierGenerationSpec<Phase> phaseIdentifierSpec() {
        return IdentifierGenerationSpec.<Phase>builder()
                .table(ConfigurableTable.PHASE)
                .entityName("phase")
                .generationRequired(IdentifierGenerationSpec.whenNewWithoutIdentifier(Phase::getId, Phase::getIdentifier))
                .actionUnit(Phase::getActionUnit)
                .typeId(phase -> phase.getType() == null ? null : phase.getType().getId())
                .displayValue("NUM_PARENT", phase -> null)
                .displayValue("ID_PARENT", phase -> null)
                .displayValue("PHASE_ORDER", Phase::getOrderNumber)
                .displayValue("ID_UA", phase -> phase.getActionUnit().getFullIdentifier())
                .partitionValue("PARENT_PHASE", phase -> null)
                .partitionValue("PHASE_ORDER", Phase::getOrderNumber)
                .identifierAlreadyUsed((phase, candidate) ->
                        phaseRepository.existsByActionUnitIdAndIdentifier(
                                phase.getActionUnit().getId(), candidate))
                .numberSetter(Phase::setGeneratedNumber)
                .identifierSetter(Phase::setIdentifier)
                .build();
    }

    public PhaseDTO findById(Long id) {
        return phaseRepository.findById(id)
                .map(phaseMapper::convert)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PhaseDTO> findAllByActionUnitId(long actionUnitId) {
        return phaseRepository.findAll(PhaseSpec.belongsToActionUnit(actionUnitId)).stream()
                .map(phaseMapper::convert)
                .toList();
    }

    private <T> void synchronizeCollection(Collection<T> managed, Collection<T> incoming) {
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

    /**
     * Find the next phase created in the same action unit after the given one.
     * If there is no next, returns the oldest one (wraps around).
     *
     * @param actionUnit The action unit to find phases for
     * @param current    The current phase to find the next one from
     * @return The next PhaseDTO, or the oldest one if there is no next
     */
    public PhaseDTO findNextByActionUnit(ActionUnitSummaryDTO actionUnit, PhaseDTO current) {
        return phaseRepository
                .findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(
                        actionUnit.getId(), current.getCreationTime())
                .map(phaseMapper::convert)
                .orElseGet(() -> phaseRepository
                        .findFirstByActionUnitIdOrderByCreationTimeAsc(actionUnit.getId())
                        .map(phaseMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + actionUnit.getId()))
                );
    }

    /**
     * Find the previous phase created in the same action unit before the given one.
     * If there is no previous, returns the most recent one (wraps around).
     *
     * @param actionUnit The action unit to find phases for
     * @param current    The current phase to find the previous one from
     * @return The previous PhaseDTO, or the most recent one if there is no previous
     */
    public PhaseDTO findPreviousByActionUnit(ActionUnitSummaryDTO actionUnit, PhaseDTO current) {
        return phaseRepository
                .findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(
                        actionUnit.getId(), current.getCreationTime())
                .map(phaseMapper::convert)
                .orElseGet(() -> phaseRepository
                        .findFirstByActionUnitIdOrderByCreationTimeDesc(actionUnit.getId())
                        .map(phaseMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + actionUnit.getId()))
                );
    }

    /**
     * Supprime la phase qui n'a aucune relation avec une autre entité.
     *
     * @throws IllegalStateException si la phase a encore des relations
     */
    public void deleteById(long id) {
        Phase entity = phaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Phase introuvable: " + id));

        UserInfo info = ExecutionContextHolder.get();
        Long actionUnitId = entity.getActionUnit() != null ? entity.getActionUnit().getId() : null;
        if (info == null || !profilePermissionService.hasDeleteEntitiesPermission(info, actionUnitId)) {
            throw new ForbiddenOperationException("You are not allowed to delete this phase");
        }

        EntityLinkGuard.Kind kind = EntityLinkGuard.Kind.PHASE;
        entityLinkGuard.requireNoLinks(kind, id, "la phase");

        PhaseDTO idOnly = new PhaseDTO();
        idOnly.setId(id);
        customFieldAnswerService.deleteAdditionalFieldAnswers(idOnly);
        phaseRepository.delete(entity);
    }
}
