package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.rules.PlaceSourceSpec;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.placesource.ExternalPlace;
import fr.siamois.domain.services.placesource.PlaceSourceConfigResolver;
import fr.siamois.domain.services.placesource.PlaceSourceProvider;
import fr.siamois.domain.services.placesource.PlaceSourceRegistry;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceFromSuggestionRequest;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceSuggestionApiServiceTest {

    private static final long MAIN_LOCATION = -108L;
    private static final long SPATIAL_CONTEXT = -104L;

    @Mock private ProjectApiService projectApiService;
    @Mock private SpatialUnitService spatialUnitService;
    @Mock private InstitutionService institutionService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private PlaceSourceConfigResolver configResolver;
    @Mock private LabelService labelService;
    @Mock private PlaceSourceProvider insee;
    @Mock private PlaceSourceProvider geoplat;

    private PlaceSuggestionApiService service;
    private ProjectApiCaller caller;
    private ConceptDTO communeConcept;

    @BeforeEach
    void setUp() {
        lenient().when(insee.id()).thenReturn("INSEE");
        lenient().when(geoplat.id()).thenReturn("GEOPLAT");
        lenient().when(insee.identifiedByCode()).thenReturn(true);
        communeConcept = new ConceptDTO();
        communeConcept.setId(417L);
        lenient().when(insee.type()).thenReturn(communeConcept);
        lenient().when(geoplat.type()).thenReturn(communeConcept);
        service = new PlaceSuggestionApiService(projectApiService, spatialUnitService, institutionService,
                profilePermissionService, new PlaceSourceRegistry(List.of(insee, geoplat)), configResolver, labelService);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of());
    }

    private void ownPlaces(SpatialUnitDTO... places) {
        lenient().when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByTypesAndByGlobalContaining(
                anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(places)));
        ConceptLabelDTO label = mock(ConceptLabelDTO.class);
        lenient().when(label.getLabel()).thenReturn("Commune");
        lenient().when(labelService.findLabelOf(any(ConceptDTO.class), any())).thenReturn(label);
    }

    private static SpatialUnitDTO place(long id, String name, String code, long organization) {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setCode(code);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(organization);
        dto.setCreatedByInstitution(institution);
        return dto;
    }

    private static PlaceSourceSpec geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing onMissing) {
        return new PlaceSourceSpec("GEOPLAT",
                Map.of("citycode", new PlaceSourceSpec.ParamBinding(MAIN_LOCATION, PlaceSourceSpec.PlaceAttribute.CODE)), onMissing);
    }

    private PlaceSuggestionApiService.Suggestions suggest(String query, Map<Long, Long> deps) {
        return service.suggest(caller, new PlaceSuggestionApiService.SuggestionRequest(10L, SPATIAL_CONTEXT, null, null, query, deps, 20, "fr"));
    }

    @Test
    void suggest_listsOwnPlacesThenCommunes_andDropsCommunesAlreadyKnown() {
        ownPlaces(place(5L, "Lyon", "69123", 10L));
        when(configResolver.forField(MAIN_LOCATION, null, null)).thenReturn(List.of(PlaceSourceSpec.of("INSEE")));
        when(insee.search("lyo", Map.of())).thenReturn(List.of(
                new ExternalPlace("Lyon", "69123", null), new ExternalPlace("Lyonnais", "42000", null)));

        List<PlaceSuggestionApiService.Suggestions> out = List.of(
                service.suggest(caller, new PlaceSuggestionApiService.SuggestionRequest(10L, MAIN_LOCATION, null, null, "lyo", Map.of(), 20, "fr")));

        List<PlaceSuggestionItemApi> items = out.get(0).items();
        assertEquals(2, items.size());
        assertEquals(5L, items.get(0).id());
        assertEquals("SIAMOIS", items.get(0).source());
        assertNull(items.get(1).id());
        assertEquals("INSEE", items.get(1).source());
        assertEquals("42000", items.get(1).code());
        verify(geoplat, never()).search(any(), any());
    }

    @Test
    void suggest_withoutConfiguredSources_neverCallsExternalServices() {
        ownPlaces();
        when(configResolver.forField(anyLong(), any(), any())).thenReturn(List.of());

        assertTrue(suggest("lyon", Map.of()).items().isEmpty());

        verify(insee, never()).search(any(), any());
        verify(geoplat, never()).search(any(), any());
    }

    @Test
    void suggest_feedsASourceParameterFromThePlacePickedInTheOtherField() {
        ownPlaces();
        when(configResolver.forField(SPATIAL_CONTEXT, null, null))
                .thenReturn(List.of(geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.UNFILTERED)));
        when(geoplat.search("rue", Map.of("citycode", "69123"))).thenReturn(List.of(new ExternalPlace("1 rue X, Lyon", null, new FullAddress())));
        when(spatialUnitService.findById(7L)).thenReturn(place(7L, "Lyon", "69123", 10L));

        PlaceSuggestionApiService.Suggestions result = suggest("rue", Map.of(MAIN_LOCATION, 7L));

        assertEquals(1, result.items().size());
        assertEquals("GEOPLAT", result.items().get(0).source());
        assertTrue(result.unnarrowedSources().isEmpty());
    }

    // The default: without the commune the addresses of the whole country are still suggested, with the hint.
    @Test
    void suggest_searchesWithoutTheFilterWhenTheFieldItReadsIsEmpty_andSaysSo() {
        ownPlaces(place(5L, "Rue de la Paix", null, 10L));
        when(configResolver.forField(SPATIAL_CONTEXT, null, null))
                .thenReturn(List.of(geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.UNFILTERED)));
        when(geoplat.search("rue", Map.of())).thenReturn(List.of(new ExternalPlace("1 rue X", null, null)));

        PlaceSuggestionApiService.Suggestions result = suggest("rue", Map.of());

        assertEquals(2, result.items().size());
        assertEquals("GEOPLAT", result.items().get(1).source());
        assertEquals(List.of("GEOPLAT"), result.unnarrowedSources());
    }

    @Test
    void suggest_searchesWithoutTheFilterWhenThePlacePickedHasNoSuchAttribute() {
        ownPlaces();
        when(configResolver.forField(SPATIAL_CONTEXT, null, null))
                .thenReturn(List.of(geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.UNFILTERED)));
        when(spatialUnitService.findById(7L)).thenReturn(place(7L, "Un lieu sans code", null, 10L));
        when(geoplat.search("rue", Map.of())).thenReturn(List.of());

        assertEquals(List.of("GEOPLAT"), suggest("rue", Map.of(MAIN_LOCATION, 7L)).unnarrowedSources());
        verify(geoplat).search("rue", Map.of());
    }

    @Test
    void suggest_leavesTheSourceOutOnlyWhenItIsConfiguredToSkip() {
        ownPlaces(place(5L, "Rue de la Paix", null, 10L));
        when(configResolver.forField(SPATIAL_CONTEXT, null, null))
                .thenReturn(List.of(geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.SKIP)));

        PlaceSuggestionApiService.Suggestions result = suggest("rue", Map.of());

        assertEquals(1, result.items().size());
        assertEquals(List.of("GEOPLAT"), result.unnarrowedSources());
        verify(geoplat, never()).search(any(), any());
    }

    @Test
    void suggest_doesNotFilterByAPlaceOfAnotherOrganization() {
        ownPlaces();
        when(configResolver.forField(SPATIAL_CONTEXT, null, null))
                .thenReturn(List.of(geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.UNFILTERED)));
        when(spatialUnitService.findById(7L)).thenReturn(place(7L, "Lyon", "69123", 99L));
        when(geoplat.search("rue", Map.of())).thenReturn(List.of());

        assertEquals(List.of("GEOPLAT"), suggest("rue", Map.of(MAIN_LOCATION, 7L)).unnarrowedSources());
        verify(geoplat).search("rue", Map.of());
    }

    @Test
    void suggest_doesNotFilterByAPlaceThatIsGone() {
        ownPlaces();
        when(configResolver.forField(SPATIAL_CONTEXT, null, null))
                .thenReturn(List.of(geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.UNFILTERED)));
        when(spatialUnitService.findById(7L)).thenThrow(new SpatialUnitNotFoundException("gone"));
        when(geoplat.search("rue", Map.of())).thenReturn(List.of());

        assertEquals(List.of("GEOPLAT"), suggest("rue", Map.of(MAIN_LOCATION, 7L)).unnarrowedSources());
    }

    // Many places of the organization must not crowd out the sources, nor hide that one needs a field filled.
    @Test
    void suggest_aFullListOfOwnPlacesStillGetsTheSourcesAndTheHint() {
        SpatialUnitDTO[] many = new SpatialUnitDTO[20];
        for (int i = 0; i < many.length; i++) {
            many[i] = place(100L + i, "Lieu " + i, null, 10L);
        }
        ownPlaces(many);
        when(configResolver.forField(SPATIAL_CONTEXT, null, null)).thenReturn(List.of(
                PlaceSourceSpec.of("INSEE"), geoplatBoundToMainLocation(PlaceSourceSpec.OnMissing.UNFILTERED)));
        when(insee.search("lyo", Map.of())).thenReturn(List.of(new ExternalPlace("Lyon", "69123", null)));
        when(geoplat.search("lyo", Map.of())).thenReturn(List.of());

        PlaceSuggestionApiService.Suggestions result = suggest("lyo", Map.of());

        assertEquals(21, result.items().size());
        assertEquals("INSEE", result.items().get(20).source());
        assertEquals(List.of("GEOPLAT"), result.unnarrowedSources());
    }

    @Test
    void suggest_ignoresAnUnknownSourceOfTheConfiguration() {
        ownPlaces();
        when(configResolver.forField(SPATIAL_CONTEXT, null, null)).thenReturn(List.of(PlaceSourceSpec.of("NOPE")));

        assertTrue(suggest("rue", Map.of()).items().isEmpty());
        verify(insee, never()).search(any(), any());
        verify(geoplat, never()).search(any(), any());
    }

    @Test
    void suggest_whenAnExternalServiceFails_keepsOwnPlaces() {
        ownPlaces(place(5L, "Lyon", null, 10L));
        when(configResolver.forField(MAIN_LOCATION, null, null)).thenReturn(List.of(PlaceSourceSpec.of("INSEE")));
        when(insee.search(any(), any())).thenThrow(new IllegalStateException("down"));

        assertEquals(1, service.suggest(caller,
                new PlaceSuggestionApiService.SuggestionRequest(10L, MAIN_LOCATION, null, null, "lyon", Map.of(), 20, "fr")).items().size());
    }

    @Test
    void fromSuggestion_unknownSource_is400() {
        PlaceFromSuggestionRequest request = request("EVIL", "Lyon");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.fromSuggestion(caller, request, "fr"));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void fromSuggestion_withoutRight_is403() {
        when(institutionService.findById(10L)).thenReturn(institution());
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(false);
        when(profilePermissionService.hasActionUnitCreatePermission(any(UserInfo.class))).thenReturn(false);

        PlaceFromSuggestionRequest request = request("INSEE", "Lyon");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.fromSuggestion(caller, request, "fr"));
        assertEquals(403, e.getStatusCode().value());
    }

    @Test
    void fromSuggestion_withProject_needsProjectWriteRight() {
        ActionUnitDTO project = new ActionUnitDTO();
        when(institutionService.findById(10L)).thenReturn(institution());
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), any())).thenReturn(false);
        when(projectApiService.requireAccessibleProject(caller, "3")).thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(profilePermissionService.hasActionUnitWritePermission(any(UserInfo.class), eq(project))).thenReturn(false);
        PlaceFromSuggestionRequest request = request("INSEE", "Lyon");
        request.setProjectId("3");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.fromSuggestion(caller, request, "fr"));
        assertEquals(403, e.getStatusCode().value());
    }

    @Test
    void fromSuggestion_existingPlace_isReturnedWithoutCreating() throws Exception {
        stubAllowedCreation();
        SpatialUnitDTO draft = new SpatialUnitDTO();
        draft.setName("Lyon");
        draft.setCode("69123");
        when(insee.draftOf(any())).thenReturn(draft);
        SpatialUnitDTO existing = place(9L, "Lyon", "69123", 10L);
        when(spatialUnitService.findExistingForSuggestion(10L, "Lyon", "69123", 417L)).thenReturn(Optional.of(existing));
        PlaceFromSuggestionRequest request = request("INSEE", "Lyon");
        request.setCode("69123");

        PlaceCreatedResponse.PlaceCreatedItem item = service.fromSuggestion(caller, request, "fr");

        assertEquals(9L, item.getId());
        verify(spatialUnitService, never()).save(any(), any());
    }

    @Test
    void fromSuggestion_newPlace_isSavedAsTheProviderDraftsIt() throws Exception {
        stubAllowedCreation();
        SpatialUnitDTO draft = new SpatialUnitDTO();
        draft.setName("Lyon");
        draft.setCode("69123");
        draft.setType(communeConcept);
        when(insee.draftOf(new ExternalPlace("Lyon", "69123", null))).thenReturn(draft);
        when(spatialUnitService.findExistingForSuggestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        SpatialUnitDTO saved = place(11L, "Lyon", "69123", 10L);
        when(spatialUnitService.save(any(), any())).thenReturn(saved);
        PlaceFromSuggestionRequest request = request("insee", " Lyon ");
        request.setCode("69123");

        PlaceCreatedResponse.PlaceCreatedItem item = service.fromSuggestion(caller, request, "fr");

        assertEquals(11L, item.getId());
        ArgumentCaptor<SpatialUnitDTO> captor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(), captor.capture());
        assertSame(draft, captor.getValue());
    }

    private void stubAllowedCreation() {
        when(institutionService.findById(10L)).thenReturn(institution());
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), any())).thenReturn(true);
    }

    private static InstitutionDTO institution() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        return institution;
    }

    private static PlaceFromSuggestionRequest request(String source, String name) {
        PlaceFromSuggestionRequest request = new PlaceFromSuggestionRequest();
        request.setOrganizationId(10L);
        request.setSource(source);
        request.setName(name);
        return request;
    }
}
