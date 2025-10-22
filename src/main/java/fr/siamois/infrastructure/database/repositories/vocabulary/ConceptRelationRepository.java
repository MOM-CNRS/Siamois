package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.services.vocabulary.ConceptRelation;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConceptRelationRepository extends CrudRepository<ConceptRelation, ConceptRelation.ConceptRelationId> {

}
