package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VocabularyRepositoryTest {

    private static final String BASE_URI = "https://thesaurus.example.org";
    private static final String EXTERNAL_ID = "th1";

    private final VocabularyRepository repository = mock(VocabularyRepository.class);

    private static Vocabulary vocabulary(long id) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setId(id);
        return vocabulary;
    }

    @Test
    void findVocabularyByBaseUriAndVocabExternalId_shouldAnswerTheOldestOne_whenDuplicatesExist() {
        when(repository.findAllByBaseUriAndVocabExternalId(BASE_URI, EXTERNAL_ID)).thenReturn(List.of(vocabulary(1L), vocabulary(2L)));
        doCallRealMethod().when(repository).findVocabularyByBaseUriAndVocabExternalId(BASE_URI, EXTERNAL_ID);

        Optional<Vocabulary> found = repository.findVocabularyByBaseUriAndVocabExternalId(BASE_URI, EXTERNAL_ID);

        assertThat(found).map(Vocabulary::getId).contains(1L);
    }

    @Test
    void findVocabularyByBaseUriAndVocabExternalId_shouldAnswerNothing_whenNoneMatches() {
        when(repository.findAllByBaseUriAndVocabExternalId(BASE_URI, EXTERNAL_ID)).thenReturn(List.of());
        doCallRealMethod().when(repository).findVocabularyByBaseUriAndVocabExternalId(BASE_URI, EXTERNAL_ID);

        assertThat(repository.findVocabularyByBaseUriAndVocabExternalId(BASE_URI, EXTERNAL_ID)).isEmpty();
    }
}
