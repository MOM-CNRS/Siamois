package fr.siamois.ui.bean.dialog.project;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The places offered by the pickers of the new project dialog, and the keys they are posted back with. */
class PlaceChoiceTest {

    @Test
    void of_shouldKeyAPlaceOfTheOrganisationByItsId() {
        ResolvedConceptResource commune = new ResolvedConceptResource().setResolvedLabel("Commune");

        PlaceChoice choice = PlaceChoice.of(new PlaceSuggestionItemApi(4L, "Lyon", "69123", "SIAMOIS", commune, null));

        assertThat(choice.getKey()).isEqualTo("4");
        assertThat(choice.getCategory()).isEqualTo("Commune");
        assertThat(choice.isExternal()).isFalse();
    }

    @Test
    void of_shouldKeyASuggestionByItsSourceAndCode_elseItsName() {
        PlaceChoice commune = PlaceChoice.of(new PlaceSuggestionItemApi(null, "Lyon", "69123", "INSEE", null, null));
        PlaceChoice address = PlaceChoice.of(new PlaceSuggestionItemApi(null, "1 rue Mercière", null, "GEOPLAT", null, null));

        assertThat(commune.getKey()).isEqualTo("external:INSEE:69123");
        assertThat(commune.isExternal()).isTrue();
        assertThat(address.getKey()).isEqualTo("external:GEOPLAT:1 rue Mercière");
    }

    @Test
    void created_shouldBeThePlaceCreatedFromTheSuggestion() {
        PlaceChoice suggestion = new PlaceChoice("external:INSEE:69123", null, "Lyon", "69123", "INSEE", "Commune", null);
        PlaceCreatedResponse.PlaceCreatedItem created = new PlaceCreatedResponse.PlaceCreatedItem(7L, "Lyon", "69123");

        PlaceChoice place = PlaceChoice.created(created, suggestion);

        assertThat(place.getKey()).isEqualTo("7");
        assertThat(place.isExternal()).isFalse();
        assertThat(place.getCategory()).isEqualTo("Commune");
    }
}
