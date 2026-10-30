package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.VocabularyInstance;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VocabularyInstanceRepository extends CrudRepository<VocabularyInstance, Long> {

    /**
     * The lookup ignores case, like the one of the vocabularies: the oldest match wins.
     */
    Optional<VocabularyInstance> findFirstByBaseUriIgnoreCaseOrderByIdAsc(String baseUri);

}
