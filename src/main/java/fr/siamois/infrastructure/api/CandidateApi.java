package fr.siamois.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.infrastructure.api.dto.CandidateDTO;
import fr.siamois.infrastructure.crypto.ApiKeyEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Slf4j
@Service
public class CandidateApi {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final ApiKeyEncryptor apiKeyEncryptor;

    public CandidateApi(RequestFactory factory, ApiKeyEncryptor apiKeyEncryptor) {
        restTemplate = factory.buildRestTemplate(true);
        mapper = new ObjectMapper();
        this.apiKeyEncryptor = apiKeyEncryptor;
    }

    public boolean postCandidateFor(VocabularyApiKey vocabularyApiKey, CandidateDTO candidateDTO) throws JsonProcessingException {
        URI uri = URI.create(String.format("%s/openapi/v1/candidate", vocabularyApiKey.getInstance().getBaseUri()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("API-KEY", apiKeyEncryptor.decrypt(vocabularyApiKey.getEncryptedKey()));
        String body = mapper.writeValueAsString(candidateDTO);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.error("Could not post the candidate to {}: {}", uri, e.getMessage());
            return false;
        }
    }

}
