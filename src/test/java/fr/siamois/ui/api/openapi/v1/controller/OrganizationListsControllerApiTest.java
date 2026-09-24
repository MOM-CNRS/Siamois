package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.service.ListQueryStubs;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.ui.api.openapi.v1.service.ContainerListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService;
import fr.siamois.ui.api.openapi.v1.service.PhaseListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitListProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationListsControllerApiTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private OrganizationListService organizationListService;
    @Mock private PlaceOpenApiService placeOpenApiService;
    @Mock private RecordingUnitResponseMapper recordingUnitResponseMapper;
    @Mock private RecordingUnitListProjectionService recordingUnitListProjectionService;
    @Mock private FindOpenApiMapper findOpenApiMapper;
    @Mock private PhaseOpenApiMapper phaseOpenApiMapper;
    @Mock private PhaseListProjectionService phaseListProjectionService;
    @Mock private ContainerOpenApiMapper containerOpenApiMapper;
    @Mock private ContainerListProjectionService containerListProjectionService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;
    private InstitutionDTO institution;
    private ActionUnitSummaryDTO editableProject;
    private ActionUnitSummaryDTO readOnlyProject;

    @BeforeEach
    void setUp() {
        OrganizationListsControllerApi controller = new OrganizationListsControllerApi(
                projectApiService, organizationListService, placeOpenApiService,
                recordingUnitResponseMapper, recordingUnitListProjectionService, findOpenApiMapper,
                phaseOpenApiMapper, phaseListProjectionService, containerOpenApiMapper, containerListProjectionService, org.mockito.Mockito.mock(ResourceBookmarkService.class), ListQueryStubs.none());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule())))
                .build();

        PersonDTO person = new PersonDTO();
        person.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of(institution));
        editableProject = project(7L, "OA-7");
        readOnlyProject = project(8L, "OA-8");
    }

    private void authenticated() {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(organizationListService.requireListOrganization(caller, 10L)).thenReturn(institution);
    }

    private void permissions(OrganizationListService.EditPermissions kind) {
        when(organizationListService.canEditByProject(eq(caller), eq(institution), any(), any(), eq(kind)))
                .thenReturn(Map.of(7L, true, 8L, false));
    }

    @Test
    void listRecordingUnits_setsProjectAndPerRowPermissions() throws Exception {
        authenticated();
        RecordingUnitDTO editable = new RecordingUnitDTO();
        editable.setId(1L);
        editable.setActionUnit(editableProject);
        RecordingUnitDTO readOnly = new RecordingUnitDTO();
        readOnly.setId(2L);
        readOnly.setActionUnit(readOnlyProject);
        when(organizationListService.pageRecordingUnits(eq(caller), eq(institution), eq(0), eq(10), eq("creationTime:desc"), eq("US"), any(), org.mockito.ArgumentMatchers.any(fr.siamois.dto.FieldQuery.class)))
                .thenReturn(new PageImpl<>(List.of(editable, readOnly), PageRequest.of(0, 10), 2));
        permissions(OrganizationListService.EditPermissions.RECORDING_UNITS);
        when(recordingUnitListProjectionService.build(any(), isNull(), any()))
                .thenReturn(RecordingUnitListProjectionService.RecordingUnitListProjection.empty());
        when(recordingUnitResponseMapper.convert(any(RecordingUnitDTO.class))).thenAnswer(inv -> {
            RecordingUnitResource r = new RecordingUnitResource();
            r.setId(String.valueOf(((RecordingUnitDTO) inv.getArgument(0)).getId()));
            return r;
        });

        mockMvc.perform(get("/api/v1/recording-units").param("organizationId", "10").param("search", "US"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.data[0].project.resourceId").value("7"))
                .andExpect(jsonPath("$.data[0].project.label").value("OA-7"))
                .andExpect(jsonPath("$.data[0]._permissions.canEdit").value(true))
                .andExpect(jsonPath("$.data[1]._permissions.canEdit").value(false))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void listFinds_setsResourceUriAndPermissions() throws Exception {
        authenticated();
        SpecimenDTO find = new SpecimenDTO();
        find.setId(3L);
        find.setActionUnit(editableProject);
        when(organizationListService.pageFinds(caller, institution, 0, 10, "fullIdentifier:asc", null, fr.siamois.dto.FieldQuery.NONE))
                .thenReturn(new PageImpl<>(List.of(find), PageRequest.of(0, 10), 1));
        permissions(OrganizationListService.EditPermissions.FINDS);
        when(findOpenApiMapper.toResource(find)).thenReturn(new FindResource());

        mockMvc.perform(get("/api/v1/finds").param("organizationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resourceUri").value("/specimen/3"))
                .andExpect(jsonPath("$.data[0].project.resourceId").value("7"))
                .andExpect(jsonPath("$.data[0]._permissions.canEdit").value(true));
    }

    @Test
    void listPhases_rowWithoutProject_isReadOnly() throws Exception {
        authenticated();
        PhaseDTO phase = new PhaseDTO();
        phase.setId(4L);
        when(organizationListService.pagePhases(caller, institution, 0, 10, "orderNumber:asc", null, fr.siamois.dto.FieldQuery.NONE))
                .thenReturn(new PageImpl<>(List.of(phase), PageRequest.of(0, 10), 1));
        permissions(OrganizationListService.EditPermissions.PHASES);
        when(phaseListProjectionService.build(any(), isNull(), any()))
                .thenReturn(PhaseListProjectionService.PhaseListProjection.empty());
        when(phaseOpenApiMapper.toResource(eq(phase), any(), any())).thenReturn(new PhaseResource());

        mockMvc.perform(get("/api/v1/phases").param("organizationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]._permissions.canEdit").value(false))
                .andExpect(jsonPath("$.data[0].project").doesNotExist());
    }

    @Test
    void listContainers_returnsPage() throws Exception {
        authenticated();
        ContainerDTO box = new ContainerDTO();
        box.setId(5L);
        box.setActionUnit(readOnlyProject);
        when(organizationListService.pageContainers(caller, institution, 0, 10, "identifier:asc", null, fr.siamois.dto.FieldQuery.NONE))
                .thenReturn(new PageImpl<>(List.of(box), PageRequest.of(0, 10), 1));
        permissions(OrganizationListService.EditPermissions.CONTAINERS);
        when(containerListProjectionService.build(any(), isNull(), any()))
                .thenReturn(ContainerListProjectionService.ContainerListProjection.empty());
        when(containerOpenApiMapper.toResource(eq(box), any(), any())).thenReturn(new ContainerResource());

        mockMvc.perform(get("/api/v1/containers").param("organizationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].project.label").value("OA-8"))
                .andExpect(jsonPath("$.data[0]._permissions.canEdit").value(false));
    }

    @Test
    void listPlaces_delegatesToPlaceService() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        PlaceResource place = new PlaceResource();
        place.setName("Cave A");
        when(placeOpenApiService.listByOrganization(eq(caller), eq(10L), eq(0), eq(10), eq("name:asc"), eq("cave"), any()))
                .thenReturn(new PlaceListResponse(List.of(place), new ListMeta(1L, 10, 0L)));

        mockMvc.perform(get("/api/v1/places").param("organizationId", "10").param("search", "cave"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].name").value("Cave A"));
    }

    @Test
    void list_withoutOrganizationId_returns400() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(organizationListService.requireListOrganization(caller, null))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v1/phases")).andExpect(status().isBadRequest());
        verifyNoInteractions(phaseOpenApiMapper);
    }

    @Test
    void list_outOfScope_returns403() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(organizationListService.requireListOrganization(caller, 99L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/finds").param("organizationId", "99")).andExpect(status().isForbidden());
    }

    @Test
    void list_withoutAuthentication_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/containers").param("organizationId", "10")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_invalidPagination_returns400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST))
                .when(projectApiService).validatePagedListRequest(-1, 10);

        mockMvc.perform(get("/api/v1/recording-units").param("organizationId", "10").param("offset", "-1"))
                .andExpect(status().isBadRequest());
        verify(projectApiService).validatePagedListRequest(-1, 10);
    }

    private static ActionUnitSummaryDTO project(Long id, String fullIdentifier) {
        ActionUnitSummaryDTO dto = new ActionUnitSummaryDTO();
        dto.setId(id);
        dto.setFullIdentifier(fullIdentifier);
        return dto;
    }
}
