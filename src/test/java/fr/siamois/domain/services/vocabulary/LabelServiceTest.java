package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
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

    @Test
    void findMatchingConcepts_shouldReturnListOfMatchingConcepts_whenInputIsNotNull() {
        ConceptPrefLabel label = new ConceptPrefLabel();
        label.setLangCode("fr");
        label.setLabel("New Label");
        ConceptAltLabel altLabel = new ConceptAltLabel();
        altLabel.setLangCode("fr");
        altLabel.setLabel("Alternative Label");

        ConceptAltLabel altLabel2 = new ConceptAltLabel();
        altLabel2.setLangCode("fr");
        altLabel2.setLabel("Non matching");

        when(conceptLabelRepository
                .findAllByParentConceptAndInputLimited(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(label, altLabel));

        Concept concept = new Concept();
        concept.setId(5L);
        concept.setExternalId("5L");

        List<ConceptLabel> results = labelService.findMatchingConcepts(concept, "fr", "Label", 10);

        assertThat(results)
                .hasSize(2)
                .containsExactlyInAnyOrder(label, altLabel);
    }

    @Test
    void findMatchingConcepts_shouldReturnAllConcepts_whenInputIsEmpty() {
        ConceptPrefLabel label = new ConceptPrefLabel();
        label.setLangCode("fr");
        label.setLabel("New Label");
        ConceptAltLabel altLabel = new ConceptAltLabel();
        altLabel.setLangCode("fr");
        altLabel.setLabel("Alternative Label");

        ConceptAltLabel altLabel2 = new ConceptAltLabel();
        altLabel2.setLangCode("fr");
        altLabel2.setLabel("Non matching");

        Concept concept = new Concept();
        concept.setId(5L);
        concept.setExternalId("5L");

        when(conceptLabelRepository
                .findAllLabelsByParentConceptAndLangCode(eq(concept.getId()), anyString(), anyInt()))
                .thenReturn(List.of(label, altLabel, altLabel2));

        List<ConceptLabel> results = labelService.findMatchingConcepts(concept, "fr", "", 10);

        assertThat(results)
                .hasSize(3)
                .containsExactlyInAnyOrder(label, altLabel, altLabel2);
    }

    @Test
    void updateLabelConcept_shouldCreateAndSave_whenPrefLabelDoesNotExist_andParentDifferent() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(10L);
        savedConcept.setExternalId("10L");
        Concept parent = new Concept();
        parent.setId(11L);
        parent.setExternalId("11L");

        when(conceptLabelRepository.findByConceptAndLangCode(savedConcept, "en")).thenReturn(Optional.empty());

        // When
        labelService.updateLabel(savedConcept, "en", "New Pref", parent);

        // Then
        ArgumentCaptor<ConceptPrefLabel> captor = ArgumentCaptor.forClass(ConceptPrefLabel.class);
        verify(conceptLabelRepository, times(1)).save(captor.capture());
        ConceptPrefLabel saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("New Pref", saved.getLabel());
        assertEquals("en", saved.getLangCode());
        assertEquals(savedConcept, saved.getConcept());
        assertEquals(parent, saved.getParentConcept());
    }

    @Test
    void updateLabelConcept_shouldUpdateExistingAndSave_whenPrefLabelExists_andValueDiffers() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(20L);
        savedConcept.setExternalId("20L");

        ConceptPrefLabel existing = new ConceptPrefLabel();
        existing.setLabel("Old");
        existing.setConcept(savedConcept);
        existing.setLangCode("fr");

        when(conceptLabelRepository.findByConceptAndLangCode(savedConcept, "fr")).thenReturn(Optional.of(existing));
        when(conceptLabelRepository.save(any(ConceptPrefLabel.class))).thenAnswer(i -> i.getArgument(0));

        // When
        labelService.updateLabel(savedConcept, "fr", "Updated", null);

        // Then
        assertEquals("Updated", existing.getLabel());
        verify(conceptLabelRepository, times(1)).save(existing);
    }

    @Test
    void updateLabelConcept_shouldSaveEvenWhenValueIsSame() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(21L);
        savedConcept.setExternalId("21L");

        ConceptPrefLabel existing = new ConceptPrefLabel();
        existing.setLabel("Same");
        existing.setConcept(savedConcept);
        existing.setLangCode("en");

        when(conceptLabelRepository.findByConceptAndLangCode(savedConcept, "en")).thenReturn(Optional.of(existing));

        // When
        labelService.updateLabel(savedConcept, "en", "Same", null);

        // Then
        verify(conceptLabelRepository, times(1)).save(existing);
    }

    @Test
    void updateLabelConcept_shouldNotSetParent_whenParentEqualsSavedConcept() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(30L);
        savedConcept.setExternalId("30L");

        when(conceptLabelRepository.findByConceptAndLangCode(savedConcept, "en")).thenReturn(Optional.empty());

        // When
        labelService.updateLabel(savedConcept, "en", "Value", savedConcept);

        // Then
        ArgumentCaptor<ConceptPrefLabel> captor = ArgumentCaptor.forClass(ConceptPrefLabel.class);
        verify(conceptLabelRepository, times(1)).save(captor.capture());
        ConceptPrefLabel saved = captor.getValue();
        assertNotNull(saved);
        assertNull(saved.getParentConcept());
    }
}
