package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitTypeOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecordingUnitTypeSearchControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitTypeOpenApiService recordingUnitTypeOpenApiService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        RecordingUnitTypeSearchControllerApi controller = new RecordingUnitTypeSearchControllerApi(
                projectApiService, recordingUnitTypeOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();
    }

    @Test
    void listTypes_withoutAuthentication_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/recording-unit-types").param("organizationId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTypes_forbiddenOrganization_returns403() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(new PersonDTO(), Set.of(99L), List.of()));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Organisation non accessible"))
                .when(projectApiService).assertOrganizationInCallerScope(eq(10L), any());

        mockMvc.perform(get("/api/v1/recording-unit-types").param("organizationId", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTypes_success_returnsFieldCodeAndTypes() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of()));
        ConceptAutocompleteDTO one = new ConceptAutocompleteDTO(null, "US", "fr");
        when(recordingUnitTypeOpenApiService.listRecordingUnitTypes(eq(10L), any(), eq("fr"), eq("us")))
                .thenReturn(List.of(one));

        mockMvc.perform(get("/api/v1/recording-unit-types")
                        .param("organizationId", "10")
                        .param("q", "us"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fieldCode").value("SIARU.TYPE"))
                .andExpect(jsonPath("$.data.types[0].originalPrefLabel").value("US"));

        verify(recordingUnitTypeOpenApiService).listRecordingUnitTypes(eq(10L), any(), eq("fr"), eq("us"));
    }
}
