package fr.siamois.domain.services;

import fr.siamois.dto.entity.FullAddress;
import fr.siamois.infrastructure.api.dto.GeoPlatResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
@AllArgsConstructor
@Service
public class GeoPlatService {

    private static final String BASE_URL =
            "https://data.geopf.fr/geocodage/completion";

    private final RestTemplate restTemplate = new RestTemplate();



    public List<FullAddress> search(String query) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL)
                    .queryParam("text", query)
                    .queryParam("maximumResponses", 7)
                    .queryParam("type", "StreetAddress")
                    .build()
                    .encode()
                    .toUri();

            GeoPlatResponse response = restTemplate.getForObject(uri, GeoPlatResponse.class);

            if (response == null || response.getResults() == null) {
                return Collections.emptyList();
            }

            return response.getResults().stream()
                    .map(r -> {
                        FullAddress a = new FullAddress();
                        a.setLabel(r.getFulltext());
                        a.setStreet(r.getStreet());
                        a.setPostcode(r.getZipcode());
                        a.setCity(r.getCity());
                        a.setLon(r.getX());
                        a.setLat(r.getY());
                        return a;
                    })
                    .toList();
        } catch (HttpClientErrorException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}





