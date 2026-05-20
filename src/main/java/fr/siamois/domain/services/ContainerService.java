package fr.siamois.domain.services;

import fr.siamois.domain.models.container.Container;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.ContainerSpec;
import fr.siamois.mapper.ContainerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing Containers.
 * This service provides methods to find, save, and manage Containers in the system.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final ContainerMapper containerMapper;

    private Specification<Container> userFilterSpecs(FilterDTO filters) {
        Specification<Container> specs = Specification.where(null);

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo nameFilter = filters.filterOf(ActionUnitSpec.NAME_FILTER);

        if (nameFilter != null && nameFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(ContainerSpec.nameContaining(nameFilter.valueAsString()));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(ContainerSpec.nameContaining(globalFilter.valueAsString()));
        }

        return specs;
    }

    private Collection<Long> resolveAncestorClosure(InstitutionDTO institutionDTO, FilterDTO filters) {
        if (filters.getAncestorClosure() != null) {
            return filters.getAncestorClosure();
        }
        Specification<Container> matchSpecs = ContainerSpec.belongsToInstitution(institutionDTO.getId())
                .and(userFilterSpecs(filters));
        List<Long> matchIds = containerRepository.findAll(matchSpecs).stream()
                .map(Container::getId)
                .toList();
        Set<Long> closure = matchIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(containerRepository.findAncestorClosure(matchIds.toArray(Long[]::new)));
        filters.setAncestorClosure(closure);
        filters.setMatchIds(new HashSet<>(matchIds));
        return closure;
    }

    private Specification<Container> prepareSpecs(@org.jspecify.annotations.NonNull InstitutionDTO institutionDTO, @NonNull FilterDTO filters) {
        Specification<Container> base = ContainerSpec.belongsToInstitution(institutionDTO.getId());

        if (filters.isRootOnly()) {
            if (filters.hasUserFilters()) {
                Collection<Long> closure = resolveAncestorClosure(institutionDTO, filters);
                if (closure.isEmpty()) {
                    return base.and((root, q, cb) -> cb.disjunction());
                }
                return base.and(ContainerSpec.unitIsRoot()).and(ContainerSpec.idIn(closure));
            }
            return base.and(ContainerSpec.unitIsRoot());
        }

        return base.and(userFilterSpecs(filters));
    }

    public Page<ContainerDTO> searchContainers(InstitutionDTO institutionDTO, FilterDTO filters, Pageable pageable) {

        Specification<Container> specs = prepareSpecs(institutionDTO, filters);

        Page<Container> res = containerRepository.findAll(specs, pageable);

        if (filters.containsColumn("name")) {
            String nameContains = filters.valueOfAsString("name");
            log.trace("{} éléments trouvées pour {} (Page {}/{})", res.getTotalElements(), nameContains,res.getNumber() + 1, res.getTotalPages());
        }

        return res.map(containerMapper::convert);

    }

    public int countSearchResults(InstitutionDTO institutionDTO, FilterDTO filters) {
        Specification<Container> specs = prepareSpecs(institutionDTO, filters);
        return Math.toIntExact(containerRepository.count(specs));
    }


}