package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpatialUnitSpec;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private PlaceOpenApiMapper placeOpenApiMapper;
    @Mock
    private LangService langService;

    private PlaceOpenApiService service;
    private ProjectApiCaller caller;
    private PersonDTO personDto;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        // The place detail counts its projects for the fiche's tab badge.
        lenient().when(projectApiService.pageAccessibleProjects(any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service = new PlaceOpenApiService(
                projectApiService,
                institutionService,
                spatialUnitService,
                conceptService,
                conceptMapper,
                profilePermissionService,
                placeOpenApiMapper,
                langService, mock(ResourceBookmarkService.class), mock(EntitySiblingsService.class), org.mockito.Mockito.mock(fr.siamois.ui.api.openapi.v1.service.ValidationOpenApiService.class));

        personDto = new PersonDTO();
        personDto.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);
        caller = new ProjectApiCaller(personDto, SCOPE, List.of());
    }

    @Test
    void listByOrganization_success_setsPermissionsAndResourceUri() {
        when(institutionService.findById(10L)).thenReturn(institution);

        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(5L);
        dto.setName("Cave A");
        dto.setCreatedByInstitution(institution);
        Page<SpatialUnitDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 50), 1);
        when(spatialUnitService.searchSpatialUnits(eq(institution), any(), any())).thenReturn(page);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);

        PlaceResource resource = new PlaceResource();
        resource.setResourceType("places");
        resource.setId("5");
        resource.setName("Cave A");
        when(placeOpenApiMapper.toResource(eq(dto), any())).thenReturn(resource);

        PlaceListResponse response = service.listByOrganization(caller, 10L, 0, 50, "name:asc", null, "fr");

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getName()).isEqualTo("Cave A");
        assertThat(response.getData().get(0).getPermissions().canEdit()).isTrue();
        assertThat(response.getData().get(0).getResourceUri()).isEqualTo("/spatial-unit/5");
        assertThat(response.getMeta().total()).isEqualTo(1L);
        verify(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
    }

    @Test
    void listByOrganization_search_filtersOnName() {
        when(institutionService.findById(10L)).thenReturn(institution);
        when(spatialUnitService.searchSpatialUnits(eq(institution), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        service.listByOrganization(caller, 10L, 0, 50, "name:asc", "cave", "fr");

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        verify(spatialUnitService).searchSpatialUnits(eq(institution), filter.capture(), any());
        assertThat(filter.getValue().valueOfAsString(SpatialUnitSpec.NAME_FILTER)).isEqualTo("cave");
    }

    @Test
    void listByOrganization_missingOrganizationId_throws400() {
        assertThatThrownBy(() -> service.listByOrganization(caller, null, 0, 50, "name:asc", null, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void listByOrganization_unknownOrg_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.listByOrganization(caller, 10L, 0, 50, "name:asc", null, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void createPlace_success() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("  Rue du Temple  ");
        request.setTypeConceptId(42L);

        Concept concept = new Concept();
        ConceptDTO category = new ConceptDTO();
        category.setId(42L);
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        when(conceptService.findById(42L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(category);

        SpatialUnitDTO saved = new SpatialUnitDTO();
        saved.setId(99L);
        saved.setName("Rue du Temple");
        saved.setCode("L-99");
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class))).thenReturn(saved);

        PlaceCreatedResponse.PlaceCreatedItem item = service.createPlace(caller, request, "fr");

        assertThat(item.getId()).isEqualTo(99L);
        assertThat(item.getName()).isEqualTo("Rue du Temple");
        assertThat(item.getCode()).isEqualTo("L-99");

        ArgumentCaptor<SpatialUnitDTO> dtoCaptor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(UserInfo.class), dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getName()).isEqualTo("Rue du Temple");
        assertThat(dtoCaptor.getValue().getCategory().getId()).isEqualTo(42L);
        verify(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
    }

    @Test
    void createPlace_withoutWritePermission_throws403() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Lieu");
        request.setTypeConceptId(42L);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(false);

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));

        verify(spatialUnitService, never()).save(any(), any());
    }

    @Test
    void createPlace_duplicateName_throws409() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Doublon");
        request.setTypeConceptId(42L);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        Concept concept = new Concept();
        when(conceptService.findById(42L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(new ConceptDTO());
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class)))
                .thenThrow(new SpatialUnitAlreadyExistsException("identifier", "already exists"));

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void createPlace_missingName_throws400() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("   ");
        request.setTypeConceptId(42L);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void updatePlace_success() throws SpatialUnitAlreadyExistsException {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setName("Ancien");
        existing.setCreatedByInstitution(institution);

        PlacePatchRequest patch = new PlacePatchRequest();
        patch.setName("Nouveau");
        patch.setTypeConceptId(42L);

        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        Concept concept = new Concept();
        ConceptDTO category = new ConceptDTO();
        category.setId(42L);
        when(conceptService.findById(42L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(category);

        SpatialUnitDTO saved = new SpatialUnitDTO();
        saved.setId(5L);
        saved.setName("Nouveau");
        saved.setCode("L-5");
        when(spatialUnitService.updatePlace(any(UserInfo.class), eq(5L), eq("Nouveau"), eq(category), eq(null)))
                .thenReturn(saved);

        PlaceCreatedResponse.PlaceCreatedItem item = service.updatePlace(caller, 5L, patch, "fr");

        assertThat(item.getId()).isEqualTo(5L);
        assertThat(item.getName()).isEqualTo("Nouveau");
        verify(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
    }

    @Test
    void updatePlace_notFound_throws404() {
        when(spatialUnitService.findById(99L))
                .thenThrow(new SpatialUnitNotFoundException("missing"));

        PlacePatchRequest patch = new PlacePatchRequest();
        patch.setName("X");

        assertThatThrownBy(() -> service.updatePlace(caller, 99L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void updatePlace_withoutWritePermission_throws403() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(false);

        PlacePatchRequest patch = new PlacePatchRequest();
        assertThatThrownBy(() -> service.updatePlace(caller, 5L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void updatePlace_duplicateName_throws409() throws SpatialUnitAlreadyExistsException {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        PlacePatchRequest patch = new PlacePatchRequest();
        patch.setName("Doublon");
        when(spatialUnitService.updatePlace(any(UserInfo.class), eq(5L), eq("Doublon"), eq(null), eq(null)))
                .thenThrow(new SpatialUnitAlreadyExistsException("identifier", "exists"));

        assertThatThrownBy(() -> service.updatePlace(caller, 5L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void deletePlace_success() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        service.deletePlace(caller, 5L, "fr");

        verify(spatialUnitService).deleteIfUnused(5L);
        verify(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
    }

    @Test
    void deletePlace_inUse_throws409() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        doThrow(new IllegalStateException("Impossible de supprimer : le lieu possède des lieux enfants"))
                .when(spatialUnitService).deleteIfUnused(5L);

        assertThatThrownBy(() -> service.deletePlace(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void deletePlace_withoutWritePermission_throws403() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(false);

        assertThatThrownBy(() -> service.deletePlace(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));

        verify(spatialUnitService, never()).deleteIfUnused(anyLong());
    }

    @Test
    void createPlace_nullRequest_throws400() {
        assertThatThrownBy(() -> service.createPlace(caller, null, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void createPlace_nullOrganizationId_throws400() {
        PlaceCreateRequest request = new PlaceCreateRequest();

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void createPlace_organizationNotFound_throws404() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void createPlace_asActionManager_success() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Lieu");
        request.setTypeConceptId(42L);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        Concept concept = new Concept();
        when(conceptService.findById(42L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(new ConceptDTO());

        SpatialUnitDTO saved = new SpatialUnitDTO();
        saved.setId(1L);
        saved.setName("Lieu");
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class))).thenReturn(saved);

        PlaceCreatedResponse.PlaceCreatedItem item = service.createPlace(caller, request, "fr");

        assertThat(item.getId()).isEqualTo(1L);
    }

    @Test
    void createPlace_nullName_throws400() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setTypeConceptId(42L);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void createPlace_missingTypeConceptId_throws400() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Lieu");

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void createPlace_typeNotFound_throws404() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Lieu");
        request.setTypeConceptId(99L);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        when(conceptService.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void createPlace_withAddress_passesAddressToSave() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Lieu");
        request.setTypeConceptId(42L);
        FullAddress address = new FullAddress();
        address.setCity("Paris");
        request.setAddress(address);

        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        Concept concept = new Concept();
        when(conceptService.findById(42L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(new ConceptDTO());
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class)))
                .thenReturn(new SpatialUnitDTO());

        service.createPlace(caller, request, "fr");

        ArgumentCaptor<SpatialUnitDTO> captor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(UserInfo.class), captor.capture());
        assertThat(captor.getValue().getAddress()).isSameAs(address);
    }

    @Test
    void updatePlace_nullPatch_success() throws SpatialUnitAlreadyExistsException {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setName("Inchangé");
        existing.setCreatedByInstitution(institution);

        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        when(spatialUnitService.updatePlace(any(UserInfo.class), eq(5L), eq(null), eq(null), eq(null)))
                .thenReturn(existing);

        PlaceCreatedResponse.PlaceCreatedItem item = service.updatePlace(caller, 5L, null, "fr");

        assertThat(item.getName()).isEqualTo("Inchangé");
    }

    @Test
    void updatePlace_typeNotFound_throws404() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        when(conceptService.findById(99L)).thenReturn(Optional.empty());

        PlacePatchRequest patch = new PlacePatchRequest();
        patch.setTypeConceptId(99L);

        assertThatThrownBy(() -> service.updatePlace(caller, 5L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void updatePlace_emptyName_throws400() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        PlacePatchRequest patch = new PlacePatchRequest();
        patch.setName("   ");

        assertThatThrownBy(() -> service.updatePlace(caller, 5L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void updatePlace_withAddress_passesAddressToUpdate() throws SpatialUnitAlreadyExistsException {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        FullAddress address = new FullAddress();
        address.setCity("Lyon");
        PlacePatchRequest patch = new PlacePatchRequest();
        patch.setAddress(address);

        SpatialUnitDTO saved = new SpatialUnitDTO();
        saved.setId(5L);
        saved.setName("Lieu");
        when(spatialUnitService.updatePlace(any(UserInfo.class), eq(5L), eq(null), eq(null), eq(address)))
                .thenReturn(saved);

        service.updatePlace(caller, 5L, patch, "fr");

        verify(spatialUnitService).updatePlace(any(UserInfo.class), eq(5L), eq(null), eq(null), eq(address));
    }

    @Test
    void updatePlace_asActionManager_success() throws SpatialUnitAlreadyExistsException {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        when(spatialUnitService.updatePlace(any(UserInfo.class), eq(5L), eq(null), eq(null), eq(null)))
                .thenReturn(existing);

        PlacePatchRequest patch = new PlacePatchRequest();
        service.updatePlace(caller, 5L, patch, "fr");

        verify(spatialUnitService).updatePlace(any(UserInfo.class), eq(5L), eq(null), eq(null), eq(null));
    }

    @Test
    void updatePlace_withoutOrganization_throws400() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        when(spatialUnitService.findById(5L)).thenReturn(existing);

        PlacePatchRequest patch = new PlacePatchRequest();

        assertThatThrownBy(() -> service.updatePlace(caller, 5L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void updatePlace_organizationWithoutId_throws400() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(new InstitutionDTO());
        when(spatialUnitService.findById(5L)).thenReturn(existing);

        PlacePatchRequest patch = new PlacePatchRequest();

        assertThatThrownBy(() -> service.updatePlace(caller, 5L, patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void deletePlace_notFoundOnDelete_throws404() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        doThrow(new SpatialUnitNotFoundException("missing"))
                .when(spatialUnitService).deleteIfUnused(5L);

        assertThatThrownBy(() -> service.deletePlace(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void deletePlace_asActionManager_success() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);

        service.deletePlace(caller, 5L, "fr");

        verify(spatialUnitService).deleteIfUnused(5L);
    }

    // --- getPlaceById ---

    @Test
    void getPlaceById_success_setsAnswersFormBundleAndUri() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setName("Cave A");
        existing.setCode("L-5");
        existing.setPlaceNumber(3);
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);

        PlaceResource mapped = new PlaceResource();
        mapped.setResourceType("places");
        mapped.setId("5");
        mapped.setName("Cave A");
        when(placeOpenApiMapper.toResource(existing, "fr")).thenReturn(mapped);
        when(langService.localeForApiLang("fr")).thenReturn(Locale.FRENCH);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(false);

        PlaceResource result = service.getPlaceById(caller, 5L, "fr");

        assertThat(result.getFormBundle()).isNotNull();
        assertThat(result.getFields()).isNotEmpty();
        assertThat(result.getAnswers())
                .containsEntry(String.valueOf(SpatialUnit.NAME_FIELD.getId()), "Cave A")
                .containsEntry(String.valueOf(SpatialUnit.CODE_FIELD.getId()), "L-5")
                .containsEntry(String.valueOf(SpatialUnit.PLACE_NUMBER_FIELD.getId()), 3);
        assertThat(result.getResourceUri()).isEqualTo("/spatial-unit/5");
        assertThat(result.getPermissions().canEdit()).isFalse();
        assertThat(result.getCount().getChildren()).isZero();
        assertThat(result.getCount().getProjects()).isZero();
    }

    @Test
    void getPlaceById_countsChildrenAndProjectsWithTheTabsOwnQueries() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setName("Cave A");
        existing.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        PlaceResource mapped = new PlaceResource();
        mapped.setId("5");
        when(placeOpenApiMapper.toResource(existing, "fr")).thenReturn(mapped);
        when(langService.localeForApiLang("fr")).thenReturn(Locale.FRENCH);
        when(spatialUnitService.countSearchResultsInSpatialUnit(eq(institution), eq(existing), any())).thenReturn(4);
        when(projectApiService.pageAccessibleProjects(eq(caller), eq(institution.getId()), isNull(), eq(0), eq(1), isNull(),
                argThat(filter -> filter.conceptManyInFilters().get("spatialContext").equals(java.util.List.of(5L)))))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(),
                        org.springframework.data.domain.PageRequest.of(0, 1), 7L));

        PlaceResource result = service.getPlaceById(caller, 5L, "fr");

        assertThat(result.getCount().getChildren()).isEqualTo(4L);
        assertThat(result.getCount().getProjects()).isEqualTo(7L);
    }

    @Test
    void getPlaceById_includesTypeAnswerFromResolvedType() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        existing.setCreatedByInstitution(institution);
        ConceptDTO category = new ConceptDTO();
        category.setId(42L);
        existing.setCategory(category);
        when(spatialUnitService.findById(5L)).thenReturn(existing);

        PlaceResource mapped = new PlaceResource();
        mapped.setResourceType("places");
        mapped.setId("5");
        ResolvedConceptResource typeRef = new ResolvedConceptResource();
        typeRef.setResourceType("concepts");
        typeRef.setId("42");
        typeRef.setResolvedLabel("Grotte");
        mapped.setType(typeRef);
        when(placeOpenApiMapper.toResource(existing, "fr")).thenReturn(mapped);
        when(langService.localeForApiLang("fr")).thenReturn(Locale.FRENCH);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);

        PlaceResource result = service.getPlaceById(caller, 5L, "fr");

        assertThat(result.getAnswers().get(String.valueOf(SpatialUnit.SPATIAL_UNIT_TYPE_FIELD.getId())))
                .isInstanceOf(fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef.class);
        assertThat(result.getPermissions().canEdit()).isTrue();
    }

    @Test
    void getPlaceById_notFound_throws404() {
        when(spatialUnitService.findById(99L))
                .thenThrow(new SpatialUnitNotFoundException("missing"));

        assertThatThrownBy(() -> service.getPlaceById(caller, 99L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void getPlaceById_outsideCallerScope_throws() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        InstitutionDTO otherInstitution = new InstitutionDTO();
        otherInstitution.setId(999L);
        existing.setCreatedByInstitution(otherInstitution);
        when(spatialUnitService.findById(5L)).thenReturn(existing);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation hors périmètre"))
                .when(projectApiService).assertOrganizationInCallerScope(999L, SCOPE);

        assertThatThrownBy(() -> service.getPlaceById(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void getPlaceById_withoutOrganization_throws400() {
        SpatialUnitDTO existing = new SpatialUnitDTO();
        existing.setId(5L);
        when(spatialUnitService.findById(5L)).thenReturn(existing);

        assertThatThrownBy(() -> service.getPlaceById(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    private PlaceCreateRequest linkedCreateRequest() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Cave");
        request.setTypeConceptId(42L);
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        Concept concept = new Concept();
        when(conceptService.findById(42L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(new ConceptDTO());
        return request;
    }

    private SpatialUnitDTO placeIn(long id, long institutionId, String name) {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(id);
        dto.setName(name);
        InstitutionDTO owner = new InstitutionDTO();
        owner.setId(institutionId);
        dto.setCreatedByInstitution(owner);
        return dto;
    }

    @Test
    void createPlace_withParentPlace_createsItAsThatPlacesChild() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = linkedCreateRequest();
        request.setParentPlaceId(8L);
        when(spatialUnitService.findById(8L)).thenReturn(placeIn(8L, 10L, "Site"));
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class))).thenReturn(new SpatialUnitDTO());

        service.createPlace(caller, request, "fr");

        ArgumentCaptor<SpatialUnitDTO> captor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(UserInfo.class), captor.capture());
        assertThat(captor.getValue().getParents()).extracting("id").containsExactly(8L);
        assertThat(captor.getValue().getChildren()).isNull();
    }

    @Test
    void createPlace_withChildPlace_createsItAsThatPlacesParent() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = linkedCreateRequest();
        request.setChildPlaceId(9L);
        when(spatialUnitService.findById(9L)).thenReturn(placeIn(9L, 10L, "Fosse"));
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class))).thenReturn(new SpatialUnitDTO());

        service.createPlace(caller, request, "fr");

        ArgumentCaptor<SpatialUnitDTO> captor = ArgumentCaptor.forClass(SpatialUnitDTO.class);
        verify(spatialUnitService).save(any(UserInfo.class), captor.capture());
        assertThat(captor.getValue().getChildren()).extracting("id").containsExactly(9L);
    }

    @Test
    void createPlace_linkedToAPlaceOfAnotherOrganization_throws400() throws SpatialUnitAlreadyExistsException {
        PlaceCreateRequest request = linkedCreateRequest();
        request.setParentPlaceId(8L);
        when(spatialUnitService.findById(8L)).thenReturn(placeIn(8L, 11L, "Ailleurs"));

        assertThatThrownBy(() -> service.createPlace(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(spatialUnitService, never()).save(any(), any());
    }

    @Test
    void duplicatePlace_copiesFieldsAndParents_withTheFirstFreeNumberedName() throws SpatialUnitAlreadyExistsException {
        SpatialUnitDTO source = placeIn(5L, 10L, "Site");
        SpatialUnitSummaryDTO parent = new SpatialUnitSummaryDTO(placeIn(1L, 10L, "Commune"));
        source.setParents(new java.util.HashSet<>(Set.of(parent)));
        source.setChildren(new java.util.HashSet<>(Set.of(new SpatialUnitSummaryDTO(placeIn(6L, 10L, "Enfant")))));
        when(spatialUnitService.findById(5L)).thenReturn(source);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(true);
        SpatialUnitDTO saved = placeIn(7L, 10L, "Site (2)");
        java.util.List<String> triedNames = new java.util.ArrayList<>();
        when(spatialUnitService.save(any(UserInfo.class), any(SpatialUnitDTO.class))).thenAnswer(inv -> {
            SpatialUnitDTO dto = inv.getArgument(1);
            triedNames.add(dto.getName());
            assertThat(dto.getParents()).containsExactly(parent);
            assertThat(dto.getChildren()).isNull();
            if (triedNames.size() == 1) throw new SpatialUnitAlreadyExistsException("identifier", "taken");
            return saved;
        });

        PlaceCreatedResponse.PlaceCreatedItem item = service.duplicatePlace(caller, 5L, "fr");

        assertThat(item.getId()).isEqualTo(7L);
        assertThat(triedNames).containsExactly("Site (1)", "Site (2)");
    }

    @Test
    void duplicatePlace_withoutPlaceManagement_throws403() throws SpatialUnitAlreadyExistsException {
        when(spatialUnitService.findById(5L)).thenReturn(placeIn(5L, 10L, "Site"));
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES))).thenReturn(false);

        assertThatThrownBy(() -> service.duplicatePlace(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(spatialUnitService, never()).save(any(), any());
    }
}
