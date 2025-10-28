package fr.siamois.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.api.dto.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.database.repositories.FieldRepository;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConceptApiTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FieldRepository fieldRepository;

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
        conceptApi = new ConceptApi(requestFactory, fieldRepository);

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
