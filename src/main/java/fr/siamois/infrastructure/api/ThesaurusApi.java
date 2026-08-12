package fr.siamois.infrastructure.api;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.infrastructure.api.dto.ThesaurusDTO;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.lang.Nullable;
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

import static fr.siamois.utils.ArkUtils.extractArkOf;

/**
 * Service to fetch thesaurus information from the API.
 *
 * @author Julien Linget
 */
@Slf4j
@Service
public class ThesaurusApi {

    private static final String ARK_PREFIX = "ark:";
    private static final int MAX_ARK_REDIRECTIONS = 10;

    private final RestTemplate restTemplate;

    /**
     * Deliberately does NOT follow redirections: resolving an ark is reading the {@code Location}
     * header hop by hop, which a following client would consume and hide.
     */
    private final RestTemplate arkResolvingTemplate;

    private final ConceptApi conceptApi;

    public ThesaurusApi(RequestFactory requestFactory, ConceptApi conceptApi) {
        this.restTemplate = requestFactory.buildRestTemplate(true);
        this.arkResolvingTemplate = requestFactory.buildRestTemplate(false);
        this.conceptApi = conceptApi;
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
        } catch (IllegalArgumentException e) {
            log.error("Invalid URI: {}", uri, e);
            throw new InvalidEndpointException("Invalid URI: " + uri);
        }
        uriObj = findRedirectUriIfArk(uriObj);

        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(uriObj).build().getQueryParams();
        String externalId = queryParams.getFirst("idt");

        String host;
        if (externalId == null || externalId.isBlank()) {
            host = apiRootOfArk(uriObj);
            externalId = thesaurusIdOfArk(host, arkOf(uriObj));
        } else {
            host = UriComponentsBuilder.fromUri(uriObj)
                    .replaceQuery(null)
                    .fragment(null)
                    .build()
                    .toUriString();
            if (host.endsWith("/")) {
                host = host.substring(0, host.length() - 1);
            }
        }

        Optional<ThesaurusDTO> result = fetchThesaurusInfo(host, externalId);
        if (result.isEmpty()) {
            throw new InvalidEndpointException(
                    String.format("Could not fetch thesaurus info of %s from the API %s", externalId, host)
            );
        }
        return result.get();
    }

    public String resolveRedirections(String uri) {
        try {
            return findRedirectUriIfArk(URI.create(uri)).toString();
        } catch (IllegalArgumentException e) {
            log.warn("Could not read {} as an URI", uri, e);
            return uri;
        }
    }

    private URI findRedirectUriIfArk(@NotNull URI uriObj) {
        URI current = uriObj;
        for (int hop = 0; hop < MAX_ARK_REDIRECTIONS; hop++) {
            // URIs that already expose query params (even without idt) must not hit the network here.
            if (current.getRawQuery() != null && !current.getRawQuery().isEmpty()) {
                return current;
            }
            URI next = redirectionOf(current);
            if (next == null || next.equals(current)) {
                return current;
            }
            current = next;
        }
        log.warn("Stopped following {} after {} redirections", uriObj, MAX_ARK_REDIRECTIONS);
        return current;
    }

    @Nullable
    private URI redirectionOf(@NotNull URI uriObj) {
        try {
            HttpEntity<String> entity = arkResolvingTemplate.getForEntity(uriObj, String.class);
            URI location = entity.getHeaders().getLocation();
            // a Location header is allowed to be relative, and is then read against the URI it came from
            return location == null ? null : uriObj.resolve(location);
        } catch (RestClientException | IllegalArgumentException e) {
            // an ark that answers nothing to a plain GET can still be resolvable through the API,
            // so this probe failing is not the end of the road : keep the URI as it was typed
            log.warn("Could not follow {} as an ark redirection", uriObj, e);
            return null;
        }
    }

    /**
     * The root of the Opentheso instance hosting an ark URI, i.e. everything before the ark itself,
     * without the {@code /api} mount arks are published under.
     */
    private String apiRootOfArk(@NotNull URI uriObj) throws InvalidEndpointException {
        String raw = uriObj.toString();
        int arkIndex = raw.indexOf(ARK_PREFIX);
        if (arkIndex < 0) {
            throw new InvalidEndpointException("Invalid URI: missing idt parameter in " + uriObj);
        }
        String root = raw.substring(0, arkIndex);
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.endsWith("/api")) {
            root = root.substring(0, root.length() - "/api".length());
        }
        return root;
    }

    /**
     * The ark alone : what follows it in the URL — a query string, a fragment — is not part of the
     * identifier and would be sent to the API as if it were.
     */
    private String arkOf(@NotNull URI uriObj) {
        String raw = uriObj.toString();
        String ark = raw.substring(raw.indexOf(ARK_PREFIX));
        return extractArkOf(ark);
    }

    private String thesaurusIdOfArk(String apiRoot, String ark) {
        return conceptApi.fetchSchemeUriOfArk(apiRoot, ark)
                .map(ThesaurusApi::lastSegmentOf)
                .orElseGet(() -> lastSegmentOf(ark));
    }

    private static String lastSegmentOf(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
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
