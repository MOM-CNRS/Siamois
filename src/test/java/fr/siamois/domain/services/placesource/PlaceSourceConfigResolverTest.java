package fr.siamois.domain.services.placesource;

import fr.siamois.domain.models.form.rules.PlaceSourceSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSourceConfigResolverTest {

    private final PlaceSourceConfigResolver resolver = new PlaceSourceConfigResolver();

    @Test
    void theMainLocationOfAProjectSuggestsCommunes() {
        assertThat(resolver.forField(-108L, null, null)).containsExactly(PlaceSourceSpec.of("INSEE"));
    }

    // The precise places are addresses of the commune picked in the main location.
    @Test
    void theSpatialContextOfAProjectSuggestsAddressesOfTheCommunePickedAbove() {
        List<PlaceSourceSpec> sources = resolver.forField(-104L, "3", null);

        assertThat(sources).hasSize(1);
        PlaceSourceSpec geoplat = sources.get(0);
        assertThat(geoplat.source()).isEqualTo("GEOPLAT");
        assertThat(geoplat.params()).containsOnlyKeys("citycode");
        assertThat(geoplat.params().get("citycode").fromField()).isEqualTo(-108L);
        assertThat(geoplat.params().get("citycode").attribute()).isEqualTo(PlaceSourceSpec.PlaceAttribute.CODE);
        assertThat(geoplat.onMissing()).isEqualTo(PlaceSourceSpec.OnMissing.UNFILTERED);
    }

    @Test
    void aFieldWithNoSourceOrUnknownHasNone() {
        assertThat(resolver.forField(-102L, null, null)).isEmpty();
        assertThat(resolver.forField(123456L, null, null)).isEmpty();
    }
}
