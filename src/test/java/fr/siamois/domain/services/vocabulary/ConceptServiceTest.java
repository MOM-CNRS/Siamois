package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.events.publisher.ConceptChangeEventPublisher;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRelatedLinkRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConceptServiceTest {

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private ConceptApi conceptApi;

    @Mock
    private LabelService labelService;

    @Mock
    private LocalizedConceptDataRepository localizedConceptDataRepository;

    @Mock
    private ConceptRelatedLinkRepository conceptRelatedLinkRepository;

    @Mock
    private ConceptChangeEventPublisher conceptChangeEventPublisher;

    @Mock
    private ConceptLabelRepository conceptLabelRepository;

    @Mock
    private ConceptHierarchyRepository conceptHierarchyRepository;

    @InjectMocks
    private ConceptService conceptService;

    private Vocabulary vocabulary;
    private Concept concept;

    @BeforeEach
    void setUp() {
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

        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Institution 1");
        institution.setIdentifier("inst1");

        Person person = new Person();
        person.setId(1L);
        person.setName("User 1");
        person.setUsername("User1");
        person.setEmail("some@mail.com");
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

    @Test
    void findById() {
        concept.setId(1L);

        when(conceptRepository.findById(1L)).thenReturn(Optional.of(concept));

        Optional<Concept> result = conceptService.findById(1L);
        assertThat(result)
                .isPresent()
                .get()
                .isEqualTo(concept);
    }

    @Test
    void saveOrGetConceptFromFullDTO_shouldReturnExistingConcept_andUpdateLabelsAndDefinitions() {
        FullInfoDTO dto = new FullInfoDTO();
        PurlInfoDTO id = new PurlInfoDTO();
        id.setValue("concept1");
        PurlInfoDTO label = new PurlInfoDTO();
        label.setLang("fr");
        label.setValue("Libellé FR");
        PurlInfoDTO def = new PurlInfoDTO();
        def.setLang("fr");
        def.setValue("Définition FR");

        dto.setIdentifier(new PurlInfoDTO[]{id});
        dto.setPrefLabel(new PurlInfoDTO[]{label});
        dto.setDefinition(new PurlInfoDTO[]{def});

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocab1", "concept1")).thenReturn(Optional.of(concept));
        when(localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), "fr")).thenReturn(Optional.empty());
        when(conceptRepository.save(any(Concept.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Concept result = conceptService.saveOrGetConceptFromFullDTO(vocabulary, dto, null);

        // Then
        assertNotNull(result);
        assertEquals(concept, result);
        verify(labelService, times(1)).updateLabel(concept, "fr", "Libellé FR", null);
        verify(localizedConceptDataRepository, times(1)).save(any(LocalizedConceptData.class));
    }

    @Test
    void saveOrGetConceptFromFullDTO_shouldCreateAndReturnNewConcept_andUpdateLabelsAndDefinitions() {
        // Given repository does not contain concept
        FullInfoDTO dto = new FullInfoDTO();
        PurlInfoDTO id = new PurlInfoDTO();
        id.setValue("concept1");
        PurlInfoDTO label = new PurlInfoDTO();
        label.setLang("en");
        label.setValue("Label EN");
        PurlInfoDTO def = new PurlInfoDTO();
        def.setLang("en");
        def.setValue("Definition EN");

        dto.setIdentifier(new PurlInfoDTO[]{id});
        dto.setPrefLabel(new PurlInfoDTO[]{label});
        dto.setDefinition(new PurlInfoDTO[]{def});

        when(conceptRepository.findConceptByExternalIdIgnoreCase("vocab1", "concept1")).thenReturn(Optional.empty());

        Concept savedConcept = new Concept();
        savedConcept.setId(2L);
        savedConcept.setExternalId("concept1");
        savedConcept.setVocabulary(vocabulary);

        when(conceptRepository.save(any(Concept.class))).thenReturn(savedConcept);
        when(localizedConceptDataRepository.findByConceptAndLangCode(savedConcept.getId(), "en")).thenReturn(Optional.empty());

        // When
        Concept result = conceptService.saveOrGetConceptFromFullDTO(vocabulary, dto, null);

        // Then
        assertNotNull(result);
        assertEquals(savedConcept, result);
        verify(conceptRepository, times(1)).save(any(Concept.class));
        verify(labelService, times(1)).updateLabel(savedConcept, "en", "Label EN", null);
        verify(localizedConceptDataRepository, times(1)).save(any(LocalizedConceptData.class));
    }

    @Test
    void updateAllLabelsFromDTO_shouldDoNothingWhenPrefLabelNull() {
        FullInfoDTO dto = new FullInfoDTO();
        dto.setPrefLabel(null);

        conceptService.updateAllLabelsFromDTO(concept, dto, null);

        verify(labelService, never()).updateLabel(any(Concept.class), any(), any(), any());
    }

    @Test
    void updateAllLabelsFromDTO_shouldDoNothingWhenPrefLabelEmpty() {
        FullInfoDTO dto = new FullInfoDTO();
        dto.setPrefLabel(new PurlInfoDTO[]{});

        conceptService.updateAllLabelsFromDTO(concept, dto, null);

        verify(labelService, never()).updateLabel(any(Concept.class), any(), any(), any());
    }

    @Test
    void updateAllLabelsFromDTO_shouldUpdateMultipleLabels_withParentConcept() {
        FullInfoDTO dto = new FullInfoDTO();
        PurlInfoDTO l1 = new PurlInfoDTO();
        l1.setLang("fr");
        l1.setValue("Libelle FR");
        PurlInfoDTO l2 = new PurlInfoDTO();
        l2.setLang("en");
        l2.setValue("Label EN");

        dto.setPrefLabel(new PurlInfoDTO[]{l1, l2});

        Concept parent = new Concept();
        parent.setId(99L);

        conceptService.updateAllLabelsFromDTO(concept, dto, parent);

        verify(labelService, times(1)).updateLabel(concept, "fr", "Libelle FR", parent);
        verify(labelService, times(1)).updateLabel(concept, "en", "Label EN", parent);
    }

    @Test
    void updateAllLabelsFromDTO_shouldUpdateLabel_withNullParent() {
        FullInfoDTO dto = new FullInfoDTO();
        PurlInfoDTO l1 = new PurlInfoDTO();
        l1.setLang("fr");
        l1.setValue("Libelle FR");

        dto.setPrefLabel(new PurlInfoDTO[]{l1});

        conceptService.updateAllLabelsFromDTO(concept, dto, null);

        verify(labelService, times(1)).updateLabel(concept, "fr", "Libelle FR", null);
    }

    @Test
    void saveAllSubConceptOfIfUpdated_shouldReturnWhenApiReturnsNull() throws Exception {
        // Given
        fr.siamois.domain.models.settings.ConceptFieldConfig config = new fr.siamois.domain.models.settings.ConceptFieldConfig();
        config.setId(10L);
        config.setFieldCode("FIELD1");
        config.setConcept(concept);

        when(conceptApi.fetchDownExpansion(config)).thenReturn(null);

        // When
        conceptService.saveAllSubConceptOfIfUpdated(config);

        // Then
        verify(conceptChangeEventPublisher, never()).publishEvent(anyString());
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void saveAllSubConceptOfIfUpdated_shouldReturnWhenParentHasNoNarrower() throws Exception {
        // Given
        fr.siamois.domain.models.settings.ConceptFieldConfig config = new fr.siamois.domain.models.settings.ConceptFieldConfig();
        config.setId(11L);
        config.setFieldCode("FIELD2");
        config.setConcept(concept);

        // Build a branch where parent exists but has no narrower
        ConceptBranchDTO.ConceptBranchDTOBuilder builder = new ConceptBranchDTO.ConceptBranchDTOBuilder();
        ConceptBranchDTO branchDTO = builder
                .identifier("url1", "concept1")
                .build();

        when(conceptApi.fetchDownExpansion(config)).thenReturn(branchDTO);

        // When
        conceptService.saveAllSubConceptOfIfUpdated(config);

        // Then
        verify(conceptChangeEventPublisher, times(1)).publishEvent(config.getFieldCode());
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void saveAllSubConceptOfIfUpdated_shouldSaveConceptsAndRelations_whenBranchHasNarrowers() throws Exception {
        // Given
        ConceptFieldConfig config = new ConceptFieldConfig();
        config.setId(12L);
        config.setFieldCode("FIELD3");
        config.setConcept(concept);

        // Build branch with parent and child
        ConceptBranchDTO.ConceptBranchDTOBuilder builder = new ConceptBranchDTO.ConceptBranchDTOBuilder();
        ConceptBranchDTO branchDTO = builder
                .identifier("concept1", "concept1")
                .identifier("url-child", "concept-child")
                .label("concept1", "Parent Label", "fr")
                .label("url-child", "Child Label", "fr")
                .definition("concept1", "Parent Def", "fr")
                .definition("url-child", "Child Def", "fr")
                .build();

        // Set narrower on parent to reference child
        FullInfoDTO parentDto = branchDTO.getData().get("concept1");
        PurlInfoDTO narrower = new PurlInfoDTO();
        narrower.setValue("url-child");
        parentDto.setNarrower(new PurlInfoDTO[]{narrower});

        // Add a related URL on the child to trigger createRelatedLinkAndSetRelatedConcepts
        FullInfoDTO childDto = branchDTO.getData().get("url-child");
        PurlInfoDTO related = new PurlInfoDTO();
        related.setValue("url-related");
        childDto.setRelated(new PurlInfoDTO[]{related});

        when(conceptApi.fetchDownExpansion(config)).thenReturn(branchDTO);

        // No existing concepts -> will be created
        when(conceptRepository.findConceptByExternalIdIgnoreCase(anyString(), anyString())).thenReturn(Optional.empty());

        Concept savedParent = new Concept();
        savedParent.setId(100L);
        savedParent.setExternalId("concept1");
        savedParent.setVocabulary(vocabulary);

        Concept savedChild = new Concept();
        savedChild.setId(101L);
        savedChild.setExternalId("concept-child");
        savedChild.setVocabulary(vocabulary);

        // Related concept that will be fetched and saved
        Concept savedRelated = new Concept();
        savedRelated.setId(102L);
        savedRelated.setExternalId("concept-related");
        savedRelated.setVocabulary(vocabulary);

        // Ensure save returns parent then child (and then related)
        when(conceptRepository.save(any(Concept.class))).thenReturn(savedParent).thenReturn(savedChild).thenReturn(savedRelated);

        when(localizedConceptDataRepository.findByConceptAndLangCode(anyLong(), anyString())).thenReturn(Optional.empty());

        // Mock fetching the related concept info by its URL
        FullInfoDTO fetchedRelated = new FullInfoDTO();
        PurlInfoDTO relatedId = new PurlInfoDTO();
        relatedId.setValue("concept-related");
        fetchedRelated.setIdentifier(new PurlInfoDTO[]{relatedId});
        when(conceptApi.fetchConceptInfoByUri(vocabulary, "url-related")).thenReturn(fetchedRelated);

        // When
        conceptService.saveAllSubConceptOfIfUpdated(config);

        // Then
        verify(conceptChangeEventPublisher, times(1)).publishEvent(config.getFieldCode());
        verify(conceptRepository, atLeast(2)).save(any(Concept.class));
        verify(localizedConceptDataRepository, atLeast(2)).save(any(LocalizedConceptData.class));
        verify(conceptRelatedLinkRepository, atLeast(1)).save(any());
    }

    @Test
    void saveAllSubConceptOfIfUpdated_shouldMarkConceptAsDeleted_whenNotInBranch() throws Exception {
        // Given
        ConceptFieldConfig config = new ConceptFieldConfig();
        config.setId(20L);
        config.setFieldCode("FIELD-DELETE");
        config.setConcept(concept);

        // Build branch with only the parent but with narrower present (so method continues)
        ConceptBranchDTO.ConceptBranchDTOBuilder builder = new ConceptBranchDTO.ConceptBranchDTOBuilder();
        ConceptBranchDTO branchDTO = builder
                .identifier("parent-url", "concept1")
                .identifier("some-child", "concept-child")
                .build();

        FullInfoDTO parentDto = branchDTO.getData().get("parent-url");
        PurlInfoDTO narrower = new PurlInfoDTO();
        narrower.setValue("some-child");
        parentDto.setNarrower(new PurlInfoDTO[]{narrower});

        branchDTO.setParentUrl("parent-url");

        when(conceptApi.fetchDownExpansion(config)).thenReturn(branchDTO);

        // Default behaviour: no existing concepts in repo for branch entries -> will be created
        when(conceptRepository.findConceptByExternalIdIgnoreCase(anyString(), anyString())).thenReturn(Optional.empty());

        // Create a concept that is NOT part of the branch and not deleted
        Concept other = new Concept();
        other.setId(200L);
        other.setExternalId("other-concept");
        other.setVocabulary(vocabulary);
        other.setDeleted(false);

        // Create a label attached to that concept and referenced by the parent concept (field parent)
        ConceptAltLabel alt = new ConceptAltLabel();
        alt.setConcept(other);
        alt.setParentConcept(concept);

        // The service will query labels by parent concept -> return our alt label which refers to 'other'
        when(conceptLabelRepository.findAllByParentConcept(concept)).thenReturn(List.of(alt));

        // When
        when(conceptRepository.save(any(Concept.class))).thenAnswer(i -> i.getArgument(0));
        conceptService.saveAllSubConceptOfIfUpdated(config);

        // Then
        verify(conceptChangeEventPublisher, times(1)).publishEvent(config.getFieldCode());
        // The non-branch concept should be marked deleted and saved
        verify(conceptRepository, atLeastOnce()).save(argThat(c -> c != null && c.isDeleted()));
        // The parent link on the label should be nulled and saved
        verify(conceptLabelRepository, atLeastOnce()).save(argThat(a -> a.getParentConcept() == null));
    }

    @Test
    void saveAllSubConceptOfIfUpdated_shouldNotMarkAlreadyDeletedConcept() throws Exception {
        // Given
        ConceptFieldConfig config = new ConceptFieldConfig();
        config.setId(21L);
        config.setFieldCode("SIATEST");
        config.setConcept(concept);

        // Build branch with only the parent but with narrower present
        ConceptBranchDTO.ConceptBranchDTOBuilder builder = new ConceptBranchDTO.ConceptBranchDTOBuilder();
        ConceptBranchDTO branchDTO = builder
                .identifier("parent-url", "concept1")
                .identifier("some-child", "concept-child")
                .build();

        FullInfoDTO parentDto = branchDTO.getData().get("parent-url");
        PurlInfoDTO narrower = new PurlInfoDTO();
        narrower.setValue("some-child");
        parentDto.setNarrower(new PurlInfoDTO[]{narrower});

        branchDTO.setParentUrl("parent-url");

        when(conceptRepository.save(any(Concept.class))).thenAnswer(i -> i.getArgument(0));
        when(conceptApi.fetchDownExpansion(config)).thenReturn(branchDTO);

        // Create a concept that is part of existing localized data but already deleted
        Concept other = new Concept();
        other.setId(201L);
        other.setExternalId("other-concept-deleted");
        other.setVocabulary(vocabulary);
        other.setDeleted(true);

        LocalizedConceptData lcd = new LocalizedConceptData();
        lcd.setConcept(other);

        // When
        conceptService.saveAllSubConceptOfIfUpdated(config);

        // Then
        verify(conceptChangeEventPublisher, times(1)).publishEvent(config.getFieldCode());
        // No save of concept as deleted because it already was
        verify(conceptRepository, never()).save(argThat(c -> c != null && c.isDeleted()));
    }

    private Map<Long, Concept> prepareConceptsForHierarchy() {
        Map<Long, Concept> concepts = new HashMap<>();

        Vocabulary currentVocabulary = new Vocabulary();
        currentVocabulary.setId(1L);
        currentVocabulary.setExternalVocabularyId("vocab1");

        Concept parentConcept = new Concept();
        parentConcept.setId(10L);
        parentConcept.setExternalId("parentFieldConcept");
        parentConcept.setVocabulary(currentVocabulary);
        concepts.put(10L, parentConcept);

        Concept child1 = new Concept();
        child1.setId(11L);
        child1.setExternalId("concept1");
        child1.setVocabulary(currentVocabulary);
        concepts.put(11L, child1);

        Concept child2 = new Concept();
        child2.setId(12L);
        child2.setExternalId("concept2");
        child2.setVocabulary(currentVocabulary);
        concepts.put(12L, child2);

        Concept child3 = new Concept();
        child3.setId(13L);
        child3.setExternalId("concept3");
        child3.setVocabulary(currentVocabulary);
        concepts.put(13L, child3);

        Concept child4 = new Concept();
        child4.setId(14L);
        child4.setExternalId("concept4");
        child4.setVocabulary(currentVocabulary);
        concepts.put(14L, child4);

        Concept child5 = new Concept();
        child5.setId(15L);
        child5.setExternalId("concept5");
        child5.setVocabulary(currentVocabulary);
        concepts.put(15L, child5);

        Concept child6 = new Concept();
        child6.setId(16L);
        child6.setExternalId("concept6");
        child6.setVocabulary(currentVocabulary);
        concepts.put(16L, child6);

        Concept child8 = new Concept();
        child8.setId(18L);
        child8.setExternalId("concept8");
        child8.setVocabulary(currentVocabulary);
        concepts.put(18L, child8);

        return concepts;
    }

    @Test
    void findParentsOfConceptInField_shouldReturnParents_whenParentsArePresent() {
        Map<Long, Concept> concepts = prepareConceptsForHierarchy();
        Concept finalChild = concepts.get(18L);
        Concept parentField = concepts.get(10L);

        Concept concept5 = concepts.get(15L);
        Concept concept3 = concepts.get(13L);

        ConceptHierarchy h1 = new ConceptHierarchy();
        h1.setId(1L);
        h1.setChild(finalChild);
        h1.setParent(concept5);

        ConceptHierarchy h2 = new ConceptHierarchy();
        h2.setId(2L);
        h2.setChild(concept5);
        h2.setParent(concept3);

        when(conceptHierarchyRepository.findAllByChildAndParentFieldContext(finalChild, parentField))
                .thenReturn(List.of(h1));
        when(conceptHierarchyRepository.findAllByChildAndParentFieldContext(concept5, parentField))
                .thenReturn(List.of(h2));

        List<Concept> results = conceptService.findParentsOfConceptInField(finalChild, parentField);

        assertThat(results)
                .hasSize(2)
                .containsExactly(concept3, concept5);
    }

    @Test
    void findParentsOfConceptInField_shouldEmpty_whenNoParentsBesidesField() {
        Map<Long, Concept> concepts = prepareConceptsForHierarchy();
        Concept finalChild = concepts.get(11L);
        Concept parentField = concepts.get(10L);


        List<Concept> results = conceptService.findParentsOfConceptInField(finalChild, parentField);

        assertThat(results)
                .isEmpty();
    }

    @Test
    void findParentsOfConceptInField_shouldOneHierarchy_whenPolyHierarchy() {
        Map<Long, Concept> concepts = prepareConceptsForHierarchy();
        Concept finalChild = concepts.get(14L);
        Concept parentField = concepts.get(10L);

        Concept concept6 = concepts.get(16L);
        Concept concept3 = concepts.get(13L);

        ConceptHierarchy h3 = new ConceptHierarchy();
        h3.setId(3L);
        h3.setChild(finalChild);
        h3.setParent(concept3);

        ConceptHierarchy h4 = new ConceptHierarchy();
        h4.setId(4L);
        h4.setChild(finalChild);
        h4.setParent(concept6);

        when(conceptHierarchyRepository.findAllByChildAndParentFieldContext(finalChild, parentField))
                .thenReturn(List.of(h3, h4));

        List<Concept> results = conceptService.findParentsOfConceptInField(finalChild, parentField);

        assertThat(results)
                .hasSize(1)
                .containsAnyOf(concept6, concept3);
    }

}
