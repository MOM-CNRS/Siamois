package fr.siamois.domain.services.placesource;

import fr.siamois.domain.services.GeoPlatService;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The addresses of the GéoPlateforme geocoding completion. {@code citycode} (INSEE code of the
 * commune), {@code zipcode} and {@code depcode} narrow the search; they are the names of the service's
 * own query parameters.
 */
@Component
@RequiredArgsConstructor
public class GeoplatformeAddressProvider implements PlaceSourceProvider {

    public static final String SOURCE_ID = "GEOPLAT";
    private static final int MIN_QUERY = 3;
    private static final String THESAURUS = "th252";
    private static final String ADDRESS_CONCEPT = "4288314";

    private final GeoPlatService geoPlatService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public Set<String> declaredParams() {
        return GeoPlatService.FILTERS;
    }

    @Override
    public List<ExternalPlace> search(String query, Map<String, String> params) {
        if (query == null || query.trim().length() < MIN_QUERY) {
            return List.of();
        }
        return geoPlatService.search(query, params).stream()
                .map(a -> new ExternalPlace(a.getLabel(), null, a))
                .toList();
    }

    @Override
    public ConceptDTO category() {
        return conceptMapper.convert(conceptRepository.findConceptByExternalIdIgnoreCase(THESAURUS, ADDRESS_CONCEPT)
                .orElseThrow(() -> new IllegalStateException("Concept « Adresse » introuvable")));
    }

    @Override
    public SpatialUnitDTO draftOf(ExternalPlace place) {
        FullAddress address = place.address();
        if (address == null) {
            address = new FullAddress();
            address.setLabel(place.name());
        }
        SpatialUnitDTO draft = new SpatialUnitDTO();
        draft.setName(place.name());
        draft.setAddress(address);
        draft.setCategory(category());
        return draft;
    }
}
