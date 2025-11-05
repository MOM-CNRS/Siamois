package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.ConceptRelatedLink;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConceptRelatedLinkRepository extends CrudRepository<ConceptRelatedLink, ConceptRelatedLink.Id> {
}
