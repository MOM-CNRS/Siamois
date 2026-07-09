package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.place.PlaceControllerApi;
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

    private MockMvc mockMvc;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        PlaceControllerApi controller = new PlaceControllerApi(projectApiService, placeOpenApiService);
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
    void getById_notImplemented_returns501() throws Exception {
        mockMvc.perform(get("/api/v1/places/5"))
                .andExpect(status().isNotImplemented());
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
}
