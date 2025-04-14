package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private ConceptLabelRepository conceptLabelRepository;

    @Mock
    private VocabularyLabelRepository vocabularyLabelRepository;

    @InjectMocks
    private LabelService labelService;

    @Test
    void findLabelOfConcept_shouldReturnNullLabel_whenConceptIsNull() {
        // When
        ConceptLabel result = labelService.findLabelOf((Concept) null, "en");

        // Then
        assertNotNull(result);
        assertEquals("NULL", result.getValue());
    }

    @Test
    void findLabelOfVocab_shouldReturnNullLabel_whenConceptIsNull() {
        // When
        VocabularyLabel result = labelService.findLabelOf((Vocabulary) null, "en");

        // Then
        assertNotNull(result);
        assertEquals("NULL", result.getValue());
    }

    @Test
    void findLabelOfConcept_shouldReturnExistingLabel_whenLabelExists() {
        // Given
        Concept concept = new Concept();
        ConceptLabel label = new ConceptLabel();
        label.setValue("Existing Label");
        when(conceptLabelRepository.findByConceptAndLangCode(concept, "en")).thenReturn(Optional.of(label));

        // When
        ConceptLabel result = labelService.findLabelOf(concept, "en");

        // Then
        assertNotNull(result);
        assertEquals("Existing Label", result.getValue());
    }


    @Test
    void findLabelOfConcept_shouldReturnFirstLabel_whenNoLabelForLangExists() {
        // Given
        Concept concept = new Concept();
        ConceptLabel label = new ConceptLabel();
        label.setValue("Fallback Label");
        when(conceptLabelRepository.findByConceptAndLangCode(concept, "en")).thenReturn(Optional.empty());
        when(conceptLabelRepository.findAllByConcept(concept)).thenReturn(List.of(label));

        // When
        ConceptLabel result = labelService.findLabelOf(concept, "en");

        // Then
        assertNotNull(result);
        assertEquals("Fallback Label", result.getValue());
    }

    @Test
    void findLabelOfConcept_shouldReturnDefaultLabel_whenNoLabelsExist() {
        // Given
        Concept concept = new Concept();
        concept.setExternalId("concept1");
        when(conceptLabelRepository.findByConceptAndLangCode(concept, "en")).thenReturn(Optional.empty());
        when(conceptLabelRepository.findAllByConcept(concept)).thenReturn(List.of());

        // When
        ConceptLabel result = labelService.findLabelOf(concept, "en");

        // Then
        assertNotNull(result);
        assertEquals("concept1", result.getValue());
    }

    @Test
    void updateLabelConcept_shouldCreateLabel_whenLabelDoesNotExist() {
        // Given
        Concept concept = new Concept();
        when(conceptLabelRepository.findByConceptAndLangCode(concept, "en")).thenReturn(Optional.empty());

        // When
        labelService.updateLabel(concept, "en", "New Label");

        // Then
        verify(conceptLabelRepository, times(1)).save(any(ConceptLabel.class));
    }

    @Test
    void updateLabelConcept_shouldUpdateLabel_whenLabelExistsAndValueDiffers() {
        // Given
        Concept concept = new Concept();
        ConceptLabel label = new ConceptLabel();
        label.setValue("Old Label");
        when(conceptLabelRepository.findByConceptAndLangCode(concept, "en")).thenReturn(Optional.of(label));

        // When
        labelService.updateLabel(concept, "en", "Updated Label");

        // Then
        assertEquals("Updated Label", label.getValue());
        verify(conceptLabelRepository, times(1)).save(label);
    }

    @Test
    void updateLabelConcept_shouldDoNothing_whenLabelExistsAndValueIsSame() {
        // Given
        Concept concept = new Concept();
        ConceptLabel label = new ConceptLabel();
        label.setValue("Same Label");
        when(conceptLabelRepository.findByConceptAndLangCode(concept, "en")).thenReturn(Optional.of(label));

        // When
        labelService.updateLabel(concept, "en", "Same Label");

        // Then
        verify(conceptLabelRepository, never()).save(any(ConceptLabel.class));
    }

    @Test
    void findLabelOfVocabulary_shouldReturnExistingLabel_whenLabelExists() {
        // Given
        Vocabulary vocabulary = new Vocabulary();
        VocabularyLabel label = new VocabularyLabel();
        label.setValue("Existing Label");
        when(vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, "en")).thenReturn(Optional.of(label));

        // When
        VocabularyLabel result = labelService.findLabelOf(vocabulary, "en");

        // Then
        assertNotNull(result);
        assertEquals("Existing Label", result.getValue());
    }

    @Test
    void findLabelOfVocabulary_shouldReturnDefaultLabel_whenNoLabelsExist() {
        // Given
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setExternalVocabularyId("vocab1");
        when(vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, "en")).thenReturn(Optional.empty());
        when(vocabularyLabelRepository.findAllByVocabulary(vocabulary)).thenReturn(List.of());

        // When
        VocabularyLabel result = labelService.findLabelOf(vocabulary, "en");

        // Then
        assertNotNull(result);
        assertEquals("vocab1", result.getValue());
    }

    @Test
    void updateLabelVocabulary_shouldCreateLabel_whenLabelDoesNotExist() {
        // Given
        Vocabulary vocabulary = new Vocabulary();
        when(vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, "en")).thenReturn(Optional.empty());

        // When
        labelService.updateLabel(vocabulary, "en", "New Label");

        // Then
        verify(vocabularyLabelRepository, times(1)).save(any(VocabularyLabel.class));
    }

    @Test
    void updateLabelVocabulary_shouldUpdateLabel_whenLabelExistsAndValueDiffers() {
        // Given
        Vocabulary vocabulary = new Vocabulary();
        VocabularyLabel label = new VocabularyLabel();
        label.setValue("Old Label");
        when(vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, "en")).thenReturn(Optional.of(label));

        // When
        labelService.updateLabel(vocabulary, "en", "Updated Label");

        // Then
        assertEquals("Updated Label", label.getValue());
        verify(vocabularyLabelRepository, times(1)).save(label);
    }

    @Test
    void updateLabelVocabulary_shouldDoNothing_whenLabelExistsAndValueIsSame() {
        // Given
        Vocabulary vocabulary = new Vocabulary();
        VocabularyLabel label = new VocabularyLabel();
        label.setValue("Same Label");
        when(vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, "en")).thenReturn(Optional.of(label));

        // When
        labelService.updateLabel(vocabulary, "en", "Same Label");

        // Then
        verify(vocabularyLabelRepository, never()).save(any(VocabularyLabel.class));
    }

}