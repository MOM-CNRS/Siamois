package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.services.vocabulary.ConceptHierarchy;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConceptRelationRepository extends CrudRepository<ConceptHierarchy, ConceptHierarchy.ConceptRelationId> {

}
