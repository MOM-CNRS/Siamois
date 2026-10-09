package fr.siamois.domain.services.placesource;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.GeoApiService;
import fr.siamois.domain.services.GeoPlatService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSourceProvidersTest {

    @Mock private GeoApiService geoApiService;
    @Mock private GeoPlatService geoPlatService;
    @Mock private ConceptRepository conceptRepository;
    @Mock private ConceptMapper conceptMapper;

    private InseeCommunesProvider insee;
    private GeoplatformeAddressProvider geoplat;
    private ConceptDTO concept;

    @BeforeEach
    void setUp() {
        insee = new InseeCommunesProvider(geoApiService, conceptRepository, conceptMapper);
        geoplat = new GeoplatformeAddressProvider(geoPlatService, conceptRepository, conceptMapper);
        concept = new ConceptDTO();
        concept.setId(417L);
    }

    private void conceptExists() {
        when(conceptRepository.findConceptByExternalIdIgnoreCase(eq("th252"), any())).thenReturn(Optional.of(new Concept()));
        when(conceptMapper.convert(any(Concept.class))).thenReturn(concept);
    }

    @Test
    void inseeSuggestsCommunesIdentifiedByTheirCode() {
        PlaceSuggestionDTO commune = new PlaceSuggestionDTO();
        commune.setName("Lyon");
        commune.setCode("69123");
        when(geoApiService.fetchCommunes("lyo")).thenReturn(List.of(commune));

        assertThat(insee.id()).isEqualTo("INSEE");
        assertThat(insee.declaredParams()).isEmpty();
        assertThat(insee.identifiedByCode()).isTrue();
        assertThat(insee.search("lyo", Map.of())).containsExactly(new ExternalPlace("Lyon", "69123", null));
    }

    @Test
    void inseeDraftsACommuneWithItsCodeAndCategory() {
        conceptExists();

        SpatialUnitDTO draft = insee.draftOf(new ExternalPlace("Lyon", "69123", null));

        assertThat(draft.getName()).isEqualTo("Lyon");
        assertThat(draft.getCode()).isEqualTo("69123");
        assertThat(draft.getType()).isSameAs(concept);
    }

    @Test
    void inseeCategoryIsRequired() {
        when(conceptRepository.findConceptByExternalIdIgnoreCase(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> insee.type()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void geoplatformeDeclaresTheServiceFiltersAndPassesThemOn() {
        FullAddress address = new FullAddress();
        address.setLabel("1 rue X 69001 Lyon");
        when(geoPlatService.search("rue", Map.of("citycode", "69123"))).thenReturn(List.of(address));

        assertThat(geoplat.id()).isEqualTo("GEOPLAT");
        assertThat(geoplat.declaredParams()).containsExactlyInAnyOrder("citycode", "zipcode", "depcode");
        assertThat(geoplat.identifiedByCode()).isFalse();
        assertThat(geoplat.search("rue", Map.of("citycode", "69123")))
                .containsExactly(new ExternalPlace("1 rue X 69001 Lyon", null, address));
    }

    @Test
    void geoplatformeNeedsThreeCharacters() {
        assertThat(geoplat.search("ru", Map.of())).isEmpty();
        assertThat(geoplat.search(null, Map.of())).isEmpty();
    }

    @Test
    void geoplatformeDraftsAnAddressPlaceNamedByItsLabel() {
        conceptExists();
        FullAddress address = new FullAddress();
        address.setLabel("1 rue X");

        SpatialUnitDTO withAddress = geoplat.draftOf(new ExternalPlace("1 rue X", null, address));
        SpatialUnitDTO withoutAddress = geoplat.draftOf(new ExternalPlace("2 rue Y", null, null));

        assertThat(withAddress.getAddress()).isSameAs(address);
        assertThat(withAddress.getCode()).isNull();
        assertThat(withoutAddress.getAddress().getLabel()).isEqualTo("2 rue Y");
    }

    @Test
    void theRegistryFindsProvidersById() {
        PlaceSourceRegistry registry = new PlaceSourceRegistry(List.of(insee, geoplat));

        assertThat(registry.find("INSEE")).containsSame(insee);
        assertThat(registry.find("NOPE")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.ids()).containsExactly("INSEE", "GEOPLAT");
    }
}
