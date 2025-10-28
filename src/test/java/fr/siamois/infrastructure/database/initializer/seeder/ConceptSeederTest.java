package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConceptSeederTest {

    @Mock
    ConceptRepository conceptRepository;

    @Mock
    LocalizedConceptDataRepository localizedConceptDataRepository;

    @InjectMocks
    ConceptSeeder seeder;

    @Test
    void seed_AlreadyExists_labelAlreadyExists() {

        Concept c = new Concept();
        Vocabulary v = new Vocabulary();

        List<ConceptSeeder.ConceptSpec> toInsert = List.of(
                new ConceptSeeder.ConceptSpec("th240", "1234556", "Label", "fr")
        );

        LocalizedConceptData lcd = new LocalizedConceptData();

        when(conceptRepository.findConceptByExternalIdIgnoreCase(anyString(), anyString())).thenReturn(Optional.of(c));
        doReturn(Optional.of(lcd)).when(localizedConceptDataRepository).findByConceptAndLangCode(any(), eq("fr"));

        seeder.seed(v, toInsert);

        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void seed_AlreadyExists_labelDoesNotExist() {

        Concept c = new Concept();
        Vocabulary v = new Vocabulary();
        c.setVocabulary(v);

        List<ConceptSeeder.ConceptSpec> toInsert = List.of(
                new ConceptSeeder.ConceptSpec("th240", "1234556", "Label", "fr")
        );

        when(conceptRepository.findConceptByExternalIdIgnoreCase(anyString(), anyString())).thenReturn(Optional.of(c));

        seeder.seed(v, toInsert);

        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void seed_DoesNotExist() {

        Vocabulary v = new Vocabulary();

        List<ConceptSeeder.ConceptSpec> toInsert = List.of(
                new ConceptSeeder.ConceptSpec("th240", "1234556", "Label", "fr")
        );

        when(localizedConceptDataRepository.findByConceptAndLangCode(anyLong(), anyString())).thenReturn(Optional.empty());
        when(conceptRepository.save(any(Concept.class))).thenAnswer(i -> {
            Concept c = i.getArgument(0);
            c.setId(-1L);
            return c;
        });

        seeder.seed(v, toInsert);

        verify(conceptRepository, times(1)).save(any(Concept.class));
    }

    @Test
    void findConceptOrThrow_shouldReturnConcept_whenFound() {
        // given
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("VOCAB1", "CONCEPT1");
        Concept expectedConcept = new Concept();
        when(conceptRepository.findConceptByExternalIdIgnoreCase("VOCAB1", "CONCEPT1"))
                .thenReturn(java.util.Optional.of(expectedConcept));

        // when
        Concept result = seeder.findConceptOrThrow(key);

        // then
        assertNotNull(result);
        assertEquals(expectedConcept, result);
        verify(conceptRepository).findConceptByExternalIdIgnoreCase("VOCAB1", "CONCEPT1");
    }

    @Test
    void findConceptOrThrow_shouldThrowException_whenNotFound() {
        // given
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("VOCAB2", "CONCEPT2");
        when(conceptRepository.findConceptByExternalIdIgnoreCase("VOCAB2", "CONCEPT2"))
                .thenReturn(java.util.Optional.empty());

        // when + then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> seeder.findConceptOrThrow(key)
        );

        assertEquals("Concept introuvable", exception.getMessage());
        verify(conceptRepository).findConceptByExternalIdIgnoreCase("VOCAB2", "CONCEPT2");
    }


}