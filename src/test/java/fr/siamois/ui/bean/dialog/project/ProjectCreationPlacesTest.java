package fr.siamois.ui.bean.dialog.project;

import fr.siamois.ui.api.openapi.v1.request.place.PlaceFromSuggestionRequest;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi;
import fr.siamois.ui.api.openapi.v1.service.PlaceSuggestionApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The places of the new project dialog go through the services of the REST API {@code /api/v1/places}. */
class ProjectCreationPlacesTest {

    private static final String LANG = "fr";
    private static final PlaceField COMMUNE = new PlaceField(10L, "Commune");

    private final PlaceSuggestionApiService placeSuggestionApiService = mock(PlaceSuggestionApiService.class);
    private final ProjectApiService projectApiService = mock(ProjectApiService.class);
    private final ProjectApiCaller caller = mock(ProjectApiCaller.class);
    private ProjectCreationPlaces places;

    @BeforeEach
    void setUp() {
        places = new ProjectCreationPlaces(mock(RecordingUnitOpenApiService.class), placeSuggestionApiService, projectApiService);
        when(projectApiService.requireCaller()).thenReturn(caller);
    }

    @Test
    void suggest_shouldAskTheSuggestionsOfTheField_narrowedByItsDependencies() {
        when(placeSuggestionApiService.suggest(eq(caller), any())).thenReturn(new PlaceSuggestionApiService.Suggestions(
                List.of(new PlaceSuggestionItemApi(null, "Lyon", "69123", "INSEE", null, null)), List.of()));

        PlaceSuggestions suggested = places.suggest(5L, COMMUNE, " ly ", Map.of(9L, 4L), LANG);

        ArgumentCaptor<PlaceSuggestionApiService.SuggestionRequest> request =
                ArgumentCaptor.forClass(PlaceSuggestionApiService.SuggestionRequest.class);
        verify(placeSuggestionApiService).suggest(eq(caller), request.capture());
        assertThat(request.getValue().organizationId()).isEqualTo(5L);
        assertThat(request.getValue().fieldId()).isEqualTo(10L);
        assertThat(request.getValue().query()).isEqualTo("ly");
        assertThat(request.getValue().deps()).isEqualTo(Map.of(9L, 4L));
        assertThat(suggested.getChoices()).extracting(PlaceChoice::getKey).containsExactly("external:INSEE:69123");
        assertThat(suggested.isNarrowed()).isTrue();
    }

    @Test
    void suggest_shouldSayTheSuggestionsWereNotNarrowed_whenASourceLacksTheFieldItDependsOn() {
        when(placeSuggestionApiService.suggest(eq(caller), any()))
                .thenReturn(new PlaceSuggestionApiService.Suggestions(List.of(), List.of("GEOPLAT")));

        assertThat(places.suggest(5L, COMMUNE, "rue", Map.of(), LANG).isNarrowed()).isFalse();
    }

    @Test
    void placeOf_shouldKeepAPlaceOfTheOrganisationAsItIs() {
        PlaceChoice lyon = new PlaceChoice("4", 4L, "Lyon", "69123", "SIAMOIS", null, null);

        assertThat(places.placeOf(5L, lyon, LANG)).isSameAs(lyon);
        verifyNoInteractions(placeSuggestionApiService);
    }

    @Test
    void placeOf_shouldCreateThePlaceOfASuggestion() {
        PlaceChoice suggestion = new PlaceChoice("external:INSEE:69123", null, "Lyon", "69123", "INSEE", "Commune", null);
        when(placeSuggestionApiService.fromSuggestion(eq(caller), any(), eq(LANG)))
                .thenReturn(new PlaceCreatedResponse.PlaceCreatedItem(7L, "Lyon", "69123"));

        PlaceChoice place = places.placeOf(5L, suggestion, LANG);

        ArgumentCaptor<PlaceFromSuggestionRequest> request = ArgumentCaptor.forClass(PlaceFromSuggestionRequest.class);
        verify(placeSuggestionApiService).fromSuggestion(eq(caller), request.capture(), eq(LANG));
        assertThat(request.getValue().getOrganizationId()).isEqualTo(5L);
        assertThat(request.getValue().getSource()).isEqualTo("INSEE");
        assertThat(request.getValue().getCode()).isEqualTo("69123");
        assertThat(request.getValue().getProjectId()).isNull();
        assertThat(place.getId()).isEqualTo(7L);
    }
}
