package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessOrderRequest;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessResource;
import fr.siamois.ui.api.openapi.v1.service.HomeAccessOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HomeAccessControllerApiTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private HomeAccessOpenApiService homeAccessService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeAccessControllerApi(projectApiService, homeAccessService))
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
        institution = new InstitutionDTO();
        institution.setId(10L);
    }

    private void inScope() {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(projectApiService.requireOrganization(10L, caller)).thenReturn(institution);
    }

    @Test
    void list_returnsTheCardsAndWhetherTheCallerEdits() throws Exception {
        inScope();
        when(homeAccessService.list(any(UserInfo.class)))
                .thenReturn(new HomeAccessListResponse(List.of(new HomeAccessResource(1L, 11L, "recordingUnit", "US", "US", null)), true));

        mockMvc.perform(get("/api/v1/home-accesses").param("organizationId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.data[0].viewId").value(11))
                .andExpect(jsonPath("$.data[0].title").value("US"));
    }

    @Test
    void create_returns201WithTheCard() throws Exception {
        inScope();
        when(homeAccessService.create(any(UserInfo.class), any(HomeAccessRequest.class)))
                .thenReturn(new HomeAccessResource(5L, 11L, "recordingUnit", "US", "US", null));

        mockMvc.perform(post("/api/v1/home-accesses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":10,\"resourceType\":\"recordingUnit\",\"title\":\"US\",\"state\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void create_withoutOrganization_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/home-accesses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"recordingUnit\",\"title\":\"US\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withoutThePermission_returns403() throws Exception {
        inScope();
        when(homeAccessService.create(any(UserInfo.class), any(HomeAccessRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/v1/home-accesses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":10,\"resourceType\":\"recordingUnit\",\"title\":\"US\",\"state\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returns204() throws Exception {
        inScope();

        mockMvc.perform(delete("/api/v1/home-accesses/5").param("organizationId", "10"))
                .andExpect(status().isNoContent());

        verify(homeAccessService).delete(any(UserInfo.class), eq(5L));
    }

    @Test
    void reorder_returns204_andPassesTheIds() throws Exception {
        inScope();

        mockMvc.perform(put("/api/v1/home-accesses/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":10,\"ids\":[3,1,2]}"))
                .andExpect(status().isNoContent());

        verify(homeAccessService).reorder(any(UserInfo.class), eq(new HomeAccessOrderRequest(10L, List.of(3L, 1L, 2L))));
    }

    @Test
    void reorder_withoutThePermission_returns403() throws Exception {
        inScope();
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(homeAccessService).reorder(any(UserInfo.class), any(HomeAccessOrderRequest.class));

        mockMvc.perform(put("/api/v1/home-accesses/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":10,\"ids\":[1]}"))
                .andExpect(status().isForbidden());
    }
}
