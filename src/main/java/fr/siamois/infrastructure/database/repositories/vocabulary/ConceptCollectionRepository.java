package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConceptCollectionRepository extends CrudRepository<ConceptCollection, Long> {
    Optional<ConceptCollection> findByVocabularyAndExternalId(Vocabulary vocabulary, String externalId);
}
