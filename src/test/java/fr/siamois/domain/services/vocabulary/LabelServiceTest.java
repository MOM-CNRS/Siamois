package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.domain.models.vocabulary.label.LocalizedAltConceptLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.LocalizedAltConceptLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private LocalizedAltConceptLabelRepository localizedAltConceptLabelRepository;

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
    void findLabelOfConcept_shouldReturnNull_whenConceptIsNull() {
        // When
        LocalizedConceptData result = labelService.findLabelOf((Concept) null, "en");

        // Then
        assertNull(result);
    }

    @Test
    void findLabelOfConcept_shouldReturnExistingLocalizedData_whenPresent() {
        // Given
        Concept concept = new Concept();
        concept.setId(1L);
        LocalizedConceptData lcd = new LocalizedConceptData();
        lcd.setLabel("Label EN");
        when(localizedConceptDataRepository.findByConceptAndLangCode(1L, "en")).thenReturn(Optional.of(lcd));

        // When
        LocalizedConceptData result = labelService.findLabelOf(concept, "en");

        // Then
        assertNotNull(result);
        assertEquals("Label EN", result.getLabel());
    }

    @Test
    void findLabelOfConcept_shouldReturnNull_whenNotPresent() {
        // Given
        Concept concept = new Concept();
        concept.setId(2L);
        when(localizedConceptDataRepository.findByConceptAndLangCode(2L, "fr")).thenReturn(Optional.empty());

        // When
        LocalizedConceptData result = labelService.findLabelOf(concept, "fr");

        // Then
        assertNull(result);
    }

    @Test
    void updateLabelConcept_shouldCreateAndSave_whenLocalizedDataDoesNotExist() {
        // Given
        Concept concept = new Concept();
        concept.setId(10L);
        Concept parentConcept = new Concept();
        parentConcept.setId(20L);
        when(localizedConceptDataRepository.findByConceptAndLangCode(10L, "en")).thenReturn(Optional.empty());

        // When
        labelService.updateLabel(concept, "en", "New Concept Label", parentConcept);

        // Then
        verify(localizedConceptDataRepository, times(1)).save(any(LocalizedConceptData.class));

        // We can capture and assert properties by using ArgumentCaptor, but keeping test simple: verify save called
    }

    @Test
    void updateLabelConcept_shouldUpdateExistingAndSave_whenLocalizedDataExists() {
        // Given
        Concept concept = new Concept();
        concept.setId(11L);
        LocalizedConceptData lcd = new LocalizedConceptData();
        lcd.setLabel("Old");
        when(localizedConceptDataRepository.findByConceptAndLangCode(11L, "en")).thenReturn(Optional.of(lcd));

        // When
        labelService.updateLabel(concept, "en", "Updated", null);

        // Then
        assertEquals("Updated", lcd.getLabel());
        verify(localizedConceptDataRepository, times(1)).save(lcd);
    }

    @Test
    void findMatchingConcepts_shouldReturnEmpty_whenNoMatchesFound() {
        // Given
        Concept parent = new Concept();
        parent.setId(300L);
        parent.setExternalId("300L");


        when(localizedConceptDataRepository.findByParentConceptAndFieldCodeAndInputLimited(300L, "en", "nope", 5))
                .thenReturn(Set.of());

        // When
        var results = labelService.findMatchingConcepts(parent, "en", "nope", 5);

        // Then
        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void findMatchingConcepts_shouldReturnEmpty_whenFindAllThrows() {
        // Given
        Concept parent = new Concept();
        parent.setId(500L);

        when(localizedConceptDataRepository.findAllByParentConcept(parent, org.springframework.data.domain.Limit.of(5)))
                .thenThrow(new RuntimeException("boom"));

        // When
        var results = labelService.findMatchingConcepts(parent, "en", null, 5);

        // Then
        assertNotNull(results);
        assertEquals(0, results.size());
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

        when(localizedAltConceptLabelRepository.findById(any())).thenReturn(Optional.empty());

        // When
        labelService.updateAltLabel(savedConcept, "en", "New Alt", parent);

        // Then
        ArgumentCaptor<LocalizedAltConceptLabel> captor = ArgumentCaptor.forClass(LocalizedAltConceptLabel.class);
        verify(localizedAltConceptLabelRepository, times(1)).save(captor.capture());
        LocalizedAltConceptLabel saved = captor.getValue();
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

        LocalizedAltConceptLabel existing = new LocalizedAltConceptLabel();
        existing.setLabel("Old");
        existing.setConcept(savedConcept);
        existing.setLangCode("fr");

        when(localizedAltConceptLabelRepository.findById(any())).thenReturn(Optional.of(existing));

        // When
        labelService.updateAltLabel(savedConcept, "fr", "Updated", null);

        // Then
        assertEquals("Updated", existing.getLabel());
        verify(localizedAltConceptLabelRepository, times(1)).save(existing);
    }

    @Test
    void updateAltLabel_shouldNotSetParent_whenParentEqualsSavedConcept() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(4L);
        savedConcept.setExternalId("4L");

        when(localizedAltConceptLabelRepository.findById(any())).thenReturn(Optional.empty());

        // When
        labelService.updateAltLabel(savedConcept, "en", "Value", savedConcept);

        // Then
        ArgumentCaptor<LocalizedAltConceptLabel> captor = ArgumentCaptor.forClass(LocalizedAltConceptLabel.class);
        verify(localizedAltConceptLabelRepository, times(1)).save(captor.capture());
        LocalizedAltConceptLabel saved = captor.getValue();
        assertNotNull(saved);
        // parent must not be set because it's equal to savedConcept
        assertNull(saved.getParentConcept());
    }

}
