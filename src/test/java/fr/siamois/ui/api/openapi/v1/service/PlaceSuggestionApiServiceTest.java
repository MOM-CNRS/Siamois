package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.GeoApiService;
import fr.siamois.domain.services.GeoPlatService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceSuggestionApiServiceTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private SpatialUnitService spatialUnitService;
    @Mock private InstitutionService institutionService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private GeoApiService geoApiService;
    @Mock private GeoPlatService geoPlatService;
    @Mock private ConceptRepository conceptRepository;
    @Mock private ConceptMapper conceptMapper;
    @Mock private LabelService labelService;

    private PlaceSuggestionApiService service;
    private ProjectApiCaller caller;
    private ConceptDTO communeConcept;

    @BeforeEach
    void setUp() {
        service = new PlaceSuggestionApiService(projectApiService, spatialUnitService, institutionService,
                profilePermissionService, geoApiService, geoPlatService, conceptRepository, conceptMapper, labelService);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of());
        communeConcept = new ConceptDTO();
        communeConcept.setId(417L);
    }

    @Test
    void parseSources_keepsKnownSourcesOnly() {
        assertEquals(List.of("INSEE", "GEOPLAT"), PlaceSuggestionApiService.parseSources(" insee, GEOPLAT ,insee,EVIL"));
        assertEquals(List.of(), PlaceSuggestionApiService.parseSources(null));
        assertEquals(List.of(), PlaceSuggestionApiService.parseSources("  "));
    }

    @Test
    void suggest_listsOwnPlacesThenCommunes_andDropsCommunesAlreadyKnown() {
        SpatialUnitDTO lyon = new SpatialUnitDTO();
        lyon.setId(5L);
        lyon.setName("Lyon");
        lyon.setCode("69123");
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq("lyo"), any(), any(), any(), eq("fr"), any())).thenReturn(new PageImpl<>(List.of(lyon)));
        when(geoApiService.fetchCommunes("lyo")).thenReturn(List.of(commune("Lyon", "69123"), commune("Lyonnais", "42000")));

        List<PlaceSuggestionItemApi> items = service.suggest(caller, 10L, "lyo", List.of("INSEE"), 20, "fr");

        assertEquals(2, items.size());
        assertEquals(5L, items.get(0).id());
        assertEquals("SIAMOIS", items.get(0).source());
        assertNull(items.get(1).id());
        assertEquals("INSEE", items.get(1).source());
        assertEquals("42000", items.get(1).code());
        verifyNoInteractions(geoPlatService);
    }

    @Test
    void suggest_withoutSources_neverCallsExternalServices() {
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.suggest(caller, 10L, "lyon", List.of(), 20, "fr").isEmpty());

        verifyNoInteractions(geoApiService, geoPlatService);
    }

    @Test
    void suggest_whenAnExternalServiceFails_keepsOwnPlaces() {
        SpatialUnitDTO lyon = new SpatialUnitDTO();
        lyon.setId(5L);
        lyon.setName("Lyon");
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(lyon)));
        when(geoApiService.fetchCommunes(any())).thenThrow(new IllegalStateException("down"));

        assertEquals(1, service.suggest(caller, 10L, "lyon", List.of("INSEE"), 20, "fr").size());
    }

    @Test
    void suggest_geoplatNeedsThreeCharacters() {
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.suggest(caller, 10L, "ru", List.of("GEOPLAT"), 20, "fr");

        verifyNoInteractions(geoPlatService);
    }

    @Test
    void fromSuggestion_unknownSource_is400() {
        PlaceFromSuggestionRequest request = request("EVIL", "Lyon");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.fromSuggestion(caller, request, "fr"));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void fromSuggestion_withoutRight_is403() {
        InstitutionDTO institution = institution();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(false);
        when(profilePermissionService.hasActionUnitCreatePermission(any(UserInfo.class))).thenReturn(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.fromSuggestion(caller, request("INSEE", "Lyon"), "fr"));
        assertEquals(403, e.getStatusCode().value());
    }

    @Test
    void fromSuggestion_withProject_needsProjectWriteRight() {
        InstitutionDTO institution = institution();
        ActionUnitDTO project = new ActionUnitDTO();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), any())).thenReturn(false);
        when(projectApiService.requireAccessibleProject(caller, "3")).thenReturn(
                new AccessibleProjectForApi(project, 0L, 0L));
        when(profilePermissionService.hasActionUnitWritePermission(any(UserInfo.class), eq(project))).thenReturn(false);
        PlaceFromSuggestionRequest request = request("INSEE", "Lyon");
        request.setProjectId("3");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.fromSuggestion(caller, request, "fr"));
        assertEquals(403, e.getStatusCode().value());
    }

    @Test
    void fromSuggestion_existingPlace_isReturnedWithoutCreating() throws Exception {
        stubAllowedCreation();
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(9L);
        existing.setName("Lyon");
        existing.setCode("69123");
        when(spatialUnitService.findExistingForSuggestion(10L, "Lyon", "69123", 417L)).thenReturn(Optional.of(existing));
        PlaceFromSuggestionRequest request = request("INSEE", "Lyon");
        request.setCode("69123");

        PlaceCreatedResponse.PlaceCreatedItem item = service.fromSuggestion(caller, request, "fr");

        assertEquals(9L, item.getId());
        verify(spatialUnitService, never()).save(any(), any());
    }

    @Test
    void fromSuggestion_newCommune_isSavedWithItsInseeCodeAndTheCommuneCategory() throws Exception {
        stubAllowedCreation();
        when(spatialUnitService.findExistingForSuggestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        SpatialUnitDTO saved = new SpatialUnitDTO();
        saved.setId(11L);
        saved.setName("Lyon");
        saved.setCode("69123");
        when(spatialUnitService.save(any(), any())).thenReturn(saved);
        PlaceFromSuggestionRequest request = request("INSEE", " Lyon ");
        request.setCode("69123");

        PlaceCreatedResponse.PlaceCreatedItem item = service.fromSuggestion(caller, request, "fr");

        assertEquals(11L, item.getId());
        ArgumentCaptor<SpatialUnitDTO> captor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(), captor.capture());
        assertEquals("Lyon", captor.getValue().getName());
        assertEquals("69123", captor.getValue().getCode());
        assertEquals(417L, captor.getValue().getCategory().getId());
        assertNull(captor.getValue().getAddress());
    }

    @Test
    void fromSuggestion_address_keepsTheAddressAndNoCode() throws Exception {
        stubAllowedCreation();
        when(spatialUnitService.findExistingForSuggestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        SpatialUnitDTO saved = new SpatialUnitDTO();
        saved.setId(12L);
        when(spatialUnitService.save(any(), any())).thenReturn(saved);
        PlaceFromSuggestionRequest request = request("GEOPLAT", "1 rue de la Paix 75002 Paris");
        FullAddress address = new FullAddress();
        address.setLabel("1 rue de la Paix 75002 Paris");
        request.setAddress(address);
        request.setCode("ignored");

        service.fromSuggestion(caller, request, "fr");

        ArgumentCaptor<SpatialUnitDTO> captor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(), captor.capture());
        assertSame(address, captor.getValue().getAddress());
        assertNull(captor.getValue().getCode());
    }

    private void stubAllowedCreation() {
        when(institutionService.findById(10L)).thenReturn(institution());
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), any())).thenReturn(true);
        when(conceptRepository.findConceptByExternalIdIgnoreCase(eq("th252"), anyString())).thenReturn(Optional.of(new Concept()));
        when(conceptMapper.convert(any(Concept.class))).thenReturn(communeConcept);
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

    private static PlaceSuggestionDTO commune(String name, String code) {
        PlaceSuggestionDTO dto = new PlaceSuggestionDTO();
        dto.setName(name);
        dto.setCode(code);
        dto.setSourceName("INSEE");
        return dto;
    }
}
