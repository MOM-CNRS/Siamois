package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConceptServiceTest {

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private ConceptApi conceptApi;

    @Mock
    private LabelService labelService;

    private ConceptService conceptService;

    private Vocabulary vocabulary;
    private Concept concept;

    @BeforeEach
    void setUp() {
        conceptService = new ConceptService(conceptRepository, conceptApi, labelService);
        vocabulary = new Vocabulary();
        VocabularyType vocabularyType = new VocabularyType();

        vocabularyType.setId(1L);
        vocabularyType.setLabel("Thesaurus");

        vocabulary.setId(1L);
        vocabulary.setBaseUri("http://example.com");
        vocabulary.setExternalVocabularyId("vocab1");
        vocabulary.setType(vocabularyType);

        concept = new Concept();
        concept.setId(1L);
        concept.setExternalId("concept1");
        concept.setVocabulary(vocabulary);
    }

    @Test
    void saveOrGetConcept_shouldSaveConcept_whenNotExist() {
        // Given
        Concept fakeConcept = new Concept();
        fakeConcept.setExternalId("concept1");
        fakeConcept.setVocabulary(vocabulary);

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocab1", "concept1")).thenReturn(Optional.empty());
        when(conceptRepository.save(any(Concept.class))).thenReturn(concept);

        // When
        Concept result = conceptService.saveOrGetConcept(fakeConcept);

        // Then
        assertNotNull(result);
        verify(conceptRepository, times(1)).save(concept);
    }

    @Test
    void saveOrGetConcept_shouldReturnConcept_whenExist() {
        // Given
        Concept fakeConcept = new Concept();
        fakeConcept.setExternalId("concept1");
        fakeConcept.setVocabulary(vocabulary);

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocab1", "concept1")).thenReturn(Optional.of(concept));

        // When
        Concept result = conceptService.saveOrGetConcept(fakeConcept);

        // Then
        assertNotNull(result);
        verify(conceptRepository, never()).save(concept);
        assertEquals(concept, result);
    }

    @Test
    void saveOrGetConceptFromFullDTO_shouldUpdateLabels_whenConceptExists() {
        // Given
        FullInfoDTO conceptDTO = new FullInfoDTO();
        PurlInfoDTO identifier = new PurlInfoDTO();
        identifier.setValue("concept1");
        conceptDTO.setIdentifier(new PurlInfoDTO[]{identifier});

        PurlInfoDTO label = new PurlInfoDTO();
        label.setLang("en");
        label.setValue("Updated Label");
        conceptDTO.setPrefLabel(new PurlInfoDTO[]{label});

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocab1", "concept1")).thenReturn(Optional.of(concept));

        // When
        Concept result = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO);

        // Then
        assertNotNull(result);
        assertEquals(concept, result);
        verify(labelService, times(1)).updateLabel(concept, "en", "Updated Label");
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void saveOrGetConceptFromFullDTO_shouldCreateConcept_whenNotExist() {
        // Given
        FullInfoDTO conceptDTO = new FullInfoDTO();
        PurlInfoDTO identifier = new PurlInfoDTO();
        identifier.setValue("concept2");
        conceptDTO.setIdentifier(new PurlInfoDTO[]{identifier});

        PurlInfoDTO label = new PurlInfoDTO();
        label.setLang("fr");
        label.setValue("Nouveau Label");
        conceptDTO.setPrefLabel(new PurlInfoDTO[]{label});

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocab1", "concept2")).thenReturn(Optional.empty());
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> {
            Concept savedConcept = invocation.getArgument(0);
            savedConcept.setId(2L);
            return savedConcept;
        });

        // When
        Concept result = conceptService.saveOrGetConceptFromFullDTO(vocabulary, conceptDTO);

        // Then
        assertNotNull(result);
        assertEquals("concept2", result.getExternalId());
        assertEquals(vocabulary, result.getVocabulary());
        verify(labelService, times(1)).updateLabel(result, "fr", "Nouveau Label");
        verify(conceptRepository, times(1)).save(any(Concept.class));
    }

    @Test
    void updateAllLabelsFromDTO_shouldUpdateLabels_whenLabelsArePresent() {
        // Given
        FullInfoDTO conceptDTO = new FullInfoDTO();
        PurlInfoDTO label1 = new PurlInfoDTO();
        label1.setLang("en");
        label1.setValue("Label in English");

        PurlInfoDTO label2 = new PurlInfoDTO();
        label2.setLang("fr");
        label2.setValue("Label en Français");

        conceptDTO.setPrefLabel(new PurlInfoDTO[]{label1, label2});

        // When
        conceptService.updateAllLabelsFromDTO(concept, conceptDTO);

        // Then
        verify(labelService, times(1)).updateLabel(concept, "en", "Label in English");
        verify(labelService, times(1)).updateLabel(concept, "fr", "Label en Français");
    }

    @Test
    void updateAllLabelsFromDTO_shouldDoNothing_whenLabelsAreNull() {
        // Given
        FullInfoDTO conceptDTO = new FullInfoDTO();
        conceptDTO.setPrefLabel(null);

        // When
        conceptService.updateAllLabelsFromDTO(concept, conceptDTO);

        // Then
        verify(labelService, never()).updateLabel(any(Concept.class), anyString(), anyString());
    }

    @Test
    void findAllById_shouldReturnConcepts_whenIdsExist() {
        // Given
        Concept concept1 = new Concept();
        concept1.setId(1L);
        concept1.setExternalId("concept1");
        concept1.setVocabulary(vocabulary);

        Concept concept2 = new Concept();
        concept2.setId(2L);
        concept2.setExternalId("concept2");
        concept2.setVocabulary(vocabulary);

        List<Long> conceptIds = List.of(1L, 2L);
        List<Concept> expectedConcepts = List.of(concept1, concept2);

        when(conceptRepository.findAllById(conceptIds)).thenReturn(expectedConcepts);

        // When
        Object result = conceptService.findAllById(conceptIds);

        // Then
        assertNotNull(result);
        assertEquals(expectedConcepts, result);
        verify(conceptRepository, times(1)).findAllById(conceptIds);
    }

    @Test
    void findAllBySpatialUnitConceptsByInstitution_Success() {

        // Given
        Concept concept1 = new Concept();
        concept1.setId(1L);
        concept1.setExternalId("concept1");
        concept1.setVocabulary(vocabulary);

        Concept concept2 = new Concept();
        concept2.setId(2L);
        concept2.setExternalId("concept2");
        concept2.setVocabulary(vocabulary);

        Institution i = new Institution();
        i.setId(1L);


        List<Concept> expectedConcepts = List.of(concept1, concept2);

        when(conceptRepository.findAllBySpatialUnitOfInstitution(any(Long.class))).thenReturn(expectedConcepts);

        List<Concept> result = conceptService.findAllBySpatialUnitOfInstitution(i);

        // Then
        assertNotNull(result);
        assertEquals(expectedConcepts, result);

    }

    @Test
    void findAllByActionUnitConceptsByInstitution_Success() {

        // Given
        Concept concept1 = new Concept();
        concept1.setId(1L);
        concept1.setExternalId("concept1");
        concept1.setVocabulary(vocabulary);

        Concept concept2 = new Concept();
        concept2.setId(2L);
        concept2.setExternalId("concept2");
        concept2.setVocabulary(vocabulary);

        Institution i = new Institution();
        i.setId(1L);


        List<Concept> expectedConcepts = List.of(concept1, concept2);

        when(conceptRepository.findAllByActionUnitOfInstitution(any(Long.class))).thenReturn(expectedConcepts);

        List<Concept> result = conceptService.findAllByActionUnitOfInstitution(i);

        // Then
        assertNotNull(result);
        assertEquals(expectedConcepts, result);

    }

}
