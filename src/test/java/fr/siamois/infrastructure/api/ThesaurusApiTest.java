package fr.siamois.infrastructure.api;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.ThesaurusDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyString;

@ExtendWith(MockitoExtension.class)
class ThesaurusApiTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RequestFactory requestFactory;

    @Mock
    private ConceptApi conceptApi;

    private ThesaurusApi thesaurusApi;

    @BeforeEach
    void setUp() {
        when(requestFactory.buildRestTemplate(anyBoolean())).thenReturn(this.restTemplate);
        thesaurusApi = new ThesaurusApi(requestFactory, conceptApi);
    }

    @Test
    void fetchAllPublicThesaurus_success() throws InvalidEndpointException {
        String server = "http://example.com";
        ThesaurusDTO[] thesaurusArray = {new ThesaurusDTO("1", List.of(new LabelDTO("fr", "Label1")), "THESAURUS")};
        when(restTemplate.getForObject(server + "/openapi/v1/thesaurus", ThesaurusDTO[].class)).thenReturn(thesaurusArray);

        List<ThesaurusDTO> result = thesaurusApi.fetchAllPublicThesaurus(server);

        assertEquals(1, result.size());
        assertEquals("Label1", result.get(0).getLabels().get(0).getTitle());
    }

    @Test
    void fetchAllPublicThesaurus_empty() throws InvalidEndpointException {
        String server = "http://example.com";
        when(restTemplate.getForObject(server + "/openapi/v1/thesaurus", ThesaurusDTO[].class)).thenReturn(null);

        List<ThesaurusDTO> result = thesaurusApi.fetchAllPublicThesaurus(server);

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchAllPublicThesaurus_invalidEndpoint() {
        String server = "http://example.com";
        when(restTemplate.getForObject(server + "/openapi/v1/thesaurus", ThesaurusDTO[].class)).thenThrow(new RestClientException("Error"));

        assertThrows(InvalidEndpointException.class, () -> thesaurusApi.fetchAllPublicThesaurus(server));
    }

    @Test
    void fetchThesaurusInfo_invalidEndpoint() {
        String uri = "http://example.com/openapi/v1/thesaurus?idt=123";
        assertThrows(InvalidEndpointException.class, () -> thesaurusApi.fetchThesaurusInfo(uri));
    }

    @Test
    void fetchThesaurusInfo_parsesIdtAfterOtherQueryParams() throws InvalidEndpointException {
        String uri = "https://thesaurus.mom.fr/?idc=4282369&idt=th223";
        ThesaurusDTO expectedThesaurus = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.mom.fr/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expectedThesaurus});

        ThesaurusDTO result = thesaurusApi.fetchThesaurusInfo(uri);

        assertEquals("th223", result.getIdTheso());
        assertEquals("https://thesaurus.mom.fr", result.getBaseUri());
    }

    @Test
    void fetchThesaurusInfo_missingIdt_throwsInvalidEndpoint() {
        assertThrows(InvalidEndpointException.class,
                () -> thesaurusApi.fetchThesaurusInfo("https://thesaurus.mom.fr/?idc=4282369"));
    }

    @Test
    void fetchThesaurusInfoByServerAndId_success() throws InvalidEndpointException {
        String server = "http://example.com";
        String idThesaurus = "123";
        ThesaurusDTO expectedThesaurus = new ThesaurusDTO(idThesaurus, List.of(new LabelDTO("fr", "Label1")), "THESAURUS");
        when(restTemplate.getForObject(server + "/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expectedThesaurus});

        Optional<ThesaurusDTO> result = thesaurusApi.fetchThesaurusInfo(server, idThesaurus);

        assertTrue(result.isPresent());
        assertEquals("123", result.get().getIdTheso());
        assertEquals("Label1", result.get().getLabels().get(0).getTitle());
    }

    @Test
    void fetchThesaurusInfoByServerAndId_notFound() throws InvalidEndpointException {
        String server = "http://example.com";
        String idThesaurus = "123";
        when(restTemplate.getForObject(server + "/openapi/v1/thesaurus", ThesaurusDTO[].class)).thenReturn(new ThesaurusDTO[]{});

        Optional<ThesaurusDTO> result = thesaurusApi.fetchThesaurusInfo(server, idThesaurus);

        assertFalse(result.isPresent());
    }

    @Test
    void fetchThesaurusInfo_arkUri_followsLocationHeaderThenResolvesIdt() throws InvalidEndpointException {
        URI ark = URI.create("https://ark.example/ark:/12345/th1");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("https://thesaurus.example/?idt=th223"));
        when(restTemplate.getForEntity(ark, String.class)).thenReturn(new ResponseEntity<>(headers, HttpStatus.FOUND));

        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.example/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        ThesaurusDTO result = thesaurusApi.fetchThesaurusInfo(ark.toString());

        assertEquals("th223", result.getIdTheso());
        assertEquals("https://thesaurus.example", result.getBaseUri());
        verify(restTemplate).getForEntity(ark, String.class);
    }

    @Test
    void fetchThesaurusInfo_uriWithQuery_skipsRedirectLookup() throws InvalidEndpointException {
        String uri = "https://thesaurus.example/?idt=th223";
        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "Label")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.example/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        ThesaurusDTO result = thesaurusApi.fetchThesaurusInfo(uri);

        assertEquals("th223", result.getIdTheso());
        verify(restTemplate, never()).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void fetchThesaurusInfo_arkUri_noLocation_throwsMissingIdt() {
        URI ark = URI.create("https://ark.example/ark:/12345/th1");
        when(restTemplate.getForEntity(ark, String.class)).thenReturn(ResponseEntity.ok("body"));

        assertThrows(InvalidEndpointException.class, () -> thesaurusApi.fetchThesaurusInfo(ark.toString()));
    }

    @Test
    void fetchThesaurusInfo_unreachableHost_throwsInvalidEndpoint() {
        URI unreachable = URI.create("https://thesaurus.invalid/opentheso");
        when(restTemplate.getForEntity(unreachable, String.class))
                .thenThrow(new ResourceAccessException("thesaurus.invalid: nodename nor servname provided"));

        assertThrows(InvalidEndpointException.class, () -> thesaurusApi.fetchThesaurusInfo(unreachable.toString()));
    }

    @Test
    void fetchThesaurusInfo_arkResolverChain_isFollowedToTheThesaurus() throws InvalidEndpointException {
        URI firstResolver = URI.create("https://ark.frantiq.fr/ark:/26678/pcrtA40nPyUzoy");
        URI secondResolver = URI.create("https://ark.mom.fr/ark:/26678/pcrtA40nPyUzoy");
        URI concept = URI.create("https://thesaurus.mom.fr/?idc=4282369&idt=th223");

        HttpHeaders toSecond = new HttpHeaders();
        toSecond.setLocation(secondResolver);
        when(restTemplate.getForEntity(firstResolver, String.class))
                .thenReturn(new ResponseEntity<>(toSecond, HttpStatus.FOUND));

        HttpHeaders toConcept = new HttpHeaders();
        toConcept.setLocation(concept);
        when(restTemplate.getForEntity(secondResolver, String.class))
                .thenReturn(new ResponseEntity<>(toConcept, HttpStatus.FOUND));

        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.mom.fr/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        ThesaurusDTO result = thesaurusApi.fetchThesaurusInfo(firstResolver.toString());

        assertEquals("th223", result.getIdTheso());
        assertEquals("https://thesaurus.mom.fr", result.getBaseUri());
        verifyNoInteractions(conceptApi);
    }

    @Test
    void fetchThesaurusInfo_relativeLocation_isResolvedAgainstTheUriItCameFrom() throws InvalidEndpointException {
        URI ark = URI.create("https://thesaurus.mom.fr/ark:/26678/pcrtA40nPyUzoy");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/?idc=4282369&idt=th223"));
        when(restTemplate.getForEntity(ark, String.class)).thenReturn(new ResponseEntity<>(headers, HttpStatus.FOUND));

        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.mom.fr/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        assertEquals("th223", thesaurusApi.fetchThesaurusInfo(ark.toString()).getIdTheso());
    }

    @Test
    void fetchThesaurusInfo_redirectionLoop_stopsInsteadOfHanging() {
        URI looping = URI.create("https://ark.example/ark:/26678/loop");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("https://ark.example/ark:/26678/loop2"));
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(headers, HttpStatus.FOUND));
        when(conceptApi.fetchSchemeUriOfArk(anyString(), anyString())).thenReturn(Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(ThesaurusDTO[].class))).thenReturn(new ThesaurusDTO[0]);

        assertThrows(InvalidEndpointException.class, () -> thesaurusApi.fetchThesaurusInfo(looping.toString()));

        // the walk is bounded : it does not follow the loop forever
        verify(restTemplate, atMost(5)).getForEntity(any(URI.class), eq(String.class));
    }

    @Test
    void fetchThesaurusInfo_conceptArk_resolvesTheThesaurusItBelongsTo() throws InvalidEndpointException {
        // a concept ark : no idt anywhere, the thesaurus is only reachable through the concept's scheme
        URI conceptArk = URI.create("https://thesaurus.example/opentheso/ark:/26678/pcrtREVS9rPi7K");
        when(restTemplate.getForEntity(conceptArk, String.class)).thenReturn(ResponseEntity.ok("body"));
        when(conceptApi.fetchSchemeUriOfArk("https://thesaurus.example/opentheso", "ark:/26678/pcrtREVS9rPi7K"))
                .thenReturn(Optional.of("https://thesaurus.example/opentheso/api/ark:/66666/th223"));

        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.example/opentheso/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        ThesaurusDTO result = thesaurusApi.fetchThesaurusInfo(conceptArk.toString());

        assertEquals("th223", result.getIdTheso());
        assertEquals("https://thesaurus.example/opentheso", result.getBaseUri());
    }

    @Test
    void fetchThesaurusInfo_thesaurusArk_fallsBackOnItsOwnQualifier() throws InvalidEndpointException {
        // the ark of the thesaurus itself is part of no scheme : its qualifier already is the idt
        URI thesaurusArk = URI.create("https://thesaurus.example/api/ark:/66666/th223");
        when(restTemplate.getForEntity(thesaurusArk, String.class)).thenReturn(ResponseEntity.ok("body"));
        when(conceptApi.fetchSchemeUriOfArk("https://thesaurus.example", "ark:/66666/th223"))
                .thenReturn(Optional.empty());

        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.example/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        assertEquals("th223", thesaurusApi.fetchThesaurusInfo(thesaurusArk.toString()).getIdTheso());
    }

    @Test
    void fetchThesaurusInfo_arkProbeUnreachable_stillResolvesThroughTheApi() throws InvalidEndpointException {
        // the plain GET on the ark may answer nothing usable; that must not stop the API resolution
        URI conceptArk = URI.create("https://thesaurus.example/opentheso/ark:/26678/pcrtREVS9rPi7K");
        when(restTemplate.getForEntity(conceptArk, String.class))
                .thenThrow(new ResourceAccessException("no route to host"));
        when(conceptApi.fetchSchemeUriOfArk("https://thesaurus.example/opentheso", "ark:/26678/pcrtREVS9rPi7K"))
                .thenReturn(Optional.of("https://thesaurus.example/opentheso/api/ark:/66666/th223"));

        ThesaurusDTO expected = new ThesaurusDTO("th223", List.of(new LabelDTO("fr", "SIAMOIS")), "THESAURUS");
        when(restTemplate.getForObject("https://thesaurus.example/opentheso/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{expected});

        assertEquals("th223", thesaurusApi.fetchThesaurusInfo(conceptArk.toString()).getIdTheso());
    }

    @Test
    void fetchThesaurusInfo_blankIdt_throws() {
        assertThrows(InvalidEndpointException.class,
                () -> thesaurusApi.fetchThesaurusInfo("https://thesaurus.example/?idt="));
    }

    @Test
    void fetchAllPublicThesaurus_illegalArgument_throwsInvalidEndpoint() {
        when(restTemplate.getForObject("http://example.com/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenThrow(new IllegalArgumentException("bad uri"));

        assertThrows(InvalidEndpointException.class,
                () -> thesaurusApi.fetchAllPublicThesaurus("http://example.com"));
    }

    @Test
    void fetchThesaurusInfo_byServerAndId_caseInsensitiveMatch_setsBaseUri() throws InvalidEndpointException {
        ThesaurusDTO listed = new ThesaurusDTO("TH223", List.of(new LabelDTO("fr", "L")), "THESAURUS");
        when(restTemplate.getForObject("http://example.com/openapi/v1/thesaurus", ThesaurusDTO[].class))
                .thenReturn(new ThesaurusDTO[]{listed});

        Optional<ThesaurusDTO> result = thesaurusApi.fetchThesaurusInfo("http://example.com", "th223");

        assertTrue(result.isPresent());
        assertEquals("http://example.com", result.get().getBaseUri());
    }

}