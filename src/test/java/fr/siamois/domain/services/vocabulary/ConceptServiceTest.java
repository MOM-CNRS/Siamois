package fr.siamois.domain.services.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.domain.events.publisher.ConceptChangeEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.PurlInfoDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptRemoteAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
    private ConceptChangeEventPublisher conceptChangeEventPublisher;

    @Mock
    private ConceptLabelRepository conceptLabelRepository;

    @Mock
    private ConceptHierarchyRepository conceptHierarchyRepository;

    @Mock
    private ApplicationContext applicationContext;

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
        conceptService.saveAllSubConceptOfIfUpdated(config, new ProgressWrapper());

        // Then
        verify(conceptChangeEventPublisher, never()).publishEvent(anyString());
        verify(conceptRepository, never()).save(any(Concept.class));
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



    @Test
    void testGetLocalizedConceptDataByConceptAndLangCode_Found() {
        // given
        LocalizedConceptData localizedConceptData;

        localizedConceptData = new LocalizedConceptData();
        localizedConceptData.setLangCode("en");
        localizedConceptData.setConcept(concept);
        when(localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), "en"))
                .thenReturn(Optional.of(localizedConceptData));

        // when
        LocalizedConceptData result = conceptService.getLocalizedConceptDataByConceptAndLangCode(concept, "en");

        // then
        assertNotNull(result);
        assertEquals("en", result.getLangCode());
        assertEquals(concept, result.getConcept());
        verify(localizedConceptDataRepository, times(1))
                .findByConceptAndLangCode(concept.getId(), "en");
    }

    @Test
    void testGetLocalizedConceptDataByConceptAndLangCode_NotFound() {
        // given
        LocalizedConceptData localizedConceptData;

        localizedConceptData = new LocalizedConceptData();
        localizedConceptData.setLangCode("en");
        localizedConceptData.setConcept(concept);
        when(localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), "fr"))
                .thenReturn(Optional.empty());

        // when
        LocalizedConceptData result = conceptService.getLocalizedConceptDataByConceptAndLangCode(concept, "fr");

        // then
        assertNull(result);
        verify(localizedConceptDataRepository, times(1))
                .findByConceptAndLangCode(concept.getId(), "fr");
    }

    private VocabularyDTO remoteVocabulary() {
        return VocabularyDTO.builder()
                .baseUri("http://example.org")
                .externalVocabularyId("th1")
                .build();
    }

    // --- the concept a pasted URL designates ----------------------------------------------------

    private FullInfoDTO conceptInfo(String prefLabel) {
        FullInfoDTO info = new FullInfoDTO();
        PurlInfoDTO label = new PurlInfoDTO();
        label.setValue(prefLabel);
        label.setLang("fr");
        info.setPrefLabel(new PurlInfoDTO[]{label});
        return info;
    }

    @Test
    void fetchConceptDesignatedBy_shouldBeEmpty_whenTheArkResolvesToNoCanonicalId() {
        // the ark itself does not encode the thesaurus's numeric id (e.g. ark:/26678/pcrtp9tsh62g34
        // resolves to 266341 on Pactols) : it must never be saved as external_id, or the same concept
        // designated once by ark and once by idc ends up as two different local Concept rows
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchConceptInfoByUri("http://example.org", "http://example.org/ark:/26678/pcrtREVS9rPi7K"))
                .thenReturn(conceptInfo("Phase chronologique"));

        Optional<ConceptAutocompleteDetachedDTO> result =
                conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/ark:/26678/pcrtREVS9rPi7K");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchConceptDesignatedBy_shouldResolveTheArkToTheThesaurusCanonicalId_whenOneIsReturned() {
        // a concept already known locally under its numeric id must be found again when designated by
        // its ark, instead of creating a second, unrelated Concept for the same real-world concept
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        FullInfoDTO info = conceptInfo("Phase chronologique");
        info.setIdentifier(new PurlInfoDTO[]{purl("266341", "")});
        when(conceptApi.fetchConceptInfoByUri("http://example.org", "http://example.org/ark:/26678/pcrtREVS9rPi7K"))
                .thenReturn(info);

        Optional<ConceptAutocompleteDetachedDTO> result =
                conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/ark:/26678/pcrtREVS9rPi7K");

        assertThat(result).isPresent();
        assertThat(result.get().concept().getExternalId()).isEqualTo("266341");
    }

    @Test
    void fetchConceptDesignatedBy_shouldPreferTheLanguageOfTheUser_andCarryTheOtherLabels() {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        FullInfoDTO info = new FullInfoDTO();
        info.setPrefLabel(new PurlInfoDTO[]{purl("Phase chronologique", "fr"), purl("Chronological phase", "en")});
        info.setAltLabel(new PurlInfoDTO[]{purl("Période", "fr")});
        info.setDefinition(new PurlInfoDTO[]{purl("Découpage du temps", "fr")});
        when(conceptApi.fetchConceptInfoByUri(anyString(), anyString())).thenReturn(info);

        UserInfo userInfo = new UserInfo(new InstitutionDTO(), new PersonDTO(), "en");
        ExecutionContextHolder.set(userInfo);
        try {
            Optional<ConceptAutocompleteDetachedDTO> result =
                    conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/?idc=12&idt=th1");

            assertThat(result).isPresent();
            assertThat(result.get().getOriginalPrefLabel()).isEqualTo("Chronological phase");
            assertThat(result.get().getAltLabels()).containsExactly("Période");
            // the definition has no english version : the only one the thesaurus returned stands for it
            assertThat(result.get().getDefinition()).isEqualTo("Découpage du temps");
        } finally {
            ExecutionContextHolder.clear();
        }
    }

    private PurlInfoDTO purl(String value, String lang) {
        PurlInfoDTO purl = new PurlInfoDTO();
        purl.setValue(value);
        purl.setLang(lang);
        return purl;
    }

    @Test
    void fetchConceptDesignatedBy_shouldReadTheConceptOfAnIdcUrl() {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchConceptInfoByUri("http://example.org", "http://example.org/?idc=12&idt=th1"))
                .thenReturn(conceptInfo("Céramique"));

        Optional<ConceptAutocompleteDetachedDTO> result =
                conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/?idc=12&idt=th1");

        assertThat(result).isPresent();
        assertThat(result.get().concept().getExternalId()).isEqualTo("12");
    }

    @Test
    void fetchConceptDesignatedBy_shouldBeEmpty_whenTheUrlDesignatesTheThesaurusOnly() {
        Optional<ConceptAutocompleteDetachedDTO> result =
                conceptService.fetchConceptDesignatedBy(remoteVocabulary(), "http://example.org/?idt=th1");

        assertThat(result).isEmpty();
        verifyNoInteractions(conceptApi);
    }

    @Test
    void fetchConceptDesignatedBy_shouldBeEmpty_whenTheThesaurusDoesNotKnowThatArk() {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        // an ark of another naan, that only its own resolver understands : the thesaurus answers 404
        when(conceptApi.fetchConceptInfoByUri(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThat(conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/ark:/26678/unknown")).isEmpty();
    }

    @Test
    void fetchConceptDesignatedBy_shouldBeEmpty_whenTheConceptCarriesNoLabel() {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        FullInfoDTO info = new FullInfoDTO();
        info.setPrefLabel(new PurlInfoDTO[0]);
        when(conceptApi.fetchConceptInfoByUri(anyString(), anyString())).thenReturn(info);

        assertThat(conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/?idc=12&idt=th1")).isEmpty();
    }

    @Test
    void fetchConceptDesignatedBy_shouldBeEmpty_whenTheThesaurusReturnsNoConcept() {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchConceptInfoByUri(anyString(), anyString())).thenReturn(null);

        assertThat(conceptService.fetchConceptDesignatedBy(vocabularyDTO, "http://example.org/?idc=12&idt=th1")).isEmpty();
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldReturnOneResultPerMatchingLabel_sharingTheConceptData() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete("http://example.org", "th1", "céram", null)).thenReturn(List.of(
                new ConceptRemoteAutocompleteDTO(1L, "http://example.org/?idc=12&idt=th1", "Céramique", false, "Objet en terre cuite"),
                new ConceptRemoteAutocompleteDTO(1L, "http://example.org/?idc=12&idt=th1", "Poterie", true, "Objet en terre cuite")
        ));

        List<ConceptAutocompleteDetachedDTO> results = conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "céram");

        assertThat(results).hasSize(2)
                .allSatisfy(result -> {
            assertThat(result.getOriginalPrefLabel()).isEqualTo("Céramique");
            assertThat(result.getAltLabels()).containsExactly("Poterie");
            assertThat(result.getDefinition()).isEqualTo("Objet en terre cuite");
            assertThat(result.getHierarchyPrefLabels()).isEmpty();
            assertThat(result.getVocabularyUri()).isEqualTo("http://example.org?idt=th1");
            assertThat(result.concept().getExternalId()).isEqualTo("1");
            assertThat(result.concept().getVocabulary()).isEqualTo(vocabularyDTO);
        });
        assertThat(results.get(0).getConceptLabelToDisplay().isAltLabel()).isFalse();
        assertThat(results.get(0).getConceptLabelToDisplay().getLabel()).isEqualTo("Céramique");
        assertThat(results.get(1).getConceptLabelToDisplay().isAltLabel()).isTrue();
        assertThat(results.get(1).getConceptLabelToDisplay().getLabel()).isEqualTo("Poterie");
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldKeepTheConceptsApart_andPreserveTheRemoteOrder() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn(List.of(
                new ConceptRemoteAutocompleteDTO(2L, "http://example.org/?idc=20&idt=th1", "Verre", false, null),
                new ConceptRemoteAutocompleteDTO(1L, "http://example.org/?idc=12&idt=th1", "Céramique", false, "Objet en terre cuite")
        ));

        List<ConceptAutocompleteDetachedDTO> results = conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "e");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).concept().getExternalId()).isEqualTo("2");
        assertThat(results.get(0).getAltLabels()).isEmpty();
        // a missing definition becomes an empty string, as the local autocomplete does
        assertThat(results.get(0).getDefinition()).isEmpty();
        assertThat(results.get(1).concept().getExternalId()).isEqualTo("1");
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldFallBackOnTheAltLabel_whenThePrefLabelDoesNotMatch() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn(List.of(
                new ConceptRemoteAutocompleteDTO(1L, "http://example.org/?idc=12&idt=th1", "Poterie", true, null)
        ));

        List<ConceptAutocompleteDetachedDTO> results = conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "pot");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOriginalPrefLabel()).isEqualTo("Poterie");
        assertThat(results.get(0).getConceptLabelToDisplay().isAltLabel()).isTrue();
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldUseTheNumericIdentifier_regardlessOfTheUriFormat() throws JsonProcessingException {
        // the thesaurus's "identifier" field is the canonical numeric id : it must be used even when
        // the concept's uri is an ark, which does not encode that id (ark:/26678/pcrtp9tsh62g34
        // resolves to 266341 on Pactols, nothing in the ark string itself says so)
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn(List.of(
                new ConceptRemoteAutocompleteDTO(266341L, "http://example.org/ark:/12345/ab6789", "Céramique", false, null)
        ));

        List<ConceptAutocompleteDetachedDTO> results = conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "céram");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).concept().getExternalId()).isEqualTo("266341");
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldIgnoreResults_whenTheConceptCannotBeIdentified() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn(List.of(
                new ConceptRemoteAutocompleteDTO(null, "http://example.org/?idt=th1", "Sans identifiant", false, null),
                new ConceptRemoteAutocompleteDTO(2L, "http://example.org/?idc=20&idt=th1", "Verre", false, null)
        ));

        List<ConceptAutocompleteDetachedDTO> results = conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "e");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).concept().getExternalId()).isEqualTo("2");
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldReturnEmptyList_whenTheThesaurusReturnsNothing() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn(List.of());

        assertThat(conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "céram")).isEmpty();
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldNotQueryTheThesaurus_whenInputIsNullOrBlank() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();

        assertThat(conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, null)).isEmpty();
        assertThat(conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "   ")).isEmpty();

        verifyNoInteractions(conceptApi);
    }

    @Test
    void fetchAutocompleteFromRemoteThesaurus_shouldSkipBlankDefinitions_whenLookingForTheConceptDefinition() throws JsonProcessingException {
        VocabularyDTO vocabularyDTO = remoteVocabulary();
        when(conceptApi.fetchRemoteAutocomplete(anyString(), anyString(), anyString(), nullable(String.class))).thenReturn(List.of(
                new ConceptRemoteAutocompleteDTO(1L, "http://example.org/?idc=12&idt=th1", "Céramique", false, "   "),
                new ConceptRemoteAutocompleteDTO(1L, "http://example.org/?idc=12&idt=th1", "Poterie", true, "Objet en terre cuite")
        ));

        List<ConceptAutocompleteDetachedDTO> results = conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "céram");

        // a blank definition carries no information, the first meaningful one stands for the concept
        assertThat(results).hasSize(2)
                .allSatisfy(result -> assertThat(result.getDefinition()).isEqualTo("Objet en terre cuite"));
    }

    // --- Deferred loading of related concepts -------------------------------------------------

    private Concept relatedStub(Long id, String uri) {
        Concept stub = new Concept();
        stub.setId(id);
        stub.setVocabulary(vocabulary);
        stub.setUri(uri);
        stub.setLoaded(false);
        return stub;
    }

    private FullInfoDTO conceptInfo(String externalId, String label) {
        FullInfoDTO info = new FullInfoDTO();
        PurlInfoDTO identifier = new PurlInfoDTO();
        identifier.setValue(externalId);
        info.setIdentifier(new PurlInfoDTO[]{identifier});
        PurlInfoDTO prefLabel = new PurlInfoDTO();
        prefLabel.setLang("fr");
        prefLabel.setValue(label);
        info.setPrefLabel(new PurlInfoDTO[]{prefLabel});
        return info;
    }

    @Test
    void loadUnloadedRelatedConceptsOf_shouldFetchEachStubAndTagItsLabelsWithTheFieldContext() {
        Concept stub = relatedStub(50L, "http://example.com/?idt=vocab1&idc=4242");
        when(conceptRepository.findUnloadedRelatedConceptsOf(1L)).thenReturn(List.of(stub));
        when(conceptApi.fetchConceptInfoByUri(vocabulary, stub.getUri())).thenReturn(conceptInfo("4242", "Céramique"));
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        conceptService.loadUnloadedRelatedConceptsOf(concept, concept);

        ArgumentCaptor<Concept> savedCaptor = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isLoaded()).isTrue();
        assertThat(savedCaptor.getValue().getExternalId()).isEqualTo("4242");
        // Without the field context the concept would stay invisible in the related autocomplete
        verify(labelService).updateLabel(stub, "fr", "Céramique", concept);
    }

    @Test
    void loadUnloadedRelatedConceptsOf_shouldDoNothing_whenEveryRelatedConceptIsAlreadyLoaded() {
        when(conceptRepository.findUnloadedRelatedConceptsOf(1L)).thenReturn(List.of());

        conceptService.loadUnloadedRelatedConceptsOf(concept, concept);

        verifyNoInteractions(conceptApi);
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void loadUnloadedRelatedConceptsOf_shouldLeaveTheStubUnloaded_whenTheThesaurusIsUnreachable() {
        Concept stub = relatedStub(50L, "http://example.com/?idt=vocab1&idc=4242");
        when(conceptRepository.findUnloadedRelatedConceptsOf(1L)).thenReturn(List.of(stub));
        when(conceptApi.fetchConceptInfoByUri(vocabulary, stub.getUri()))
                .thenThrow(new ResourceAccessException("thesaurus down"));

        conceptService.loadUnloadedRelatedConceptsOf(concept, concept);

        // One unreachable concept must not take down the autocomplete of every other candidate
        assertThat(stub.isLoaded()).isFalse();
        verify(conceptRepository, never()).save(any(Concept.class));
        verifyNoInteractions(labelService);
    }

    @Test
    void loadUnloadedRelatedConceptsOf_shouldLeaveTheStubUnloaded_whenTheThesaurusAnswersWithoutIdentifier() {
        Concept stub = relatedStub(50L, "http://example.com/?idt=vocab1&idc=4242");
        when(conceptRepository.findUnloadedRelatedConceptsOf(1L)).thenReturn(List.of(stub));
        when(conceptApi.fetchConceptInfoByUri(vocabulary, stub.getUri())).thenReturn(null);

        conceptService.loadUnloadedRelatedConceptsOf(concept, concept);

        assertThat(stub.isLoaded()).isFalse();
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void loadUnloadedRelatedConceptsOf_shouldSkipAStubWithoutUri_ratherThanFetchIt() {
        Concept stub = relatedStub(50L, null);
        when(conceptRepository.findUnloadedRelatedConceptsOf(1L)).thenReturn(List.of(stub));

        conceptService.loadUnloadedRelatedConceptsOf(concept, concept);

        verifyNoInteractions(conceptApi);
        verify(conceptRepository, never()).save(any(Concept.class));
    }

    @Test
    void loadUnloadedRelatedConceptsOf_shouldDoNothing_whenTheBaseValueIsNotSavedYet() {
        Concept unsaved = new Concept();
        unsaved.setVocabulary(vocabulary);

        conceptService.loadUnloadedRelatedConceptsOf(unsaved, concept);

        verify(conceptRepository, never()).findUnloadedRelatedConceptsOf(any());
        verifyNoInteractions(conceptApi);
    }

}
