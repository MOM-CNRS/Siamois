package fr.siamois.infrastructure.api;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.infrastructure.api.dto.ThesaurusDTO;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service to fetch thesaurus information from the API.
 *
 * @author Julien Linget
 */
@Slf4j
@Service
public class ThesaurusApi {

    private final RestTemplate restTemplate;

    public ThesaurusApi(RequestFactory requestFactory) {
        this.restTemplate = requestFactory.buildRestTemplate(false);
    }

    /**
     * Send a request to the API to fetch all public thesaurus names, ids and labels.
     *
     * @param server The server URL
     * @return A list of thesaurus DTOs
     */
    public List<ThesaurusDTO> fetchAllPublicThesaurus(String server) throws InvalidEndpointException {
        String uri = server + "/openapi/v1/thesaurus";
        try {
            ThesaurusDTO[] data = restTemplate.getForObject(uri, ThesaurusDTO[].class);
            if (data == null) return new ArrayList<>();
            return Arrays.asList(data);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new InvalidEndpointException("Could not fetch thesaurus data from the API");
        }
    }

    /**
     * Fetch thesaurus information based on the provided URI.
     *
     * @param uri The URI of the thesaurus, which should contain the idt parameter.
     * @return ThesaurusDTO containing the information of the thesaurus.
     * @throws InvalidEndpointException If the URI is invalid or if the thesaurus information cannot be fetched.
     */
    public ThesaurusDTO fetchThesaurusInfo(String uri) throws InvalidEndpointException {
        URI uriObj;
        try {
            uriObj = URI.create(uri);
            uriObj = findRedirectUriIfArk(uriObj);
        } catch (IllegalArgumentException e) {
            log.error("Invalid URI: {}", uri, e);
            throw new InvalidEndpointException("Invalid URI: " + uri);
        }

        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(uriObj).build().getQueryParams();
        String externalId = queryParams.getFirst("idt");
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidEndpointException("Invalid URI: missing idt parameter in " + uriObj);
        }

        String host = UriComponentsBuilder.fromUri(uriObj)
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        Optional<ThesaurusDTO> result = fetchThesaurusInfo(host, externalId);
        if (result.isEmpty()) {
            throw new InvalidEndpointException(
                    String.format("Could not fetch thesaurus info of %s from the API %s", externalId, host)
            );
        }
        return result.get();
    }

    private URI findRedirectUriIfArk(@NotNull URI uriObj) {
        // ARK URIs have no query string and redirect to a URL containing idt.
        // URIs that already expose query params (even without idt) must not hit the network here.
        if (uriObj.getRawQuery() != null && !uriObj.getRawQuery().isEmpty()) {
            return uriObj;
        }
        HttpEntity<String> entity = restTemplate.getForEntity(uriObj, String.class);
        if (entity != null && entity.getHeaders().getLocation() != null) {
            return entity.getHeaders().getLocation();
        }
        return uriObj;
    }

    /**
     * Fetch thesaurus information based on the server and idThesaurus.
     *
     * @param server      The server URL where the thesaurus is hosted.
     * @param idThesaurus The ID of the thesaurus to fetch.
     * @return An Optional containing the ThesaurusDTO if found, otherwise empty.
     * @throws InvalidEndpointException If the server URL is invalid or if the thesaurus information cannot be fetched.
     */
    public Optional<ThesaurusDTO> fetchThesaurusInfo(String server, String idThesaurus) throws InvalidEndpointException {
        List<ThesaurusDTO> publicThesaurus = fetchAllPublicThesaurus(server);

        Optional<ThesaurusDTO> result = publicThesaurus.stream()
                .filter(thesaurus -> thesaurus.getIdTheso().equalsIgnoreCase(idThesaurus))
                .findFirst();

        result.ifPresent(thesaurusDTO -> thesaurusDTO.setBaseUri(server));

        return result;
    }

}
