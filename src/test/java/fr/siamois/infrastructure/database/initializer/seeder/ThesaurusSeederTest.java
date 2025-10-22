package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThesaurusSeederTest {

    @Mock
    VocabularyRepository vocabularyRepository;
    @Mock
    VocabularyService vocabularyService;

    @InjectMocks
    ThesaurusSeeder thesaurusSeeder;

    Vocabulary vocabulary;
    VocabularyType vocabularyType;

    @BeforeEach
    void setUp() {
        vocabularyType = new VocabularyType();
        vocabularyType.setLabel("Thesaurus");
        vocabulary = new Vocabulary();
        vocabulary.setExternalVocabularyId("th240");
        vocabulary.setType(vocabularyType);
    }



    @Test
    void seed_Inserted() throws DatabaseDataInitException, InvalidEndpointException {

        List<ThesaurusSeeder.ThesaurusSpec> toInsert = List.of(
                new ThesaurusSeeder.ThesaurusSpec("https://opentheso.mom.fr", "th240")
        );

        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(any(String.class), any(String.class))).thenReturn(Optional.empty());
        when(vocabularyService.findOrCreateVocabularyOfUri(any(String.class))).thenReturn(vocabulary);

        Map<String, Vocabulary> result = thesaurusSeeder.seed(toInsert);

        assertEquals(result.get("th240"), vocabulary);

    }

    @Test
    void seed_shouldThrowDatabaseDataInitException_whenInvalidEndpointExceptionOccurs() throws  InvalidEndpointException {
        List<ThesaurusSeeder.ThesaurusSpec> toInsert = List.of(
                new ThesaurusSeeder.ThesaurusSpec("https://opentheso.mom.fr", "th240")
        );

        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(any(String.class), any(String.class))).thenReturn(Optional.empty());
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenThrow(new InvalidEndpointException("bad endpoint"));

        DatabaseDataInitException ex = assertThrows(
                DatabaseDataInitException.class,
                () -> thesaurusSeeder.seed(toInsert)
        );


        assertThat(ex.getMessage()).contains("Error creating vocabulary from URI");
        assertThat(ex.getCause()).isInstanceOf(InvalidEndpointException.class);

    }

    @Test
    void seed_AlreadyExists() throws DatabaseDataInitException {

        List<ThesaurusSeeder.ThesaurusSpec> toInsert = List.of(
                new ThesaurusSeeder.ThesaurusSpec("https://opentheso.mom.fr", "th240")
        );

        when(vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(any(String.class), any(String.class))).thenReturn(Optional.ofNullable(vocabulary));

        Map<String, Vocabulary> result = thesaurusSeeder.seed(toInsert);

        assertEquals(result.get("th240"), vocabulary);
    }
}