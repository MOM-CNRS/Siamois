package fr.siamois.domain.services;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.api.dto.geoapi.CommuneListResponse;
import fr.siamois.mapper.ConceptMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Service
public class GeoApiService {

    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;

    private static final String BASE_URL = "https://geo.api.gouv.fr/communes";

    private final RestTemplate restTemplate;

    public List<PlaceSuggestionDTO> fetchCommunes(String input) {
        if (input == null || input.trim().length() < 3) {
            return Collections.emptyList();
        }

        URI uri = UriComponentsBuilder
                .fromHttpUrl(BASE_URL)
                .queryParam("nom", input)
                .queryParam("limit", 10)
                .build()
                .encode()
                .toUri();

        CommuneListResponse response = restTemplate.getForObject(uri, CommuneListResponse.class);

        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        // On récupère le concept "Commune" (ID 417) pour typer les résultats externes
        Concept communeConcept = conceptService.findById(417).orElse(new Concept());
        ConceptDTO conceptDTO = conceptMapper.convert(communeConcept);

        return response.stream()
                .map(r -> {
                    PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
                    dto.setName(r.getNom());
                    dto.setCategory(conceptDTO);
                    dto.setCode(r.getCode());
                    dto.setSourceName("INSEE");
                    return dto;
                })
                .toList();
    }
}