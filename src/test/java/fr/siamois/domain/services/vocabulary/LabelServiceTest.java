package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        assertEquals("[212]", result.getLabel());
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
    void updateAltLabel_shouldReuseExistingRow_whenSameLabelAlreadyExists() {
        // Given
        Concept savedConcept = new Concept();
        savedConcept.setId(3L);
        savedConcept.setExternalId("3L");

        ConceptAltLabel existing = new ConceptAltLabel();
        existing.setLabel("Existing");
        existing.setConcept(savedConcept);
        existing.setLangCode("fr");

        when(conceptLabelRepository.findAltLabelByConceptAndLangCodeAndLabel(savedConcept, "fr", "Existing")).thenReturn(Optional.of(existing));
        when(conceptLabelRepository.save(any(ConceptAltLabel.class))).thenAnswer(i -> i.getArgument(0));

        // When
        labelService.updateAltLabel(savedConcept, "fr", "Existing", null);

        // Then
        assertEquals("Existing", existing.getLabel());
        verify(conceptLabelRepository, times(1)).save(existing);
    }

    @Test
    void updateAltLabel_shouldCreateANewRow_whenAnotherAltLabelAlreadyExistsForTheSameLanguage() {
        // Given: a concept can have several altLabels (synonyms) in the same language
        Concept savedConcept = new Concept();
        savedConcept.setId(3L);
        savedConcept.setExternalId("3L");

        when(conceptLabelRepository.findAltLabelByConceptAndLangCodeAndLabel(savedConcept, "fr", "fait")).thenReturn(Optional.empty());
        when(conceptLabelRepository.save(any(ConceptAltLabel.class))).thenAnswer(i -> i.getArgument(0));

        // When
        labelService.updateAltLabel(savedConcept, "fr", "fait", null);

        // Then
        ArgumentCaptor<ConceptAltLabel> captor = ArgumentCaptor.forClass(ConceptAltLabel.class);
        verify(conceptLabelRepository, times(1)).save(captor.capture());
        assertEquals("fait", captor.getValue().getLabel());
        assertEquals("fr", captor.getValue().getLangCode());
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
    void replaceAltLabels_shouldKeepEverySynonym_andDropStaleOnes() {
        // Given: the concept already carries two French altLabels, one of which ("perime")
        // is no longer returned by the thesaurus, plus the new import brings back two French
        // synonyms ("objet" and "fait") that must both survive.
        Concept savedConcept = new Concept();
        savedConcept.setId(5L);
        savedConcept.setExternalId("5L");

        ConceptAltLabel staleLabel = new ConceptAltLabel();
        staleLabel.setConcept(savedConcept);
        staleLabel.setLangCode("fr");
        staleLabel.setLabel("perime");

        ConceptAltLabel keptLabel = new ConceptAltLabel();
        keptLabel.setConcept(savedConcept);
        keptLabel.setLangCode("fr");
        keptLabel.setLabel("objet");

        when(conceptLabelRepository.findAllAltLabelsByConcept(savedConcept))
                .thenReturn(Set.of(staleLabel, keptLabel));
        when(conceptLabelRepository.findAltLabelByConceptAndLangCodeAndLabel(savedConcept, "fr", "objet"))
                .thenReturn(Optional.of(keptLabel));
        when(conceptLabelRepository.findAltLabelByConceptAndLangCodeAndLabel(savedConcept, "fr", "fait"))
                .thenReturn(Optional.empty());
        when(conceptLabelRepository.save(any(ConceptAltLabel.class))).thenAnswer(i -> i.getArgument(0));

        PurlInfoDTO objet = new PurlInfoDTO();
        objet.setLang("fr");
        objet.setValue("objet");
        PurlInfoDTO fait = new PurlInfoDTO();
        fait.setLang("fr");
        fait.setValue("fait");

        // When
        labelService.replaceAltLabels(savedConcept, new PurlInfoDTO[]{objet, fait}, null);

        // Then
        verify(conceptLabelRepository, times(1)).deleteAll(List.of(staleLabel));
        ArgumentCaptor<ConceptAltLabel> captor = ArgumentCaptor.forClass(ConceptAltLabel.class);
        verify(conceptLabelRepository, times(2)).save(captor.capture());
        List<String> savedLabels = captor.getAllValues().stream().map(ConceptAltLabel::getLabel).toList();
        assertTrue(savedLabels.containsAll(List.of("objet", "fait")));
    }

    @Test
    void replaceAltLabels_shouldWriteOnce_twoSynonymsThatDifferOnlyByCase() {
        // Given: the thesaurus lists "CHA" and "ChA" for the same concept; the label column ignores case,
        // so writing both would break its unique key.
        Concept savedConcept = new Concept();
        savedConcept.setId(6L);
        savedConcept.setExternalId("6L");
        when(conceptLabelRepository.findAllAltLabelsByConcept(savedConcept)).thenReturn(Set.of());
        when(conceptLabelRepository.findAltLabelByConceptAndLangCodeAndLabel(any(), any(), any())).thenReturn(Optional.empty());
        when(conceptLabelRepository.save(any(ConceptAltLabel.class))).thenAnswer(i -> i.getArgument(0));

        PurlInfoDTO upper = new PurlInfoDTO();
        upper.setLang("fr");
        upper.setValue("CHA");
        PurlInfoDTO mixed = new PurlInfoDTO();
        mixed.setLang("fr");
        mixed.setValue("ChA");

        // When
        labelService.replaceAltLabels(savedConcept, new PurlInfoDTO[]{upper, mixed}, null);

        // Then
        ArgumentCaptor<ConceptAltLabel> captor = ArgumentCaptor.forClass(ConceptAltLabel.class);
        verify(conceptLabelRepository, times(1)).save(captor.capture());
        assertEquals("CHA", captor.getValue().getLabel());
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

    @Test
    void testFindAllAltLabelOf_Found() {
        // given
        Concept concept;
        ConceptAltLabel altLabel1;
        ConceptAltLabel altLabel2;
        concept = new Concept();
        concept.setId(1L);

        altLabel1 = new ConceptAltLabel();
        altLabel1.setLabel("Synonym A");
        altLabel1.setLangCode("en");

        altLabel2 = new ConceptAltLabel();
        altLabel2.setLabel("Synonym B");
        altLabel2.setLangCode("en");

        when(conceptLabelRepository.findAllAltLabelsByLangCodeAndConcept("en", concept))
                .thenReturn(Set.of(altLabel1, altLabel2));

        // when
        Set<ConceptAltLabel> result = labelService.findAllAltLabelOf(concept, "en");

        // then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(altLabel1));
        assertTrue(result.contains(altLabel2));

        verify(conceptLabelRepository, times(1))
                .findAllAltLabelsByLangCodeAndConcept("en", concept);
    }

    @Test
    void testFindAllAltLabelOf_Empty() {
        // given
        Concept concept;
        ConceptAltLabel altLabel1;
        ConceptAltLabel altLabel2;
        concept = new Concept();
        concept.setId(1L);

        altLabel1 = new ConceptAltLabel();
        altLabel1.setLabel("Synonym A");
        altLabel1.setLangCode("en");

        altLabel2 = new ConceptAltLabel();
        altLabel2.setLabel("Synonym B");
        altLabel2.setLangCode("en");
        when(conceptLabelRepository.findAllAltLabelsByLangCodeAndConcept("fr", concept))
                .thenReturn(Set.of());

        // when
        Set<ConceptAltLabel> result = labelService.findAllAltLabelOf(concept, "fr");

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(conceptLabelRepository, times(1))
                .findAllAltLabelsByLangCodeAndConcept("fr", concept);
    }

    private static PurlInfoDTO purl(String lang, String value) {
        PurlInfoDTO purl = new PurlInfoDTO();
        purl.setLang(lang);
        purl.setValue(value);
        return purl;
    }

    @Test
    void updateAllLabelsFromDTOs_shouldReadTheLabelsOnce_andWriteOnlyWhatChanged() {
        Concept savedConcept = new Concept();
        savedConcept.setId(7L);
        savedConcept.setExternalId("7");
        ConceptPrefLabel unchanged = new ConceptPrefLabel();
        unchanged.setId(1L);
        unchanged.setConcept(savedConcept);
        unchanged.setLangCode("fr");
        unchanged.setLabel("vase");
        ConceptAltLabel stale = new ConceptAltLabel();
        stale.setId(2L);
        stale.setConcept(savedConcept);
        stale.setLangCode("fr");
        stale.setLabel("perime");
        when(conceptLabelRepository.findAllByConceptIn(any())).thenReturn(List.of(unchanged, stale));

        FullInfoDTO dto = new FullInfoDTO();
        dto.setPrefLabel(new PurlInfoDTO[]{purl("fr", "vase"), purl("en", "vase")});
        dto.setAltLabel(new PurlInfoDTO[]{purl("fr", "pot")});

        labelService.updateAllLabelsFromDTOs(Map.of(savedConcept, dto), null);

        verify(conceptLabelRepository, times(1)).findAllByConceptIn(any());
        verify(conceptLabelRepository).deleteAll(List.of(stale));
        ArgumentCaptor<Iterable<ConceptLabel>> captor = ArgumentCaptor.captor();
        verify(conceptLabelRepository).saveAll(captor.capture());
        List<String> written = new ArrayList<>();
        captor.getValue().forEach(label -> written.add(label.getLangCode() + " " + label.getLabel()));
        // the unchanged French pref label is not written again
        assertThat(written).containsExactlyInAnyOrder("en vase", "fr pot");
    }

    @Test
    void updateAllLabelsFromDTOs_shouldUpdateTheExistingSynonym_whenOnlyItsCaseChanged() {
        // the label column ignores case : deleting "Pot" and inserting "pot" would collide on its unique key
        Concept savedConcept = new Concept();
        savedConcept.setId(8L);
        savedConcept.setExternalId("8");
        ConceptAltLabel existing = new ConceptAltLabel();
        existing.setId(3L);
        existing.setConcept(savedConcept);
        existing.setLangCode("fr");
        existing.setLabel("Pot");
        when(conceptLabelRepository.findAllByConceptIn(any())).thenReturn(List.of(existing));

        FullInfoDTO dto = new FullInfoDTO();
        dto.setAltLabel(new PurlInfoDTO[]{purl("fr", "pot")});

        labelService.updateAllLabelsFromDTOs(Map.of(savedConcept, dto), null);

        verify(conceptLabelRepository, never()).deleteAll(any());
        assertEquals("pot", existing.getLabel());
        verify(conceptLabelRepository).saveAll(argThat(labels -> labels.iterator().next() == existing));
    }

    @Test
    void updateAllLabelsFromDTOs_shouldTagTheLabelsWithTheFieldParent_exceptThoseOfTheParentItself() {
        Concept fieldParent = new Concept();
        fieldParent.setId(100L);
        fieldParent.setExternalId("100");
        Concept child = new Concept();
        child.setId(9L);
        child.setExternalId("9");
        ConceptPrefLabel untagged = new ConceptPrefLabel();
        untagged.setId(4L);
        untagged.setConcept(child);
        untagged.setLangCode("fr");
        untagged.setLabel("vase");
        ConceptPrefLabel alreadyTagged = new ConceptPrefLabel();
        alreadyTagged.setId(5L);
        alreadyTagged.setConcept(child);
        alreadyTagged.setLangCode("en");
        alreadyTagged.setLabel("vase");
        alreadyTagged.setParentConcept(fieldParent);
        when(conceptLabelRepository.findAllByConceptIn(any())).thenReturn(List.of(untagged, alreadyTagged));

        FullInfoDTO childDto = new FullInfoDTO();
        childDto.setPrefLabel(new PurlInfoDTO[]{purl("fr", "vase"), purl("en", "vase")});
        // the label column ignores case : the second synonym is the first one
        childDto.setAltLabel(new PurlInfoDTO[]{purl("fr", "CHA"), purl("fr", "ChA")});
        FullInfoDTO parentDto = new FullInfoDTO();
        parentDto.setPrefLabel(new PurlInfoDTO[]{purl("fr", "céramique")});
        Map<Concept, FullInfoDTO> dtoByConcept = new LinkedHashMap<>();
        dtoByConcept.put(child, childDto);
        dtoByConcept.put(fieldParent, parentDto);

        labelService.updateAllLabelsFromDTOs(dtoByConcept, fieldParent);

        ArgumentCaptor<Iterable<ConceptLabel>> captor = ArgumentCaptor.captor();
        verify(conceptLabelRepository).saveAll(captor.capture());
        Map<String, ConceptLabel> written = new HashMap<>();
        captor.getValue().forEach(label -> written.put(label.getLangCode() + " " + label.getLabel(), label));
        assertThat(written).containsOnlyKeys("fr vase", "fr CHA", "fr céramique");
        assertThat(written.get("fr vase").getParentConcept()).isSameAs(fieldParent);
        assertThat(written.get("fr CHA").getParentConcept()).isSameAs(fieldParent);
        assertNull(written.get("fr céramique").getParentConcept());
    }
}
