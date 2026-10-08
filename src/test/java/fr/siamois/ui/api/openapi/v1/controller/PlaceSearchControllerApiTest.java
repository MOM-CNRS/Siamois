package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.place.PlaceSearchControllerApi;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlaceSearchControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private LabelService labelService;
    @Mock
    private fr.siamois.ui.api.openapi.v1.service.PlaceSuggestionApiService placeSuggestionApiService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        PlaceSearchControllerApi controller = new PlaceSearchControllerApi(projectApiService, spatialUnitService, labelService, placeSuggestionApiService);
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

    // What a picker shows as soon as it opens, before anything is typed.
    @Test
    void autocomplete_emptyQuery_returnsFirstPageByName() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(
                new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of()));
        SpatialUnitDTO su = new SpatialUnitDTO();
        su.setId(5L);
        su.setName("Abbaye");
        PageRequest firstPage = PageRequest.of(0, 20, Sort.by("name"));
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(""), isNull(), isNull(), isNull(), eq("fr"), eq(firstPage)))
                .thenReturn(new PageImpl<>(List.of(su), firstPage, 1));

        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10")
                        .param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Abbaye"));
        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Abbaye"));
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
                eq(PageRequest.of(0, 20, Sort.by("name")))))
                .thenReturn(new PageImpl<>(List.of(su), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/places/autocomplete")
                        .param("organizationId", "10")
                        .param("q", "  lilas  "))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].name").value("Rue des Lilas"))
                .andExpect(jsonPath("$.data[0].code").value("LILAS"));

        verify(projectApiService).assertOrganizationInCallerScope(10L, Set.of(10L));
    }

    @Test
    void suggestions_passesTheFieldAndTheDependenciesOfItsSources() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of()));
        when(placeSuggestionApiService.suggest(any(), eq(10L), eq(-104L), eq("3"), isNull(), eq("rue"),
                eq(java.util.Map.of(-108L, 7L)), eq(20), eq("fr")))
                .thenReturn(new fr.siamois.ui.api.openapi.v1.service.PlaceSuggestionApiService.Suggestions(
                        List.of(new fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi(
                                null, "1 rue X", null, "GEOPLAT", null, null)),
                        List.of("INSEE")));

        mockMvc.perform(get("/api/v1/places/suggestions")
                        .param("organizationId", "10")
                        .param("fieldId", "-104")
                        .param("projectId", "3")
                        .param("q", "rue")
                        .param("dep.-108", "7")
                        .param("dep.abc", "x")
                        .param("dep.-5", "oops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("1 rue X"))
                .andExpect(jsonPath("$.data[0].source").value("GEOPLAT"))
                .andExpect(jsonPath("$.unnarrowedSources[0]").value("INSEE"));
    }
}
