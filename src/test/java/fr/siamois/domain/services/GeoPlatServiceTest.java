package fr.siamois.domain.services;

import fr.siamois.dto.entity.FullAddress;
import fr.siamois.infrastructure.api.dto.GeoPlatResponse;
import fr.siamois.infrastructure.api.dto.GeoPlatResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoPlatServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GeoPlatService service;

    private URI searched(String query, Map<String, String> filters) {
        when(restTemplate.getForObject(any(URI.class), eq(GeoPlatResponse.class))).thenReturn(new GeoPlatResponse());
        service.search(query, filters);
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(captor.capture(), eq(GeoPlatResponse.class));
        return captor.getValue();
    }

    @Test
    void search_sendsTheDeclaredFiltersToTheService() {
        URI uri = searched("rue de la paix", Map.of("citycode", "69123"));

        assertTrue(uri.getQuery().contains("citycode=69123"));
        assertTrue(uri.getQuery().contains("type=StreetAddress"));
    }

    @Test
    void search_ignoresAFilterTheServiceDoesNotDeclare() {
        URI uri = searched("rue", Map.of("evil", "1", "zipcode", " "));

        assertFalse(uri.getQuery().contains("evil"));
        assertFalse(uri.getQuery().contains("zipcode"));
    }

    @Test
    void search_mapsTheResultsToAddresses() {
        GeoPlatResult result = new GeoPlatResult();
        result.setFulltext("1 rue de la Paix 75002 Paris");
        result.setCity("Paris");
        result.setX(2.33);
        result.setY(48.86);
        GeoPlatResponse response = new GeoPlatResponse();
        response.setResults(List.of(result));
        when(restTemplate.getForObject(any(URI.class), eq(GeoPlatResponse.class))).thenReturn(response);

        List<FullAddress> addresses = service.search("rue");

        assertEquals(1, addresses.size());
        assertEquals("1 rue de la Paix 75002 Paris", addresses.get(0).getLabel());
        assertEquals(2.33, addresses.get(0).getLon());
    }

    @Test
    void search_whenTheServiceFails_isEmpty() {
        when(restTemplate.getForObject(any(URI.class), eq(GeoPlatResponse.class))).thenThrow(new RestClientException("down"));

        assertTrue(service.search("rue", Map.of()).isEmpty());
    }
}
