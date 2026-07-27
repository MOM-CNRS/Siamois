package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FindControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private FindOpenApiService findOpenApiService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        FindControllerApi controller = new FindControllerApi(projectApiService, recordingUnitOpenApiService, findOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        person = new Person();
        person.setId(1L);
        personDto = new PersonDTO();
        personDto.setId(1L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                person, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getById_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/finds/5"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void getById_success_returnsResourceData() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        FindResource payload = new FindResource();
        payload.setResourceType("finds");
        payload.setId("5");
        when(recordingUnitOpenApiService.buildFindMobilierForm("5", personDto, Set.of(10L), "fr"))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/finds/5")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("5"))
                .andExpect(jsonPath("$.data.specimenType").doesNotExist());

        verify(recordingUnitOpenApiService).buildFindMobilierForm("5", personDto, Set.of(10L), "fr");
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(recordingUnitOpenApiService.buildFindMobilierForm(anyString(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        mockMvc.perform(get("/api/v1/finds/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_create_returns201() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        FindResource res = new FindResource();
        res.setResourceType("finds");
        res.setId("55");
        when(findOpenApiService.createFind(any(), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/finds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordingUnitId\":\"1\",\"typeId\":\"2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("55"));
    }

    @Test
    void patch_returns200() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        FindResource res = new FindResource();
        res.setResourceType("finds");
        res.setId("3");
        when(findOpenApiService.patchFind(eq(3L), any(), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(res);

        mockMvc.perform(patch("/api/v1/finds/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("3"));
    }

    @Test
    void delete_returns204() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        mockMvc.perform(delete("/api/v1/finds/7"))
                .andExpect(status().isNoContent());

        verify(findOpenApiService).deleteFind(7L, personDto, Set.of(10L), "fr");
    }

    @Test
    void delete_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(delete("/api/v1/finds/7"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(findOpenApiService);
    }

    @Test
    void getFindForm_returns200() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setId("42");
        type.setResolvedLabel("Céramique");
        FindCreateFormData data = new FindCreateFormData(type, null, Map.of());
        when(recordingUnitOpenApiService.buildFindCreateForm(10L, 42L, personDto, "fr")).thenReturn(data);

        mockMvc.perform(get("/api/v1/finds/form")
                        .param("organizationId", "10")
                        .param("typeConceptId", "42")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findType.id").value("42"))
                .andExpect(jsonPath("$.data.findType.resolvedLabel").value("Céramique"));

        verify(projectApiService).assertOrganizationInCallerScope(10L, Set.of(10L));
        verify(recordingUnitOpenApiService).buildFindCreateForm(10L, 42L, personDto, "fr");
    }

    @Test
    void getFindForm_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/finds/form")
                        .param("organizationId", "10")
                        .param("typeConceptId", "42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFindForm_outOfScope_returns403() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "hors périmètre"))
                .when(projectApiService).assertOrganizationInCallerScope(99L, Set.of(10L));

        mockMvc.perform(get("/api/v1/finds/form")
                        .param("organizationId", "99")
                        .param("typeConceptId", "42"))
                .andExpect(status().isForbidden());
    }
}
