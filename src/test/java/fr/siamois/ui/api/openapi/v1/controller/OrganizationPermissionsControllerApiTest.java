package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.organization.OrganizationPermissionsControllerApi;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationPermissionsControllerApiTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private ProfilePermissionService profilePermissionService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrganizationPermissionsControllerApi(projectApiService, profilePermissionService))
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
        institution = new InstitutionDTO();
        institution.setId(10L);
    }

    @Test
    void permissions_whoMayCreateProjects_getTrue() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(projectApiService.requireOrganization(10L, caller)).thenReturn(institution);
        when(profilePermissionService.institutionIdsWithActionUnitCreatePermission(caller.person(), List.of(institution)))
                .thenReturn(Set.of(10L));

        mockMvc.perform(get("/api/v1/organizations/10/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canCreateProjects").value(true));
    }

    @Test
    void permissions_otherwise_getFalse() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(projectApiService.requireOrganization(10L, caller)).thenReturn(institution);
        when(profilePermissionService.institutionIdsWithActionUnitCreatePermission(caller.person(), List.of(institution)))
                .thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/organizations/10/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canCreateProjects").value(false));
    }

    @Test
    void permissions_outsideScope_returns403() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(projectApiService.requireOrganization(99L, caller)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/organizations/99/permissions"))
                .andExpect(status().isForbidden());
    }
}
