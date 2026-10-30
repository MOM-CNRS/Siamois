package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.TreePage;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.list.ListWindow;
import fr.siamois.ui.api.openapi.v1.request.list.TreeRequest;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The places' tree view as the API serves it: the roots of the organization-wide list, the children of one
 * place, and what each row and the page carry (the flags, the match count).
 */
@ExtendWith(MockitoExtension.class)
class PlaceOpenApiServiceTreeTest {

    private static final Set<Long> SCOPE = Set.of(10L);
    private static final ListWindow WINDOW = new ListWindow(0, 50, "name:asc", "cave");

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
    private InstitutionDTO institution;
    private SpatialUnitDTO row;

    @BeforeEach
    void setUp() {
        service = new PlaceOpenApiService(
                projectApiService, institutionService, spatialUnitService, conceptService, conceptMapper,
                profilePermissionService, placeOpenApiMapper, langService,
                new fr.siamois.domain.services.vocabulary.FieldTextService(
                mock(fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository.class),
                mock(fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository.class),
                mock(fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository.class)),
                mock(ResourceBookmarkService.class),
                mock(EntitySiblingsService.class), mock(ValidationOpenApiService.class),
                mock(fr.siamois.domain.services.document.DocumentService.class));
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, SCOPE, List.of());
        institution = new InstitutionDTO();
        institution.setId(10L);
        row = new SpatialUnitDTO();
        row.setId(5L);
        row.setName("Cave A");
        row.setCreatedByInstitution(institution);
        lenient().when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);
        lenient().when(placeOpenApiMapper.toResource(eq(row), any())).thenAnswer(inv -> {
            PlaceResource resource = new PlaceResource();
            resource.setId("5");
            resource.setName("Cave A");
            return resource;
        });
    }

    private Page<SpatialUnitDTO> pageOfRow() {
        return new PageImpl<>(List.of(row), PageRequest.of(0, 50), 1);
    }

    // ---------- roots of the organization-wide list ----------

    @Test
    void roots_askForRootsOnlyAndFlagEachRow() {
        when(institutionService.findById(10L)).thenReturn(institution);
        when(spatialUnitService.searchSpatialUnits(eq(institution), any(FilterDTO.class), any(Pageable.class))).thenReturn(pageOfRow());
        // 5 has children to show; only 7 matched the search, so 5 is context.
        when(spatialUnitService.treeFlags(eq(List.of(5L)), any(FilterDTO.class)))
                .thenReturn(new SpatialUnitService.TreeFlags(Set.of(5L), Set.of(7L)));

        PlaceListResponse response = service.listByOrganization(
                caller, 10L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null));

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        verify(spatialUnitService).searchSpatialUnits(eq(institution), filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().isRootOnly()).isTrue();
        assertThat(response.getData().get(0).getHasChildren()).isTrue();
        assertThat(response.getData().get(0).getMatch()).isFalse();
    }

    @Test
    void roots_withoutTree_carryNoFlags() {
        when(institutionService.findById(10L)).thenReturn(institution);
        when(spatialUnitService.searchSpatialUnits(eq(institution), any(FilterDTO.class), any(Pageable.class))).thenReturn(pageOfRow());

        PlaceListResponse response = service.listByOrganization(
                caller, 10L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.OFF);

        assertThat(response.getData().get(0).getHasChildren()).isNull();
        assertThat(response.getData().get(0).getMatch()).isNull();
        verify(spatialUnitService, never()).treeFlags(any(), any());
    }

    @Test
    void roots_theRootsMetaCarriesTheMatchCountOfTheTreePage() {
        when(institutionService.findById(10L)).thenReturn(institution);
        when(spatialUnitService.searchSpatialUnits(eq(institution), any(FilterDTO.class), any(Pageable.class)))
                .thenReturn(new TreePage<>(List.of(row), PageRequest.of(0, 50), 1, 37));
        when(spatialUnitService.treeFlags(any(), any(FilterDTO.class)))
                .thenReturn(new SpatialUnitService.TreeFlags(Set.of(), null));

        PlaceListResponse response = service.listByOrganization(
                caller, 10L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null));

        assertThat(response.getMeta().total()).isEqualTo(1L);
        assertThat(response.getMeta().matchCount()).isEqualTo(37L);
    }

    @Test
    void roots_withoutAnOrganization_isABadRequest() {
        assertThatThrownBy(() -> service.listByOrganization(
                caller, null, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void roots_ofAnUnknownOrganization_isNotFound() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.listByOrganization(
                caller, 10L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- children of one place ----------

    private SpatialUnitDTO parent() {
        SpatialUnitDTO parent = new SpatialUnitDTO();
        parent.setId(3L);
        parent.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(3L)).thenReturn(parent);
        return parent;
    }

    @Test
    void children_withTree_listTheTreeChildrenAndFlagThem() {
        SpatialUnitDTO parent = parent();
        when(spatialUnitService.searchChildrenTree(eq(institution), eq(parent), any(FilterDTO.class), any(Pageable.class)))
                .thenReturn(pageOfRow());
        when(spatialUnitService.treeFlags(any(), any(FilterDTO.class)))
                .thenReturn(new SpatialUnitService.TreeFlags(Set.of(), Set.of(5L)));

        PlaceListResponse response = service.listChildren(
                caller, 3L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null));

        assertThat(response.getData().get(0).getMatch()).isTrue();
        assertThat(response.getData().get(0).getHasChildren()).isFalse();
        // The match count is asked for explicitly, never by default.
        assertThat(response.getMeta().matchCount()).isNull();
        verify(spatialUnitService, never()).countMatchesBelow(any(), any());
    }

    @Test
    void children_withTreeAndMatchCount_countTheMatchesBelowTheParent() {
        SpatialUnitDTO parent = parent();
        when(spatialUnitService.searchChildrenTree(eq(institution), eq(parent), any(FilterDTO.class), any(Pageable.class)))
                .thenReturn(pageOfRow());
        when(spatialUnitService.treeFlags(any(), any(FilterDTO.class)))
                .thenReturn(new SpatialUnitService.TreeFlags(Set.of(), null));
        when(spatialUnitService.countMatchesBelow(eq(3L), any(FilterDTO.class))).thenReturn(12L);

        PlaceListResponse response = service.listChildren(
                caller, 3L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null).withMatchCount(true));

        assertThat(response.getMeta().matchCount()).isEqualTo(12L);
    }

    @Test
    void children_withoutTree_areTheDirectChildrenWithoutFlags() {
        SpatialUnitDTO parent = parent();
        when(spatialUnitService.searchSpatialUnitsInSpatialUnit(eq(institution), eq(parent), any(FilterDTO.class), any(Pageable.class)))
                .thenReturn(pageOfRow());

        PlaceListResponse response = service.listChildren(
                caller, 3L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.OFF);

        assertThat(response.getData().get(0).getHasChildren()).isNull();
        verify(spatialUnitService, never()).searchChildrenTree(any(), any(), any(), any());
    }

    @Test
    void children_searchFiltersOnTheName() {
        SpatialUnitDTO parent = parent();
        when(spatialUnitService.searchChildrenTree(eq(institution), eq(parent), any(FilterDTO.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
        when(spatialUnitService.treeFlags(any(), any(FilterDTO.class)))
                .thenReturn(new SpatialUnitService.TreeFlags(Set.of(), null));

        service.listChildren(caller, 3L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null));

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        verify(spatialUnitService).searchChildrenTree(eq(institution), eq(parent), filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().hasUserFilters()).isTrue();
    }

    @Test
    void children_ofAnUnknownPlace_isNotFound() {
        when(spatialUnitService.findById(3L)).thenThrow(new SpatialUnitNotFoundException("missing"));

        assertThatThrownBy(() -> service.listChildren(
                caller, 3L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.tree(null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void children_ofAPlaceWithoutOrganization_isABadRequest() {
        SpatialUnitDTO orphan = new SpatialUnitDTO();
        orphan.setId(3L);
        when(spatialUnitService.findById(3L)).thenReturn(orphan);

        assertThatThrownBy(() -> service.listChildren(
                caller, 3L, WINDOW, "fr", FieldQuery.NONE, null, TreeRequest.OFF))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
