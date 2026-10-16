package fr.siamois.domain.services.placesource;

import fr.siamois.domain.services.GeoApiService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The communes of the INSEE reference (geo.api.gouv.fr); a commune is identified by its INSEE code. */
@Component
@RequiredArgsConstructor
public class InseeCommunesProvider implements PlaceSourceProvider {

    public static final String SOURCE_ID = "INSEE";
    private static final String THESAURUS = "th252";
    private static final String COMMUNE_CONCEPT = "4287976";

    private final GeoApiService geoApiService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public Set<String> declaredParams() {
        return Set.of();
    }

    @Override
    public List<ExternalPlace> search(String query, Map<String, String> params) {
        return geoApiService.fetchCommunes(query).stream()
                .map((PlaceSuggestionDTO c) -> new ExternalPlace(c.getName(), c.getCode(), null))
                .toList();
    }

    @Override
    public ConceptDTO type() {
        return conceptMapper.convert(conceptRepository.findConceptByExternalIdIgnoreCase(THESAURUS, COMMUNE_CONCEPT)
                .orElseThrow(() -> new IllegalStateException("Concept « Commune » introuvable")));
    }

    @Override
    public SpatialUnitDTO draftOf(ExternalPlace place) {
        SpatialUnitDTO draft = new SpatialUnitDTO();
        draft.setName(place.name());
        draft.setCode(place.code());
        draft.setType(type());
        return draft;
    }

    @Override
    public boolean identifiedByCode() {
        return true;
    }
}
