package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void findMatchingConcepts_shouldReturnAllConcepts_whenInputIsNullOrEmpty() {
        // Given
        Concept parent = new Concept();
        parent.setId(100L);

        LocalizedConceptData lcd1 = new LocalizedConceptData();
        Concept c1 = new Concept();
        c1.setId(1L);
        c1.setExternalId("1L");
        lcd1.setConcept(c1);

        LocalizedConceptData lcd2 = new LocalizedConceptData();
        Concept c2 = new Concept();
        c2.setId(2L);
        c2.setExternalId("2L");
        lcd2.setConcept(c2);

        when(localizedConceptDataRepository.findAllByParentConcept(parent, org.springframework.data.domain.Limit.of(10)))
                .thenReturn(List.of(lcd1, lcd2));

        // When
        var results = labelService.findMatchingConcepts(parent, "en", null, 10);

        // Then
        assertEquals(2, results.size());
        // ensure both concepts are present
        boolean contains1 = results.stream().anyMatch(c -> c.getId().equals(1L));
        boolean contains2 = results.stream().anyMatch(c -> c.getId().equals(2L));
        assertTrue(contains1);
        assertTrue(contains2);
    }

    @Test
    void findMatchingConcepts_shouldReturnMatchingConcepts_whenInputProvided() {
        // Given
        Concept parent = new Concept();
        parent.setId(200L);

        LocalizedConceptData matched = new LocalizedConceptData();
        Concept cm = new Concept();
        cm.setId(3L);
        // no-op
        matched.setConcept(cm);

        when(localizedConceptDataRepository.findConceptByFieldCodeAndInputLimit(200L, "en", "inp", 5))
                .thenReturn(Set.of(matched));

        LocalizedConceptData otherLang = new LocalizedConceptData();
        Concept co = new Concept();
        co.setId(4L);
        co.setExternalId("4L");
        otherLang.setConcept(co);

        when(localizedConceptDataRepository.findLocalizedConceptDataByParentConceptAndLabelContaining(parent, "inp"))
                .thenReturn(Set.of(otherLang));

        // When
        var results = labelService.findMatchingConcepts(parent, "en", "inp", 5);

        // Then
        // Should contain both concepts up to the limit
        assertEquals(2, results.size());
        boolean has3 = results.stream().anyMatch(c -> c.getId().equals(3L));
        boolean has4 = results.stream().anyMatch(c -> c.getId().equals(4L));
        assertTrue(has3);
        assertTrue(has4);
    }

    @Test
    void findMatchingConcepts_shouldReturnEmpty_whenNoMatchesFound() {
        // Given
        Concept parent = new Concept();
        parent.setId(300L);
        parent.setExternalId("300L");


        when(localizedConceptDataRepository.findConceptByFieldCodeAndInputLimit(300L, "en", "nope", 5))
                .thenReturn(Set.of());
        when(localizedConceptDataRepository.findLocalizedConceptDataByParentConceptAndLabelContaining(parent, "nope"))
                .thenReturn(Set.of());

        // When
        var results = labelService.findMatchingConcepts(parent, "en", "nope", 5);

        // Then
        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void findMatchingConcepts_shouldEnforceLimit_whenOtherLangReturnsMoreThanLimit() {
        // Given
        Concept parent = new Concept();
        parent.setId(400L);

        // similarity search returns nothing
        when(localizedConceptDataRepository.findConceptByFieldCodeAndInputLimit(400L, "en", "inp", 1))
                .thenReturn(Set.of());

        // other lang returns 3 matches but limit is 1
        LocalizedConceptData a = new LocalizedConceptData();
        Concept ca = new Concept(); ca.setId(10L); ca.setExternalId("10"); a.setConcept(ca);
        LocalizedConceptData b = new LocalizedConceptData();
        Concept cb = new Concept(); cb.setId(11L); cb.setExternalId("11"); b.setConcept(cb);
        LocalizedConceptData c = new LocalizedConceptData();
        Concept cc = new Concept(); cc.setId(12L); cc.setExternalId("12"); c.setConcept(cc);

        when(localizedConceptDataRepository.findLocalizedConceptDataByParentConceptAndLabelContaining(parent, "inp"))
                .thenReturn(Set.of(a, b, c));

        // When
        var results = labelService.findMatchingConcepts(parent, "en", "inp", 1);

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());
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

}
