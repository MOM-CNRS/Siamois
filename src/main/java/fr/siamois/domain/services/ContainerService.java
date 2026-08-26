package fr.siamois.domain.services;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.ContainerSpec;
import fr.siamois.mapper.ContainerMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

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
    private final UnitDefinitionService unitDefinitionService;
    private final EntityIdentifierGenerator identifierGenerator;
    private final ProfilePermissionService profilePermissionService;

    private Specification<Container> userFilterSpecs(FilterDTO filters) {
        Specification<Container> specs = Specification.where(null);

        FilterDTO.FilterInfo globalFilter = filters.filterOf(ActionUnitSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo nameFilter = filters.filterOf(ContainerSpec.IDENTIFIER_FILTER);

        if (nameFilter != null && nameFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(ContainerSpec.nameContaining(nameFilter.valueAsString()));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(ContainerSpec.nameContaining(globalFilter.valueAsString()));
        }

        if (filters.containsColumn(ContainerSpec.ACTION_UNIT_FILTER)) {
            specs = specs.and(ContainerSpec.isInActionUnit(filters.valueAsIdListOf(ContainerSpec.ACTION_UNIT_FILTER)));
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

        if (filters.containsColumn(ContainerSpec.IDENTIFIER_FILTER)) {
            String nameContains = filters.valueOfAsString(ContainerSpec.IDENTIFIER_FILTER);
            log.trace("{} éléments trouvées pour {} (Page {}/{})", res.getTotalElements(), nameContains,res.getNumber() + 1, res.getTotalPages());
        }

        return res.map(containerMapper::convert);

    }

    public int countSearchResults(InstitutionDTO institutionDTO, FilterDTO filters) {
        Specification<Container> specs = prepareSpecs(institutionDTO, filters);
        return Math.toIntExact(containerRepository.count(specs));
    }

    public int countByActionContext(ActionUnitDTO actionUnit) {
        return containerRepository.countByActionUnitId(actionUnit.getId());
    }

    public boolean identifierAlreadyExistInAction(ContainerDTO container) {
        if (container.getActionUnit() == null) {
            return false;
        }
        return containerRepository.findByActionUnitIdAndIdentifier(
                        container.getActionUnit().getId(), container.getIdentifier()).stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), container.getId()));
    }

    public ContainerDTO save(ContainerDTO dto) {
        Container entity = containerMapper.invertConvert(dto);

        UserInfo info = ExecutionContextHolder.get();
        Long actionUnitId = entity != null && entity.getActionUnit() != null ? entity.getActionUnit().getId() : null;
        if (info == null || !profilePermissionService.hasProjectPermission(info, actionUnitId, PermissionConstants.PROJECT_EDIT_CONTAINERS)) {
            throw new ForbiddenOperationException("You are not allowed to edit this container");
        }

        if (dto.getParentId() != null) {
            entity.setParent(containerRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Container parent not found: " + dto.getParentId())));
        }
        resolveUnitsOf(entity);
        identifierGenerator.generateIdentifierIfRequired(entity, containerIdentifierSpec());
        entity = containerRepository.save(entity);
        return containerMapper.convert(entity);
    }

    private IdentifierGenerationSpec<Container> containerIdentifierSpec() {
        return IdentifierGenerationSpec.<Container>builder()
                .table(ConfigurableTable.CONTENANT)
                .entityName("container")
                .generationRequired(container -> container.getId() == null)
                .actionUnit(Container::getActionUnit)
                .typeId(container -> container.getType() == null ? null : container.getType().getId())
                .displayValue("NUM_PARENT", container -> container.getParent() == null
                        ? null : container.getParent().getGeneratedNumber())
                .displayValue("ID_PARENT", container -> container.getParent() == null
                        ? null : container.getParent().getIdentifier())
                .displayValue("ID_UA", container -> container.getActionUnit().getFullIdentifier())
                .partitionValue("PARENT_CONTAINER", container -> container.getParent() == null
                        ? null : container.getParent().getId())
                .identifierAlreadyUsed((container, candidate) ->
                        containerRepository.existsByActionUnitIdAndIdentifier(
                                container.getActionUnit().getId(), candidate))
                .numberSetter(Container::setGeneratedNumber)
                .identifierSetter(Container::setIdentifier)
                .build();
    }

    private void resolveUnitsOf(Container container) {
        if (container == null) return;
        Stream.of(container.getLength(), container.getWidth(), container.getHeight(), container.getWeight())
                .filter(Objects::nonNull)
                .forEach(measurement -> measurement.setUnit(unitDefinitionService.resolve(measurement.getUnit())));
    }

    public ContainerDTO findById(Long id) {
        return containerRepository.findById(id)
                .map(containerMapper::convert)
                .orElse(null);
    }

    /**
     * Find the next container created in the same action unit after the given one.
     * If there is no next, returns the oldest one (wraps around).
     *
     * @param actionUnit The action unit to find containers for
     * @param current    The current container to find the next one from
     * @return The next ContainerDTO, or the oldest one if there is no next
     */
    public ContainerDTO findNextByActionUnit(ActionUnitSummaryDTO actionUnit, ContainerDTO current) {
        return containerRepository
                .findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(
                        actionUnit.getId(), current.getCreationTime())
                .map(containerMapper::convert)
                .orElseGet(() -> containerRepository
                        .findFirstByActionUnitIdOrderByCreationTimeAsc(actionUnit.getId())
                        .map(containerMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + actionUnit.getId()))
                );
    }

    /**
     * Find the previous container created in the same action unit before the given one.
     * If there is no previous, returns the most recent one (wraps around).
     *
     * @param actionUnit The action unit to find containers for
     * @param current    The current container to find the previous one from
     * @return The previous ContainerDTO, or the most recent one if there is no previous
     */
    public ContainerDTO findPreviousByActionUnit(ActionUnitSummaryDTO actionUnit, ContainerDTO current) {
        return containerRepository
                .findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(
                        actionUnit.getId(), current.getCreationTime())
                .map(containerMapper::convert)
                .orElseGet(() -> containerRepository
                        .findFirstByActionUnitIdOrderByCreationTimeDesc(actionUnit.getId())
                        .map(containerMapper::convert)
                        .orElseThrow(() -> new ActionUnitNotFoundException("No ActionUnit found for institution " + actionUnit.getId()))
                );
    }

}
