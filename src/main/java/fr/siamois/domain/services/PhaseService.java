package fr.siamois.domain.services;

import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.GeneratedIdentifier;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.PhaseSpec;
import fr.siamois.mapper.PhaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PhaseService {

    private final PhaseRepository phaseRepository;
    private final PhaseMapper phaseMapper;
    private final EntityIdentifierGenerator identifierGenerator;

    private Specification<Phase> userFilterSpecs(FilterDTO filters) {
        Specification<Phase> specs = Specification.where(null);

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo nameFilter = filters.filterOf(ActionUnitSpec.NAME_FILTER);

        if (nameFilter != null && nameFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(PhaseSpec.identifierContaining(nameFilter.valueAsString()));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(PhaseSpec.identifierContaining(globalFilter.valueAsString()));
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

        generateIdentifierIfRequired(managed);

        return phaseMapper.convert(phaseRepository.save(managed));
    }

    private void generateIdentifierIfRequired(Phase phase) {
        if (phase.getId() != null) return;
        ActionUnit actionUnit = phase.getActionUnit();
        Map<String, Object> values = new HashMap<>();
        values.put("NUM_PARENT", null);
        values.put("ID_PARENT", null);
        values.put("PHASE_ORDER", phase.getOrderNumber());
        values.put("ID_UA", actionUnit.getFullIdentifier());
        Map<String, Object> partitions = new HashMap<>();
        partitions.put("PARENT_PHASE", null);
        partitions.put("PHASE_ORDER", phase.getOrderNumber());
        Long typeId = phase.getType() == null ? null : phase.getType().getId();
        GeneratedIdentifier generated = identifierGenerator.generate(
                ConfigurableTable.PHASE, actionUnit, typeId, values, partitions,
                candidate -> phaseRepository.existsByActionUnitIdAndIdentifier(actionUnit.getId(), candidate));
        phase.setGeneratedNumber(generated.number());
        phase.setIdentifier(generated.value());
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
}
