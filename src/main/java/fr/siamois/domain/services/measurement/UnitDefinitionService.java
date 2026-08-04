package fr.siamois.domain.services.measurement;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnitDefinitionService {

    private final UnitDefinitionRepository unitDefinitionRepository;
    private final UnitDefinitionMapper unitDefinitionMapper;

    /**
     * The stored unit the given one stands for: by id when it has one, by symbol and dimension
     * otherwise — a unit built in code carries no id and no concept, only what it measures.
     *
     * @param unit the unit to resolve, null when the measurement has none
     * @return the managed unit, or null if none was asked for
     * @throws IllegalStateException if the instance knows no such unit; add it to the catalogue of
     *                               {@code UnitDefinitionInitializer} rather than letting a
     *                               measurement create one of its own
     */
    @Transactional(readOnly = true)
    public @Nullable UnitDefinition resolve(@Nullable UnitDefinition unit) {
        if (unit == null) return null;
        if (unit.getId() != null) return resolveById(unit.getId());

        return unitDefinitionRepository
                .findFirstBySymbolAndDimensionOrderByIdAsc(unit.getSymbol(), unit.getDimension())
                .orElseThrow(() -> new IllegalStateException(
                        "No unit definition seeded for symbol '" + unit.getSymbol()
                                + "' and dimension " + unit.getDimension()));
    }

    /**
     * The stored unit of that id.
     *
     * @throws IllegalStateException if the row is gone
     */
    @Transactional(readOnly = true)
    public @Nullable UnitDefinition resolveById(@Nullable Long id) {
        if (id == null) return null;
        return unitDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("UnitDefinition not found: " + id));
    }

    /**
     * The units a measurement field can be created with, in alphabetical order. Units belong to the
     * instance, not to a project, so every form is offered the same list.
     */
    @Transactional(readOnly = true)
    public List<UnitDefinitionDTO> findOptions() {
        return unitDefinitionRepository.findAllByOrderByLabelAsc().stream()
                .map(unitDefinitionMapper::convert)
                .toList();
    }

}
