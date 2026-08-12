package fr.siamois.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptApiCollectionDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptRemoteAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.FieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConceptApiTest {

    private static final URI COLLECTION_BRANCH_URI =
            URI.create("http://example.com/openapi/v1/group/th223/branch?idGroups=g1");

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FieldRepository fieldRepository;

    @Mock
    private ConceptCollectionRepository conceptCollectionRepository;

    @Mock
    private RequestFactory requestFactory;

    private ConceptApi conceptApi;

    @Mock
    private ObjectMapper mapper;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(requestFactory.buildRestTemplate(true)).thenReturn(restTemplate);
        conceptApi = new ConceptApi(requestFactory, fieldRepository, conceptCollectionRepository);

        vocabulary = new Vocabulary();
        vocabulary.setBaseUri("http://example.com");
        vocabulary.setExternalVocabularyId("th223");
    }

    @Test
    void fetchConceptInfo() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"testId\": {}}", HttpStatus.OK));

        FullInfoDTO result = conceptApi.fetchConceptInfo(vocabulary, "testId");

        assertNotNull(result);
    }

    @Test
    void fetchFieldsBranch() throws NotSiamoisThesaurusException, IOException, ErrorProcessingExpansionException {
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

        FullInfoDTO result = conceptApi.fetchConceptInfo(vocabulary, "12");

        assertNull(result);
    }

    @Test
    void fetchFieldsBranch_returnNull_whenVocabNotFound() throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
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
    void fetchFieldsBranch_throws_whenJsonException() throws JsonProcessingException {
        conceptApi = new ConceptApi(restTemplate);
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("NOT EMPTY");

        when(mapper.readValue(anyString(), eq(ConceptApi.ConceptDTO[].class))).thenThrow(JsonProcessingException.class);

        assertThrows(ErrorProcessingExpansionException.class, () -> conceptApi.fetchFieldsBranch(vocabulary));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchDownExpansion_shouldHandleJsonProcessingException() throws JsonProcessingException {
        // Arrange
        URI uri = URI.create("http://example.com/openapi/v1/concept/th223/testId/expansion?way=down");
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Invalid JSON", HttpStatus.OK));
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        assertThrows(ErrorProcessingExpansionException.class, () -> conceptApi.fetchDownExpansion(vocabulary, "testId"));
    }

    @Test
    void fetchDownExpansion_bodyNull_returnsNull() throws ErrorProcessingExpansionException, NoSuchFieldException, IllegalAccessException {
        // Arrange
        ConceptFieldConfig config = new ConceptFieldConfig();
        fr.siamois.domain.models.vocabulary.Concept concept = new fr.siamois.domain.models.vocabulary.Concept.Builder()
                .externalId("testId")
                .vocabulary(vocabulary)
                .id(1L)
                .build();
        config.setConcept(concept);
        config.setId(1L);

        URI uri = URI.create("http://example.com/openapi/v1/concept/th223/testId/expansion?way=down");
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // inject mocked mapper into conceptApi (public ctor sets fieldRepository)
        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        // Act
        ConceptBranchDTO result = conceptApi.fetchDownExpansion(config);

        // Assert
        assertNull(result);
        verify(fieldRepository, never()).updateChecksumForFieldConfig(anyLong(), anyString());
    }

    @Test
    void fetchDownExpansion_sameChecksum_returnsNull() throws Exception {
        // Arrange
        String body = "Same content for checksum";
        String checksum = sha3Hex(body);

        ConceptFieldConfig config = new ConceptFieldConfig();
        fr.siamois.domain.models.vocabulary.Concept concept = new fr.siamois.domain.models.vocabulary.Concept.Builder()
                .externalId("testId")
                .vocabulary(vocabulary)
                .id(2L)
                .build();
        config.setConcept(concept);
        config.setId(2L);
        config.setExistingHash(checksum);

        URI uri = URI.create("http://example.com/openapi/v1/concept/th223/testId/expansion?way=down");
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        // Act
        ConceptBranchDTO result = conceptApi.fetchDownExpansion(config);

        // Assert
        assertNull(result);
        verify(fieldRepository, never()).updateChecksumForFieldConfig(anyLong(), anyString());
    }

    @Test
    void fetchDownExpansion_differentChecksum_updatesAndReturnsBranch() throws Exception {
        // Arrange
        String body = "{\"k\":{}}";
        String expectedChecksum = sha3Hex(body);

        ConceptFieldConfig config = new ConceptFieldConfig();
        fr.siamois.domain.models.vocabulary.Concept concept = new fr.siamois.domain.models.vocabulary.Concept.Builder()
                .externalId("testId")
                .vocabulary(vocabulary)
                .id(3L)
                .build();
        config.setConcept(concept);
        config.setId(3L);
        config.setExistingHash("different-old-checksum");

        URI uri = URI.create("http://example.com/openapi/v1/concept/th223/testId/expansion?way=down");
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        // Mock mapper to return a map with one FullInfoDTO entry
        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("k", new FullInfoDTO()));

        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        // Act
        ConceptBranchDTO result = conceptApi.fetchDownExpansion(config);

        // Assert
        assertNotNull(result);
        verify(fieldRepository, times(1)).updateChecksumForFieldConfig(config.getId(), expectedChecksum);
    }

    @Test
    void fetchDownExpansion_throws_whenJsonException() throws Exception {
        // Arrange
        String body = "invalid json";

        ConceptFieldConfig config = new ConceptFieldConfig();
        fr.siamois.domain.models.vocabulary.Concept concept = new fr.siamois.domain.models.vocabulary.Concept.Builder()
                .externalId("testId")
                .vocabulary(vocabulary)
                .id(4L)
                .build();
        config.setConcept(concept);
        config.setId(4L);

        URI uri = URI.create("http://example.com/openapi/v1/concept/th223/testId/expansion?way=down");
        when(restTemplate.exchange(eq(uri), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        // mapper throws JSON exception
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("err"){});

        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        // Act / Assert
        assertThrows(ErrorProcessingExpansionException.class, () -> conceptApi.fetchDownExpansion(config));
    }

    @Test
    void fetchConceptInfoByUri_withArk_returnsDTO() throws Exception {
        // Arrange
        String arkUri = "http://other.example/ark:12345/abc";
        URI expected = URI.create("http://example.com/openapi/v1/concept/ark:12345/abc");

        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"k\":{}}", HttpStatus.OK));

        // inject mocked mapper
        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("k", new FullInfoDTO()));

        // Act
        FullInfoDTO result = conceptApi.fetchConceptInfoByUri(vocabulary, arkUri);

        // Assert
        assertNotNull(result);
        verify(restTemplate, times(1)).exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void fetchConceptInfoByUri_withQueryParams_returnsDTO() throws Exception {
        // Arrange
        String uriStr = "http://host.example/path?idt=th223&idc=testId";
        URI expected = URI.create("http://example.com/openapi/v1/concept/th223/testId");

        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"x\":{}}", HttpStatus.OK));

        // inject mocked mapper
        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("x", new FullInfoDTO()));

        // Act
        FullInfoDTO result = conceptApi.fetchConceptInfoByUri(vocabulary, uriStr);

        // Assert
        assertNotNull(result);
        verify(restTemplate, times(1)).exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void fetchConceptInfoByUri_returnsNull_whenJsonProcessingException() throws Exception {
        // Arrange
        String uriStr = "http://host.example/path?idt=th223&idc=testId";
        URI expected = URI.create("http://example.com/openapi/v1/concept/th223/testId");

        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{not json}", HttpStatus.OK));

        // inject mocked mapper that throws
        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        //noinspection unchecked
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("err"){});

        // Act
        FullInfoDTO result = conceptApi.fetchConceptInfoByUri(vocabulary, uriStr);

        // Assert
        assertNull(result);
        verify(restTemplate, times(1)).exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void fetchConceptInfoByUri_returnsNull_whenEmptyMap() throws Exception {
        // Arrange
        String uriStr = "http://host.example/path?idt=th223&idc=testId";
        URI expected = URI.create("http://example.com/openapi/v1/concept/th223/testId");

        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"empty\":{}}", HttpStatus.OK));

        // inject mocked mapper returning empty map
        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);

        when(mapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of());

        // Act
        FullInfoDTO result = conceptApi.fetchConceptInfoByUri(vocabulary, uriStr);

        // Assert
        assertNull(result);
        verify(restTemplate, times(1)).exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class));
    }

    // --- fetchCollectionBranch -----------------------------------------------------------------

    @Test
    void fetchCollectionBranch_throws_whenBodyIsNull() {
        ConceptCollection collection = collection("g1", null);

        when(restTemplate.exchange(eq(COLLECTION_BRANCH_URI), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(ErrorProcessingExpansionException.class, () -> conceptApi.fetchCollectionBranch(vocabulary, collection));
        verifyNoInteractions(conceptCollectionRepository);
    }

    @Test
    void fetchCollectionBranch_returnsNull_whenCollectionHasNotChanged() throws Exception {
        String body = "{\"k\":{}}";
        ConceptCollection collection = collection("g1", sha3Hex(body));

        when(restTemplate.exchange(eq(COLLECTION_BRANCH_URI), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        assertNull(conceptApi.fetchCollectionBranch(vocabulary, collection));
        verify(conceptCollectionRepository, never()).save(any());
    }

    @Test
    void fetchCollectionBranch_updatesChecksumAndReturnsBranch_whenCollectionChanged() throws Exception {
        String body = "{\"http://example.com/concept/th223/12\":{}}";
        ConceptCollection collection = collection("g1", "an-outdated-checksum");

        when(restTemplate.exchange(eq(COLLECTION_BRANCH_URI), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ConceptBranchDTO result = conceptApi.fetchCollectionBranch(vocabulary, collection);

        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals(sha3Hex(body), collection.getExistingHash());
        verify(conceptCollectionRepository, times(1)).save(collection);
    }

    @Test
    void fetchCollectionBranch_updatesChecksumAndReturnsBranch_whenCollectionWasNeverFetched() throws Exception {
        String body = "{\"http://example.com/concept/th223/12\":{}}";
        ConceptCollection collection = collection("g1", null);

        when(restTemplate.exchange(eq(COLLECTION_BRANCH_URI), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        assertNotNull(conceptApi.fetchCollectionBranch(vocabulary, collection));
        assertEquals(sha3Hex(body), collection.getExistingHash());
        verify(conceptCollectionRepository, times(1)).save(collection);
    }

    @Test
    void fetchCollectionBranch_throws_whenJsonException() throws Exception {
        ConceptCollection collection = collection("g1", null);

        when(restTemplate.exchange(eq(COLLECTION_BRANCH_URI), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("invalid json", HttpStatus.OK));

        injectMockedMapper();
        //noinspection unchecked
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        assertThrows(ErrorProcessingExpansionException.class, () -> conceptApi.fetchCollectionBranch(vocabulary, collection));
    }

    // --- fetchRemoteAutocomplete ---------------------------------------------------------------

    @Test
    void fetchRemoteAutocomplete_returnsEveryMatchingLabel() throws JsonProcessingException {
        URI expected = URI.create("http://example.com/openapi/v1/concept/th223/autocomplete/cera?full=true");
        String body = """
                [
                  {"identifier": 12, "uri": "http://example.com/?idc=12&idt=th223", "label": "Céramique", "isAltLabel": false, "definition": "Objet en terre cuite"},
                  {"identifier": 12, "uri": "http://example.com/?idc=12&idt=th223", "label": "Poterie", "isAltLabel": true, "definition": null}
                ]""";

        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        List<ConceptRemoteAutocompleteDTO> results = conceptApi.fetchRemoteAutocomplete("http://example.com", "th223", "cera");

        assertEquals(2, results.size());
        assertEquals(12L, results.get(0).identifier());
        assertEquals("Céramique", results.get(0).label());
        assertEquals("Objet en terre cuite", results.get(0).definition());
        assertEquals(Boolean.FALSE, results.get(0).isAltLabel());
        assertEquals(Boolean.TRUE, results.get(1).isAltLabel());
    }

    @Test
    void fetchRemoteAutocomplete_propagatesJsonException() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{not json}", HttpStatus.OK));

        injectMockedMapper();
        //noinspection unchecked
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        // an unreadable answer is not "no match" : it must reach the caller, which turns it into an
        // error message instead of an empty suggestion list
        assertThrows(JsonProcessingException.class,
                () -> conceptApi.fetchRemoteAutocomplete("http://example.com", "th223", "cera"));
    }

    @Test
    void fetchRemoteAutocomplete_encodesWhatTheUserTyped() throws JsonProcessingException {
        // the input reaches the API as typed : a raw space would make URI.create throw
        URI expected = URI.create("https://thesaurus.example/opentheso/openapi/v1/concept/th223/autocomplete/c%C3%A9ram%20fine?full=true");
        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));

        assertTrue(conceptApi.fetchRemoteAutocomplete("https://thesaurus.example/opentheso", "th223", "céram fine").isEmpty());

        verify(restTemplate).exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class));
    }

    // --- fetchPublicCollections ----------------------------------------------------------------

    @Test
    void fetchPublicCollections_returnsEveryCollectionWithItsLabels() {
        URI expected = URI.create("http://example.com/openapi/v1/group/th223");
        String body = """
                [
                  {"idGroup": "g1", "labels": [{"lang": "fr", "title": "Céramique"}, {"lang": "en", "title": "Pottery"}]},
                  {"idGroup": "g2", "labels": []}
                ]""";

        when(restTemplate.exchange(eq(expected), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        VocabularyDTO vocabularyDTO = VocabularyDTO.builder()
                .baseUri("http://example.com")
                .externalVocabularyId("th223")
                .build();

        List<ConceptApiCollectionDTO> results = conceptApi.fetchPublicCollections(vocabularyDTO);

        assertEquals(2, results.size());
        assertEquals("g1", results.get(0).idGroup());
        assertEquals(2, results.get(0).labels().size());
        assertEquals("Céramique", results.get(0).labels().get(0).getTitle());
        assertEquals("fr", results.get(0).labels().get(0).getLang());
        assertEquals("g2", results.get(1).idGroup());
        assertTrue(results.get(1).labels().isEmpty());
    }

    @Test
    void fetchPublicCollections_returnsEmptyList_whenJsonException() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{not json}", HttpStatus.OK));

        injectMockedMapper();
        //noinspection unchecked
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);

        VocabularyDTO vocabularyDTO = VocabularyDTO.builder()
                .baseUri("http://example.com")
                .externalVocabularyId("th223")
                .build();

        assertTrue(conceptApi.fetchPublicCollections(vocabularyDTO).isEmpty());
    }

    private ConceptCollection collection(String externalId, String existingHash) {
        ConceptCollection collection = new ConceptCollection();
        collection.setId(1L);
        collection.setExternalId(externalId);
        collection.setVocabulary(vocabulary);
        collection.setExistingHash(existingHash);
        return collection;
    }

    /**
     * Replaces the mapper built by the constructor, so JSON failures can be simulated.
     */
    private void injectMockedMapper() throws NoSuchFieldException, IllegalAccessException {
        java.lang.reflect.Field mapperField = ConceptApi.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(conceptApi, mapper);
    }

    private static String sha3Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA3-256");
        byte[] hash = digest.digest(input.getBytes());
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }



}
