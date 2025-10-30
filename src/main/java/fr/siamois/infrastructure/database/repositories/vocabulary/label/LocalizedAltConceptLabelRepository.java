package fr.siamois.infrastructure.database.repositories.vocabulary.label;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.LocalizedAltConceptLabel;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalizedAltConceptLabelRepository extends CrudRepository<LocalizedAltConceptLabel, ConceptLabel.Id> {
}
