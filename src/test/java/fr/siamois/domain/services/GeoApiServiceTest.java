package fr.siamois.domain.services;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.api.dto.geoapi.CommuneListResponse;
import fr.siamois.mapper.ConceptMapper;
import org.junit.jupiter.api.BeforeEach;
import fr.siamois.infrastructure.api.dto.geoapi.CommuneResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(MockitoExtension.class)
class GeoApiServiceTest {

    @Mock
    private ConceptService conceptService;

    @Mock
    private ConceptMapper conceptMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GeoApiService geoApiService;

    private Concept mockConcept;
    private ConceptDTO mockConceptDTO;

    @BeforeEach
    void setUp() {
        mockConcept = new Concept();
        mockConcept.setId(417L);

        mockConceptDTO = new ConceptDTO();
        mockConceptDTO.setId(45);
    }

    @Test
    void fetchCommunes_ShouldReturnEmptyList_WhenInputTooShort() {
        // Act
        List<PlaceSuggestionDTO> result = geoApiService.fetchCommunes("Pa");

        // Assert
        assertTrue(result.isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fetchCommunes_ShouldReturnSuggestions_WhenApiResponds() {
        // Arrange
        String input = "Lyon";

        CommuneListResponse apiResponse = new CommuneListResponse();
        CommuneResponse paris = new CommuneResponse();
        paris.setNom("Paris");
        paris.setCode("75056");
        apiResponse.add(paris);

        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://geo.api.gouv.fr/communes")
                .queryParam("nom", input)
                .queryParam("limit", 10)
                .build()
                .encode()
                .toUri();

        when(restTemplate.getForObject(uri, CommuneListResponse.class))
                .thenReturn(apiResponse);

        when(conceptService.findById(417)).thenReturn(Optional.of(mockConcept));
        when(conceptMapper.convert(mockConcept)).thenReturn(mockConceptDTO);

        // Act
        List<PlaceSuggestionDTO> result = geoApiService.fetchCommunes(input);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        PlaceSuggestionDTO suggestion = result.get(0);
        assertEquals("Paris", suggestion.getName());
        assertEquals("75056", suggestion.getCode());

        verify(restTemplate).getForObject(any(URI.class), eq(CommuneListResponse.class));
    }

    @Test
    void fetchCommunes_ShouldReturnEmpty_WhenApiResponseIsNull() {
        // Arrange
        when(restTemplate.getForObject(any(URI.class), eq(CommuneListResponse.class)))
                .thenReturn(null);

        // Act
        List<PlaceSuggestionDTO> result = geoApiService.fetchCommunes("Nantes");

        // Assert
        assertTrue(result.isEmpty());
    }
}