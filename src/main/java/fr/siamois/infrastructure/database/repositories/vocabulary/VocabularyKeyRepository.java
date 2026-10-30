package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VocabularyKeyRepository extends CrudRepository<VocabularyApiKey, VocabularyApiKey.VocabularyApiKeyId> {

    @Query("SELECT vak FROM VocabularyApiKey vak WHERE vak.id.personId = :personId")
    List<VocabularyApiKey> findAllByPersonId(Long personId);

}
