package fr.siamois.domain.services;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.infrastructure.api.dto.GeoPlatResponse;
import fr.siamois.infrastructure.api.dto.geoapi.CommuneListResponse;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.mapper.ConceptMapper;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class GeoApiService {

    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final SpatialUnitRepository spatialUnitRepository;

    private static final String BASE_URL = "https://geo.api.gouv.fr/communes";
    private final RestTemplate restTemplate = new RestTemplate();

    public List<PlaceSuggestionDTO> search(String input) {
        if (input == null || input.trim().length() < 2) {
            return Collections.emptyList();
        }

        // 1. Récupération du Concept (Commune) pour le mapping
        // Tip: Tu pourrais mettre cela en cache pour éviter un appel DB à chaque frappe
        Concept communeConcept = conceptService.findById(417).orElse(new Concept());
        ConceptDTO conceptDTO = conceptMapper.convert(communeConcept);

        // 2. Appel DB : Top 3 par similarité
        List<PlaceSuggestionDTO> internalResults = spatialUnitRepository.findTop3BySimilarity(input)
                .stream()
                .map(su -> {
                    PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
                    dto.setName(su.getName());
                    dto.setCategory(conceptDTO);
                    dto.setCode(su.getCode());
                    dto.setId(String.valueOf(su.getId()));
                    dto.setSourceName("SIAMOIS"); // mark as internal
                    return dto;
                })
                .toList();

        // 3. Appel API Gouv
        URI uri = UriComponentsBuilder
                .fromHttpUrl(BASE_URL)
                .queryParam("nom", input)
                .queryParam("limit", 10)
                .build()
                .encode()
                .toUri();

        CommuneListResponse response = restTemplate.getForObject(uri, CommuneListResponse.class);

        // 4. Fusion des résultats
        List<PlaceSuggestionDTO> finalResults = new ArrayList<>(internalResults);

        if (response != null) {
            // On récupère les codes déjà présents en interne pour éviter les doublons
            Set<String> existingCodes = internalResults.stream()
                    .map(PlaceSuggestionDTO::getCode)
                    .collect(Collectors.toSet());

            response.stream()
                    .filter(r -> !existingCodes.contains(r.getCode())) // Filtre les doublons
                    .limit(7) // On complète pour arriver à environ 10 résultats au total
                    .forEach(r -> {
                        PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
                        dto.setName(r.getNom());
                        dto.setCategory(conceptDTO);
                        dto.setCode(r.getCode());
                        dto.setSourceName("INSEE");
                        dto.setId(r.getCode());
                        finalResults.add(dto);
                    });
        }

        return finalResults;
    }
}





