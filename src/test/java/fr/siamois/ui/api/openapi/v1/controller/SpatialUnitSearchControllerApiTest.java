package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SpatialUnitSearchControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private SpatialUnitService spatialUnitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        SpatialUnitSearchControllerApi controller = new SpatialUnitSearchControllerApi(projectApiService, spatialUnitService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();
    }

    @Test
    void autocomplete_withoutAuthentication_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10")
                        .param("q", "rue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void autocomplete_emptyQuery_returns400() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of()));

        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10")
                        .param("q", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autocomplete_forbiddenOrganization_returns403() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(new PersonDTO(), Set.of(99L), List.of()));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Organisation non accessible"))
                .when(projectApiService).assertOrganizationInCallerScope(eq(10L), any());

        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10")
                        .param("q", "a"))
                .andExpect(status().isForbidden());
    }

    @Test
    void autocomplete_success_returnsMatches() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of()));

        SpatialUnitDTO su = new SpatialUnitDTO();
        su.setId(77L);
        su.setName("Rue des Lilas");
        su.setCode("LILAS");
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                eq(10L),
                eq("lilas"),
                isNull(),
                isNull(),
                isNull(),
                eq("fr"),
                eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(su), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10")
                        .param("q", "  lilas  "))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].name").value("Rue des Lilas"))
                .andExpect(jsonPath("$.data[0].code").value("LILAS"));

        verify(projectApiService).assertOrganizationInCallerScope(eq(10L), eq(Set.of(10L)));
    }
}
