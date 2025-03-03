package fr.siamois.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exceptions.NotSiamoisThesaurusException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullConceptDTO;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConceptApiTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RequestFactory requestFactory;

    private ConceptApi conceptApi;

    @Mock
    private ObjectMapper mapper;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(requestFactory.buildRestTemplate()).thenReturn(restTemplate);
        conceptApi = new ConceptApi(requestFactory);

        vocabulary = new Vocabulary();
        vocabulary.setBaseUri("http://example.com");
        vocabulary.setExternalVocabularyId("th223");
    }

    @Test
    void fetchConceptsUnderTopTerm() {
        Concept concept = new Concept();
        concept.setExternalId("testId");
        vocabulary = new Vocabulary();
        vocabulary.setBaseUri("http://example.com");
        vocabulary.setExternalVocabularyId("vocabId");
        concept.setVocabulary(vocabulary);

        ConceptBranchDTO expectedBranch = new ConceptBranchDTO();
        FullConceptDTO fullConceptDTO = new FullConceptDTO();
        expectedBranch.addConceptBranchDTO("testUrl", fullConceptDTO);

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"testUrl\": {}}", HttpStatus.OK));

        ConceptBranchDTO result = conceptApi.fetchConceptsUnderTopTerm(concept);

        assertEquals(expectedBranch.getData().size(), result.getData().size());
    }

    @Test
    void fetchConceptInfo() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"testId\": {}}", HttpStatus.OK));

        FullConceptDTO result = conceptApi.fetchConceptInfo(vocabulary, "testId");

        assertNotNull(result);
    }

    @Test
    void fetchFieldsBranch() throws NotSiamoisThesaurusException, IOException {
        String baseInfo = Files.readString(Path.of("src/test/resources/json/topconcept_baseinfo.json"), StandardCharsets.UTF_8);
        String completeInfo = Files.readString(Path.of("src/test/resources/json/topconcept_full.json"), StandardCharsets.UTF_8);

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(baseInfo);

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(completeInfo, HttpStatus.OK));

        ConceptBranchDTO result = conceptApi.fetchFieldsBranch(vocabulary);

        assertNotNull(result);
    }

    @Test
    void fetchConceptInfo_throwJSONException() throws JsonProcessingException {
        conceptApi = new ConceptApi(requestFactory, mapper);

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Not empty", HttpStatus.OK));

        //noinspection unchecked
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        FullConceptDTO result = conceptApi.fetchConceptInfo(vocabulary, "12");

        assertNull(result);
    }

    @Test
    void fetchFieldsBranch_returnNull_whenVocabNotFound() throws NotSiamoisThesaurusException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(null);

        ConceptBranchDTO result = conceptApi.fetchFieldsBranch(vocabulary);

        assertNull(result);
    }

    @Test
    void fetchFieldsBranch_throws_whenThesauIsNotSiamois() throws JsonProcessingException {
        conceptApi = new ConceptApi(requestFactory, mapper);

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("NOT EMPTY");

        ConceptApi.ConceptDTO dto = new ConceptApi.ConceptDTO();
        dto.idConcept = "12";
        dto.labels = new LabelDTO[]{new LabelDTO()};

        when(mapper.readValue(anyString(), eq(ConceptApi.ConceptDTO[].class))).thenReturn(new ConceptApi.ConceptDTO[] { dto });

        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Not empty", HttpStatus.OK));

        //noinspection unchecked
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        assertThrows(NotSiamoisThesaurusException.class, () -> conceptApi.fetchFieldsBranch(vocabulary));
    }

    @Test
    void fetchFieldsBranch_throws_whenJsonException() throws JsonProcessingException, NotSiamoisThesaurusException {
        conceptApi = new ConceptApi(requestFactory, mapper);
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("NOT EMPTY");

        when(mapper.readValue(anyString(), eq(ConceptApi.ConceptDTO[].class))).thenThrow(JsonProcessingException.class);

        ConceptBranchDTO result = conceptApi.fetchFieldsBranch(vocabulary);

        assertNull(result);
    }

}