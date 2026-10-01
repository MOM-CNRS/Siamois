package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.dto.FieldQuery;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.response.container.ContainerListResponse;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.phase.PhaseListResponse;
import fr.siamois.ui.api.openapi.v1.service.ContainerListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.FindListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.PhaseListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The three project list endpoints (containers, phases, finds) share one shape: page, permissions, projection, bookmarks. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectListControllersTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private FieldQueryService fieldQueryService;
    @Mock private ResourceBookmarkService bookmarks;
    @Mock private ContainerOpenApiMapper containerMapper;
    @Mock private PhaseOpenApiMapper phaseMapper;
    @Mock private FindOpenApiMapper findMapper;
    @Mock private ContainerListProjectionService containerProjection;
    @Mock private PhaseListProjectionService phaseProjection;
    @Mock private FindListProjectionService findProjection;

    private final InstitutionDTO institution = new InstitutionDTO();
    private ProjectApiCaller caller;
    private final LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();

    @BeforeEach
    void setUp() {
        institution.setId(4L);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(4L), List.of(institution));
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(fieldQueryService.parse(any(), any(), anyString(), any())).thenReturn(FieldQuery.NONE);
        when(projectApiService.canEditContainersForProject(any(), anyString(), any())).thenReturn(true);
        when(projectApiService.canEditPhasesForProject(any(), anyString(), any())).thenReturn(false);
        when(projectApiService.canEditFindsForProject(any(), anyString(), any())).thenReturn(true);
        when(projectApiService.canValidateForProject(any(), anyString(), any())).thenReturn(true);
    }

    private ActionUnitSummaryDTO project() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setId(1L);
        au.setCreatedByInstitution(institution);
        return au;
    }

    @Test
    void containers_listWithProjection() {
        ContainerDTO dto = new ContainerDTO();
        dto.setId(9L);
        dto.setActionUnit(project());
        when(projectApiService.pageContainersForProject(any(), eq("1"), eq(0), eq(10), eq("identifier:asc"), eq("x"), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));
        when(containerProjection.build(any(), eq("all"), eq("fr")))
                .thenReturn(new ContainerListProjectionService.ContainerListProjection(Map.of(), Map.of(9L, Map.of("f", "v"))));
        when(containerMapper.toResource(eq(dto), eq("fr"), any())).thenReturn(new ContainerResource());

        ResponseEntity<ContainerListResponse> response = new ProjectContainersControllerApi(projectApiService, containerMapper,
                containerProjection, bookmarks, fieldQueryService)
                .listContainers("1", 0, 10, "x", "identifier:asc", query, "all", "fr-FR,fr");

        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        ContainerResource resource = response.getBody().getData().get(0);
        assertThat(resource.getAnswers()).containsEntry("f", "v");
        assertThat(resource.getPermissions()).isNotNull();
        verify(bookmarks).markBookmarked(any(PersonDTO.class), eq(institution), any(List.class), eq("fr"));
    }

    @Test
    void containers_emptyPageSkipsTheBookmarkQuery() {
        when(projectApiService.pageContainersForProject(any(), anyString(), anyInt(), anyInt(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(containerProjection.build(any(), any(), any())).thenReturn(ContainerListProjectionService.ContainerListProjection.empty());

        ResponseEntity<ContainerListResponse> response = new ProjectContainersControllerApi(projectApiService, containerMapper,
                containerProjection, bookmarks, fieldQueryService)
                .listContainers("1", 0, 10, null, "identifier:asc", query, null, null);

        assertThat(response.getBody().getData()).isEmpty();
        verify(bookmarks, never()).markBookmarked(any(PersonDTO.class), any(), any(List.class), any());
    }

    @Test
    void phases_listWithoutProjectionHasNoAnswers() {
        PhaseDTO dto = new PhaseDTO();
        dto.setId(3L);
        dto.setActionUnit(project());
        when(projectApiService.pagePhasesForProject(any(), eq("1"), anyInt(), anyInt(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));
        when(phaseProjection.build(any(), any(), any())).thenReturn(PhaseListProjectionService.PhaseListProjection.empty());
        when(phaseMapper.toResource(eq(dto), any(), any())).thenReturn(new PhaseResource());

        ResponseEntity<PhaseListResponse> response = new ProjectPhasesControllerApi(projectApiService, phaseMapper,
                phaseProjection, bookmarks, fieldQueryService)
                .listPhases("1", 0, 10, null, "orderNumber:asc", query, null, "en");

        PhaseResource resource = response.getBody().getData().get(0);
        assertThat(resource.getAnswers()).isNull();
        assertThat(resource.getPermissions()).isNotNull();
        verify(bookmarks).markBookmarked(any(PersonDTO.class), eq(institution), any(List.class), eq("en"));
    }

    @Test
    void finds_listWithProjection() {
        SpecimenDTO dto = new SpecimenDTO();
        dto.setId(5L);
        dto.setCreatedByInstitution(institution);
        when(projectApiService.pageFindsForProject(any(), eq("1"), anyInt(), anyInt(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));
        when(findProjection.build(any(), eq("all"), any()))
                .thenReturn(new FindListProjectionService.FindListProjection(Map.of(5L, Map.of("k", 1))));
        when(findMapper.toResource(dto)).thenReturn(new FindResource());

        ResponseEntity<FindListResponse> response = new ProjectFindsApi(projectApiService, findMapper, bookmarks,
                fieldQueryService, findProjection)
                .getFinds("1", 0, 10, null, "fullIdentifier:asc", query, "all", "fr");

        FindResource resource = response.getBody().getData().get(0);
        assertThat(resource.getAnswers()).containsEntry("k", 1);
        assertThat(resource.getResourceUri()).isEqualTo("/specimen/5");
    }
}
