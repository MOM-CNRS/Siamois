package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConceptRelationRepository extends CrudRepository<ConceptHierarchy, Long> {

}
