package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.service.ListQueryStubs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.place.PlaceControllerApi;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private PlaceOpenApiService placeOpenApiService;
    @Mock
    private fr.siamois.ui.api.openapi.v1.service.ProjectListAssembler projectListAssembler;

    private MockMvc mockMvc;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        PlaceControllerApi controller = new PlaceControllerApi(projectApiService, placeOpenApiService, projectListAssembler, ListQueryStubs.none());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        personDto = new PersonDTO();
        personDto.setId(1L);
    }

    @Test
    void create_returns201() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(placeOpenApiService.createPlace(any(), any(), eq("fr")))
                .thenReturn(new PlaceCreatedResponse.PlaceCreatedItem(5L, "Cave A", "L-5"));

        mockMvc.perform(post("/api/v1/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "fr")
                        .content("""
                                {
                                  "organizationId": 10,
                                  "name": "Cave A",
                                  "typeConceptId": 42
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.name").value("Cave A"))
                .andExpect(jsonPath("$.data.code").value("L-5"));

        verify(placeOpenApiService).createPlace(any(), any(), eq("fr"));
    }

    @Test
    void create_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(post("/api/v1/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationId":10,"name":"Cave A","typeConceptId":42}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_returns200() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(placeOpenApiService.updatePlace(any(), eq(5L), any(), eq("fr")))
                .thenReturn(new PlaceCreatedResponse.PlaceCreatedItem(5L, "Cave B", "L-5"));

        mockMvc.perform(patch("/api/v1/places/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "fr")
                        .content("""
                                {"name":"Cave B"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.name").value("Cave B"));

        verify(placeOpenApiService).updatePlace(any(), eq(5L), any(), eq("fr"));
    }

    @Test
    void patch_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(patch("/api/v1/places/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cave B\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_returns204() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        mockMvc.perform(delete("/api/v1/places/5")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isNoContent());

        verify(placeOpenApiService).deletePlace(any(), eq(5L), eq("fr"));
    }

    @Test
    void delete_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(delete("/api/v1/places/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returns200() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        PlaceResource resource = new PlaceResource();
        resource.setResourceType("places");
        resource.setId("5");
        resource.setName("Cave A");
        when(placeOpenApiService.getPlaceById(any(), eq(5L), eq("fr"))).thenReturn(resource);

        mockMvc.perform(get("/api/v1/places/5")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("5"))
                .andExpect(jsonPath("$.data.name").value("Cave A"));

        verify(placeOpenApiService).getPlaceById(any(), eq(5L), eq("fr"));
    }

    @Test
    void getById_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/places/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(placeOpenApiService.getPlaceById(any(), eq(99L), eq("fr")))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Lieu introuvable"));

        mockMvc.perform(get("/api/v1/places/99")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFinds_notImplemented_returns501() throws Exception {
        mockMvc.perform(get("/api/v1/places/5/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getList_notImplemented_returns501() throws Exception {
        mockMvc.perform(get("/api/v1/places/5/recording-units")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getChildren_delegatesToTheService() throws Exception {
        org.mockito.Mockito.when(placeOpenApiService.listChildren(any(), eq(5L), eq(0), eq(10), eq("name:asc"), eq("cave"), eq("fr")))
                .thenReturn(new fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse(
                        List.of(), new fr.siamois.ui.api.openapi.v1.generic.response.ListMeta(0L, 10, 0L)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/places/5/children")
                        .param("search", "cave"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Total-Count", "0"));
    }

    @Test
    void getProjects_forcesTheSpatialContextFilter_onThePlacesOrganization() throws Exception {
        fr.siamois.dto.entity.SpatialUnitDTO place = new fr.siamois.dto.entity.SpatialUnitDTO();
        place.setId(5L);
        fr.siamois.dto.entity.InstitutionDTO institution = new fr.siamois.dto.entity.InstitutionDTO();
        institution.setId(10L);
        place.setCreatedByInstitution(institution);
        org.mockito.Mockito.when(placeOpenApiService.requireAccessible(any(), eq(5L))).thenReturn(place);
        org.springframework.data.domain.Page<fr.siamois.dto.api.AccessibleProjectForApi> rows = org.springframework.data.domain.Page.empty();
        org.mockito.Mockito.when(projectApiService.pageAccessibleProjects(any(), eq(10L), isNull(), eq(0), eq(20), eq("name:asc"),
                org.mockito.ArgumentMatchers.argThat(filter ->
                        List.of(5L).equals(filter.conceptManyInFilters().get("spatialContext"))), org.mockito.ArgumentMatchers.any(fr.siamois.dto.FieldQuery.class)))
                .thenReturn(rows);
        org.mockito.Mockito.when(projectListAssembler.assemble(any(), eq(rows), isNull(), eq("fr"), eq(20), eq(0)))
                .thenReturn(new fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse(
                        List.of(), new fr.siamois.ui.api.openapi.v1.generic.response.ListMeta(0L, 20, 0L)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/places/5/projects"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.meta.total").value(0));
    }
}
