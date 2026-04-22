package fr.siamois.infrastructure.database.repositories.measurement;


import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UnitDefinitionRepository extends CrudRepository<UnitDefinition, Long> {

    Optional<UnitDefinition> findByConcept(Concept concept);

}
