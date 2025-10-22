package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionCodeSeederTest {

    @Mock
    ConceptRepository conceptRepository;

    @Mock
    ActionCodeRepository actionCodeRepository;

    @Captor
    ArgumentCaptor<ActionCode> actionCodeCaptor;

    ActionCodeSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new ActionCodeSeeder(conceptRepository, actionCodeRepository);
    }

    @Test
    void seed_shouldCreateActionCode_whenNotExists() {
        // Given
        var spec = new ActionCodeSeeder.ActionCodeSpec("069260", "conceptExtId", "vocabExtId");
        var concept = new Concept();
        concept.setId(123L);

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocabExtId", "conceptExtId"))
                .thenReturn(Optional.of(concept));
        when(actionCodeRepository.findById("069260")).thenReturn(Optional.empty());

        // When
        seeder.seed(List.of(spec));

        // Then
        verify(actionCodeRepository).save(actionCodeCaptor.capture());
        ActionCode saved = actionCodeCaptor.getValue();
        assertThat(saved.getCode()).isEqualTo("069260");
        assertThat(saved.getType()).isEqualTo(concept);

        verify(conceptRepository, times(1))
                .findConceptByExternalIdIgnoreCase("vocabExtId", "conceptExtId");
        verify(actionCodeRepository, times(1)).findById("069260");
        verifyNoMoreInteractions(actionCodeRepository, conceptRepository);
    }

    @Test
    void seed_shouldNotCreateActionCode_whenAlreadyExists() {
        // Given
        var spec = new ActionCodeSeeder.ActionCodeSpec("0610216", "tConcept", "tVocab");
        var concept = new Concept();
        concept.setId(1L);

        when(conceptRepository.findConceptByExternalIdIgnoreCase("tVocab", "tConcept"))
                .thenReturn(Optional.of(concept));
        when(actionCodeRepository.findById("0610216"))
                .thenReturn(Optional.of(new ActionCode())); // déjà présent

        // When
        seeder.seed(List.of(spec));

        // Then
        verify(actionCodeRepository, never()).save(any(ActionCode.class));
        verify(actionCodeRepository, times(1)).findById("0610216");
        verify(conceptRepository, times(1))
                .findConceptByExternalIdIgnoreCase("tVocab", "tConcept");
        verifyNoMoreInteractions(actionCodeRepository, conceptRepository);
    }

    @Test
    void seed_shouldThrow_whenConceptNotFound() {
        // Given
        var spec = new ActionCodeSeeder.ActionCodeSpec("A123", "missingConcept", "missingVocab");
        when(conceptRepository.findConceptByExternalIdIgnoreCase("missingVocab", "missingConcept"))
                .thenReturn(Optional.empty());


        List<ActionCodeSeeder.ActionCodeSpec> specs = List.of(spec);

        assertThatThrownBy(() -> seeder.seed(specs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Concept introuvable");

        verify(conceptRepository, times(1))
                .findConceptByExternalIdIgnoreCase("missingVocab", "missingConcept");
        verify(actionCodeRepository, never()).findById(anyString());
        verify(actionCodeRepository, never()).save(any());
        verifyNoMoreInteractions(actionCodeRepository, conceptRepository);
    }

    @Test
    void seed_shouldProcessMultipleSpecs() {
        // Given
        var s1 = new ActionCodeSeeder.ActionCodeSpec("000001", "c1", "v1");
        var s2 = new ActionCodeSeeder.ActionCodeSpec("000002", "c2", "v2");

        var c1 = new Concept(); c1.setId(10L);
        var c2 = new Concept(); c2.setId(20L);

        when(conceptRepository.findConceptByExternalIdIgnoreCase("v1", "c1"))
                .thenReturn(Optional.of(c1));
        when(conceptRepository.findConceptByExternalIdIgnoreCase("v2", "c2"))
                .thenReturn(Optional.of(c2));

        when(actionCodeRepository.findById("000001")).thenReturn(Optional.empty());
        when(actionCodeRepository.findById("000002")).thenReturn(Optional.empty());

        // When
        seeder.seed(List.of(s1, s2));

        // Then
        verify(actionCodeRepository, times(2)).save(actionCodeCaptor.capture());
        var savedList = actionCodeCaptor.getAllValues();

        assertThat(savedList)
                .extracting(ActionCode::getCode)
                .containsExactlyInAnyOrder("000001", "000002");

        assertThat(savedList)
                .extracting(ActionCode::getType)
                .containsExactlyInAnyOrder(c1, c2);

        verify(conceptRepository, times(1))
                .findConceptByExternalIdIgnoreCase("v1", "c1");
        verify(conceptRepository, times(1))
                .findConceptByExternalIdIgnoreCase("v2", "c2");
        verify(actionCodeRepository, times(1)).findById("000001");
        verify(actionCodeRepository, times(1)).findById("000002");
        verifyNoMoreInteractions(actionCodeRepository, conceptRepository);
    }
}
