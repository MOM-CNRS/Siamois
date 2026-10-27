package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VocabularyKeyRepository extends CrudRepository<VocabularyApiKey, VocabularyApiKey.VocabularyApiKeyId> {

    @Query("SELECT vak FROM VocabularyApiKey vak WHERE vak.vocabulary = :vocabulary AND vak.person = :person")
    Optional<VocabularyApiKey> findByVocabularyAndPerson(Vocabulary vocabulary, Person person);

    @Query("SELECT vak FROM VocabularyApiKey vak WHERE vak.id.personId = :personId")
    List<VocabularyApiKey> findAllByPersonId(Long personId);

}
