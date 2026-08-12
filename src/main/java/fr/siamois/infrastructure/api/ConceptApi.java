package fr.siamois.infrastructure.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service to fetch concept information from the API.
 *
 * @author Julien Linget
 */
@Slf4j
@Service
public class ConceptApi {

    public static final String ERROR_WHILE_PROCESSING_JSON = "Error while processing JSON";
    private final RestTemplate restTemplate;

    private final ObjectMapper mapper;
    private FieldRepository fieldRepository;
    private ConceptCollectionRepository conceptCollectionRepository;

    /**
     * Autowired constructor for ConceptApi.
     *
     * @param factory RequestFactory to build the RestTemplate.
     */
    @Autowired
    public ConceptApi(RequestFactory factory, FieldRepository fieldRepository, ConceptCollectionRepository conceptCollectionRepository) {
        restTemplate = factory.buildRestTemplate(true);
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.fieldRepository = fieldRepository;
        this.conceptCollectionRepository = conceptCollectionRepository;
    }

    /**
     * Constructor for ConceptApi with a custom RestTemplate for testing purposes.
     *
     * @param restTemplate RestTemplate to use for API requests.
     */
    public ConceptApi(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Constructor for ConceptApi with a custom ObjectMapper for testing purposes.
     *
     * @param factory RequestFactory to build the RestTemplate.
     * @param mapper  ObjectMapper to use for JSON processing.
     */
    ConceptApi(RequestFactory factory, ObjectMapper mapper) {
        this.restTemplate = factory.buildRestTemplate(true);
        this.mapper = mapper;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Fetches the down expansion of a concept in a given vocabulary.
     *
     * @param vocabulary the vocabulary in which the concept is defined
     * @param idConcept  the external ID of the concept to expand
     * @return ConceptBranchDTO containing the expanded concepts
     * @throws ErrorProcessingExpansionException if there is an error processing the expansion
     */
    public ConceptBranchDTO fetchDownExpansion(Vocabulary vocabulary, String idConcept) throws ErrorProcessingExpansionException {
        URI uri = URI.create(String.format("%s/openapi/v1/concept/%s/%s/expansion?way=down", vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId(), idConcept));

        ResponseEntity<String> response = sendRequestAcceptJson(uri);

        TypeReference<Map<String, FullInfoDTO>> typeReference = new TypeReference<>() {
        };

        return processApiResponse(response, typeReference);
    }

    private ConceptBranchDTO processApiResponse(ResponseEntity<String> response, TypeReference<Map<String, FullInfoDTO>> typeReference) throws ErrorProcessingExpansionException {
        Map<String, FullInfoDTO> result;
        try {
            result = mapper.readValue(response.getBody(), typeReference);
            ConceptBranchDTO branch = new ConceptBranchDTO();
            result.forEach(branch::addConceptBranchDTO);
            return branch;
        } catch (JsonProcessingException e) {
            log.error(ERROR_WHILE_PROCESSING_JSON, e);
            throw new ErrorProcessingExpansionException("Error while processing JSON for expansion");
        }
    }

    private static String bytesToHex(byte[] hash) {
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

    private static String hashOfString(String string) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] hash = digest.digest(string.getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("SHA3-256 algorithm not found", e);
        }
    }

    public ConceptBranchDTO fetchDownExpansion(ConceptFieldConfig config) throws ErrorProcessingExpansionException {
        Concept concept = config.getConcept();
        Vocabulary vocabulary = concept.getVocabulary();
        String existingChecksum = config.getExistingHash();
        URI uri = URI.create(String.format("%s/openapi/v1/concept/%s/%s/expansion?way=down", vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId(), concept.getExternalId()));
        ResponseEntity<String> response = sendRequestAcceptJson(uri);
        String body = response.getBody();
        if (body == null) return null;
        String contentSum = hashOfString(body);
        if (existingChecksum != null && existingChecksum.equals(contentSum))
            return null;

        fieldRepository.updateChecksumForFieldConfig(config.getId(), contentSum);

        TypeReference<Map<String, FullInfoDTO>> typeReference = new TypeReference<>() {
        };
        return processApiResponse(response, typeReference);
    }

    /**
     * Fetch collection's concepts
     * @param vocabulary The vocabulary of the Concept
     * @param collection The collection
     * @return The complete branch including all concepts of the collection, if the collection has not changed since last fetch. Skip process and returns null
     */
    @Nullable
    public ConceptBranchDTO fetchCollectionBranch(Vocabulary vocabulary, ConceptCollection collection) throws ErrorProcessingExpansionException {
        URI uri = URI.create(String.format("%s/openapi/v1/group/%s/branch?idGroups=%s", vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId(), collection.getExternalId()));
        ResponseEntity<String> response = sendRequestAcceptJson(uri);
        String body = response.getBody();
        if (body == null) {
            throw new ErrorProcessingExpansionException("Could not find branch with id " + collection.getExternalId());
        }
        String contentSum = hashOfString(body);
        if (Objects.equals(collection.getExistingHash(), contentSum)) {
            log.warn("Collection {} of {} has not changed. Skipping...",  collection.getExternalId(), vocabulary.getExternalVocabularyId());
            return null;
        }

        collection.setExistingHash(contentSum);
        conceptCollectionRepository.save(collection);

        TypeReference<Map<String, FullInfoDTO>> typeReference = new TypeReference<>() {};

        return processApiResponse(response, typeReference);
    }

    /**
     * The input is what the user is typing, so it reaches this method with spaces, accents or any
     * other character illegal in a URI : the path segments are built through
     * {@link UriComponentsBuilder} and encoded, since {@code URI.create} on a raw input would throw
     * {@link IllegalArgumentException}. Only the segments are encoded — percent-encoding
     * {@code vocabularyUri} as a whole would escape its own {@code ://} and {@code /} and leave a
     * relative URI the {@code RestTemplate} refuses.
     */
    public List<ConceptRemoteAutocompleteDTO> fetchRemoteAutocomplete(String vocabularyUri, String vocabularyExternalId, String input) throws JsonProcessingException {
        URI uri = UriComponentsBuilder.fromUriString(vocabularyUri)
                .pathSegment("openapi", "v1", "concept", vocabularyExternalId, "autocomplete", input)
                .queryParam("full", "true")
                .build()
                .encode()
                .toUri();
        ResponseEntity<String> response = sendRequestAcceptJson(uri);
        String body = response.getBody();

        TypeReference<List<ConceptRemoteAutocompleteDTO>> typeReference = new TypeReference<>() {};
        return mapper.readValue(body, typeReference);
    }

    public List<ConceptApiCollectionDTO> fetchPublicCollections(VocabularyDTO vocabularyDTO) {
        var uri = URI.create(String.format("%s/openapi/v1/group/%s", vocabularyDTO.getBaseUri(), vocabularyDTO.getExternalVocabularyId()));
        ResponseEntity<String> response = sendRequestAcceptJson(uri);
        String body = response.getBody();
        TypeReference<List<ConceptApiCollectionDTO>> typeReference = new TypeReference<>() {};
        try {
            return mapper.readValue(body, typeReference);
        } catch (JsonProcessingException e) {
            log.error(ERROR_WHILE_PROCESSING_JSON, e);
            return new ArrayList<>();
        }
    }

    static class ConceptDTO {
        @JsonProperty("idConcept")
        String idConcept;

        @JsonProperty("labels")
        LabelDTO[] labels;
    }

    /**
     * Fetches the full information of a concept in a given vocabulary.
     *
     * @param vocabulary the vocabulary in which the concept is defined
     * @param conceptId  the external ID of the concept to fetch
     * @return FullInfoDTO containing the full information of the concept
     */
    public FullInfoDTO fetchConceptInfo(Vocabulary vocabulary, String conceptId) {
        URI uri = URI.create(vocabulary.getBaseUri() + String.format("/openapi/v1/concept/%s/%s", vocabulary.getExternalVocabularyId(), conceptId));
        ResponseEntity<String> response = sendRequestAcceptJson(uri);

        TypeReference<Map<String, FullInfoDTO>> typeReference = new TypeReference<>() {
        };

        try {
            Map<String, FullInfoDTO> result = mapper.readValue(response.getBody(), typeReference);
            return result.values().stream().findFirst().orElseThrow(() -> new RuntimeException("Invalid concept"));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public FullInfoDTO fetchConceptInfoByUri(Vocabulary vocabulary, String uriStr) {
        String identifier = "";
        if (uriStr.contains("ark:")) {
            identifier = uriStr.substring(uriStr.indexOf("ark:"));
        } else {
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString(uriStr).build().getQueryParams();
            identifier = params.getFirst("idt") + "/" + params.getFirst("idc");
        }
        URI uri = URI.create(vocabulary.getBaseUri() + String.format("/openapi/v1/concept/%s", identifier));
        ResponseEntity<String> response = sendRequestAcceptJson(uri);

        TypeReference<Map<String, FullInfoDTO>> typeReference = new TypeReference<>() {
        };

        try {
            Map<String, FullInfoDTO> result = mapper.readValue(response.getBody(), typeReference);
            if (result.isEmpty()) {
                return null;
            }
            return result.values()
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Map should not be empty here"));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private ResponseEntity<String> sendRequestAcceptJson(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, requestEntity, String.class);
    }

    private boolean isAutocompleteTopTerm(FullInfoDTO concept) {
        return concept != null && concept.getNotation() != null
                && Arrays.stream(concept.getNotation())
                .anyMatch(notation -> notation.getValue().equalsIgnoreCase("SIAMOIS#SIAAUTO"));
    }

    private Optional<ConceptDTO> findAutocompleteTopTerm(Vocabulary vocabulary, ConceptDTO[] array) {
        for (ConceptDTO conceptDTO : array) {
            FullInfoDTO fullInfoDTO = fetchConceptInfo(vocabulary, conceptDTO.idConcept);
            if (isAutocompleteTopTerm(fullInfoDTO)) {
                return Optional.of(conceptDTO);
            }
        }
        return Optional.empty();
    }

    /**
     * Fetches the branch of fields for a given vocabulary.
     *
     * @param vocabulary the vocabulary for which to fetch the fields branch
     * @return ConceptBranchDTO containing the fields branch
     * @throws NotSiamoisThesaurusException      if the vocabulary is not a Siamois thesaurus
     * @throws ErrorProcessingExpansionException if there is an error processing the expansion
     */
    public ConceptBranchDTO fetchFieldsBranch(Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException {
        URI uri = URI.create(vocabulary.getBaseUri() + String.format("/openapi/v1/thesaurus/%s/topconcept", vocabulary.getExternalVocabularyId()));

        String conceptDTO = restTemplate.getForObject(uri, String.class);
        if (conceptDTO == null) {
            log.error("Vocabulary not found");
            return null;
        }

        try {
            ConceptDTO[] array = mapper.readValue(conceptDTO, ConceptDTO[].class);

            ConceptDTO autocompleteParent = findAutocompleteTopTerm(vocabulary, array)
                    .orElseThrow(() -> new NotSiamoisThesaurusException("Concept with notation SIAMOIS#SIAAUTO not found in thesaurus %s",
                            vocabulary.getExternalVocabularyId()));

            return fetchDownExpansion(vocabulary, autocompleteParent.idConcept);

        } catch (JsonProcessingException e) {
            log.error("Error while parsing branch", e);
            throw new ErrorProcessingExpansionException("Error while parsing branch");
        }
    }

}
