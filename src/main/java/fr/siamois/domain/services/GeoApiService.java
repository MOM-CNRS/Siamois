package fr.siamois.domain.services;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.infrastructure.api.dto.GeoPlatResponse;
import fr.siamois.infrastructure.api.dto.geoapi.CommuneListResponse;
import fr.siamois.mapper.ConceptMapper;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
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

    private static final String BASE_URL =
            "https://geo.api.gouv.fr/communes";

    private final RestTemplate restTemplate = new RestTemplate();

    public List<SpatialUnitSummaryDTO> search(String input) {

        URI uri = UriComponentsBuilder
                .fromHttpUrl(BASE_URL)
                .queryParam("nom", input)
                .queryParam("limit", 10)
                .build()
                .encode()
                .toUri();

        CommuneListResponse response =
                restTemplate.getForObject(uri, CommuneListResponse.class);

        if (response == null) {
            return Collections.emptyList();
        }

        Concept communeConcept = conceptService.findById(417).orElse(new Concept());
        ConceptDTO conceptDTO = conceptMapper.convert(communeConcept);

        return response.stream()
                .map(r -> {
                    SpatialUnitSummaryDTO a = new SpatialUnitSummaryDTO();
                    a.setName(r.getNom());
                    a.setCategory(conceptDTO);
                    a.setCode(r.getCode());
                    return a;
                })
                .toList();
    }
}





