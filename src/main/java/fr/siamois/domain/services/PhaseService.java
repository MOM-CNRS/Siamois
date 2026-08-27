package fr.siamois.domain.services;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.PhaseSpec;
import fr.siamois.mapper.PhaseMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PhaseService {

    private final PhaseRepository phaseRepository;
    private final PhaseMapper phaseMapper;
    private final EntityIdentifierGenerator identifierGenerator;
    private final ProfilePermissionService profilePermissionService;

    private Specification<Phase> userFilterSpecs(FilterDTO filters) {
        Specification<Phase> specs = Specification.where(null);

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo nameFilter = filters.filterOf(PhaseSpec.IDENTIFIER_FILTER);

        if (nameFilter != null && nameFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(PhaseSpec.identifierContaining(nameFilter.valueAsString()));
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
        return phaseRepository.findAll(prepareSpecs(institutionDTO, filters), pageable)
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
                info, managed.getActionUnit().getId(), PermissionConstants.PROJECT_EDIT_PHASES)) {
            throw new ForbiddenOperationException("You are not allowed to edit this phase");
        }

        identifierGenerator.generateIdentifierIfRequired(managed, phaseIdentifierSpec());

        return phaseMapper.convert(phaseRepository.save(managed));
    }

    private IdentifierGenerationSpec<Phase> phaseIdentifierSpec() {
        return IdentifierGenerationSpec.<Phase>builder()
                .table(ConfigurableTable.PHASE)
                .entityName("phase")
                .generationRequired(phase -> phase.getId() == null)
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
}
