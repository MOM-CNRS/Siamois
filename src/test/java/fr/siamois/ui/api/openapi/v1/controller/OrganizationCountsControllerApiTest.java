package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.organization.OrganizationCountsControllerApi;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationCountsResource;
import fr.siamois.ui.api.openapi.v1.service.OrganizationCountsService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationCountsControllerApiTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private OrganizationCountsService organizationCountsService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        OrganizationCountsControllerApi controller = new OrganizationCountsControllerApi(projectApiService, organizationCountsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
    }

    @Test
    void getCounts_returns200WithEveryCount() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(organizationCountsService.countsForOrganization(caller, 10L))
                .thenReturn(new OrganizationCountsResource(1, 2, 3, 4, 5, 6));

        mockMvc.perform(get("/api/v1/organizations/10/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects").value(1))
                .andExpect(jsonPath("$.data.places").value(2))
                .andExpect(jsonPath("$.data.recordingUnits").value(3))
                .andExpect(jsonPath("$.data.finds").value(4))
                .andExpect(jsonPath("$.data.phases").value(5))
                .andExpect(jsonPath("$.data.containers").value(6));
    }

    @Test
    void getCounts_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/organizations/10/counts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCounts_outsideScope_returns403() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(organizationCountsService.countsForOrganization(any(), eq(99L)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/organizations/99/counts"))
                .andExpect(status().isForbidden());
    }
}
