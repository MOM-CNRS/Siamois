package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private VocabularyLabelRepository vocabularyLabelRepository;

    @Mock
    private LocalizedConceptDataRepository localizedConceptDataRepository;

    @Mock
    private ConceptLabelRepository conceptLabelRepository;

    @InjectMocks
    private LabelService labelService;

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
    void findLabelOfVocabulary_shouldReturnNullLabel_whenVocabularyIsNull() {
        // When
        VocabularyLabel result = labelService.findLabelOf((Vocabulary) null, "fr");

        // Then
        assertNotNull(result);
        assertEquals("NULL", result.getValue());
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

    // Tests for Concept-related methods

    @Test
    void findLabelOfConcept_shouldReturnExternalCodeWhenNoFallbackMatch_whenNotPresent() {
        // Given
        Concept concept = new Concept();
        concept.setId(2L);
        concept.setExternalId("212");

        // When
        ConceptLabel result = labelService.findLabelOf(concept, "fr");

        // Then
        assertNotNull(result);
        assertEquals("[212]",  result.getLabel());
        assertEquals("fr", result.getLangCode());
    }

    @Test
    void updateAltLabel_shouldCreateAndSave_whenAltLabelDoesNotExist_andParentDifferent() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(1L);
        savedConcept.setExternalId("1L");
        Concept parent = new Concept();
        parent.setId(2L);
        parent.setExternalId("2L");

        // When
        labelService.updateAltLabel(savedConcept, "en", "New Alt", parent);

        // Then
        ArgumentCaptor<ConceptAltLabel> captor = ArgumentCaptor.forClass(ConceptAltLabel.class);
        verify(conceptLabelRepository, times(1)).save(captor.capture());
        ConceptAltLabel saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("New Alt", saved.getLabel());
        assertEquals("en", saved.getLangCode());
        assertEquals(savedConcept, saved.getConcept());
        assertEquals(parent, saved.getParentConcept());
    }

    @Test
    void updateAltLabel_shouldUpdateExistingAndSave_whenAltLabelExists_andNoParentProvided() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(3L);
        savedConcept.setExternalId("3L");

        ConceptAltLabel existing = new ConceptAltLabel();
        existing.setLabel("Old");
        existing.setConcept(savedConcept);
        existing.setLangCode("fr");


        when(conceptLabelRepository.findAltLabelByConceptAndLangCode(savedConcept, "fr")).thenReturn(Optional.of(existing));
        when(conceptLabelRepository.save(any(ConceptAltLabel.class))).thenAnswer(i -> i.getArgument(0));

        // When
        labelService.updateAltLabel(savedConcept, "fr", "Updated", null);

        // Then
        assertEquals("Updated", existing.getLabel());
        verify(conceptLabelRepository, times(1)).save(existing);
    }

    @Test
    void updateAltLabel_shouldNotSetParent_whenParentEqualsSavedConcept() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(4L);
        savedConcept.setExternalId("4L");

        // When
        labelService.updateAltLabel(savedConcept, "en", "Value", savedConcept);

        // Then
        ArgumentCaptor<ConceptAltLabel> captor = ArgumentCaptor.forClass(ConceptAltLabel.class);
        verify(conceptLabelRepository, times(1)).save(captor.capture());
        ConceptAltLabel saved = captor.getValue();
        assertNotNull(saved);
        // parent must not be set because it's equal to savedConcept
        assertNull(saved.getParentConcept());
    }

}
