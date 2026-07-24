package fr.siamois.domain.services;

import fr.siamois.domain.models.phase.Phase;
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
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PhaseService {

    private final PhaseRepository phaseRepository;
    private final PhaseMapper phaseMapper;

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
            managed.setType(entity.getType());
            managed.setTitle(entity.getTitle());
            managed.setDescription(entity.getDescription());
            managed.setOrderNumber(entity.getOrderNumber());
            managed.setLowerBound(entity.getLowerBound());
            managed.setUpperBound(entity.getUpperBound());
            synchronizeCollection(managed.getPeriods(), entity.getPeriods());
            synchronizeCollection(managed.getKeywords(), entity.getKeywords());
        }

        return phaseMapper.convert(phaseRepository.save(managed));
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
