package fr.siamois.infrastructure.database.repositories.measurement;


import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface UnitDefinitionRepository extends CrudRepository<UnitDefinition, Long> {

    Optional<UnitDefinition> findByConcept(Concept concept);

    List<UnitDefinition> findAllByOrderByLabelAsc();

    /**
     * The stored counterpart of a unit built in code — the system fields declare theirs inline (see
     * {@code RecordingUnitForm}), with no id and no concept to look it up by.
     */
    Optional<UnitDefinition> findFirstBySymbolAndDimensionOrderByIdAsc(String symbol, UnitDefinition.Dimension dimension);

}
