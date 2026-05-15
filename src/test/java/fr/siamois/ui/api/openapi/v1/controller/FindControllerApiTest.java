package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormData;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
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

        MobilierControllerApi controller = new MobilierControllerApi(projectApiService, recordingUnitOpenApiService, findOpenApiService);
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
    void getFindForm_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/mobiliers/5/form"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void getFindForm_success_returnsJson() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ConceptDTO type = new ConceptDTO();
        type.setId(2L);
        FindFormData payload = new FindFormData(type, null, Map.of(), Map.of());
        when(recordingUnitOpenApiService.buildFindForm(eq(5L), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/mobiliers/5/form")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specimenType.id").value(2));

        verify(recordingUnitOpenApiService).buildFindForm(5L, personDto, Set.of(10L), "fr");
    }

    @Test
    void getFindForm_passesAcceptLanguageToService() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ConceptDTO type = new ConceptDTO();
        type.setId(1L);
        when(recordingUnitOpenApiService.buildFindForm(eq(9L), eq(personDto), eq(Set.of(10L)), eq("en")))
                .thenReturn(new FindFormData(type, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/mobiliers/9/form")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildFindForm(9L, personDto, Set.of(10L), "en");
    }

    @Test
    void getFindForm_withoutAcceptLanguage_defaultsToFrench() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        when(recordingUnitOpenApiService.buildFindForm(eq(7L), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(new FindFormData(type, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/mobiliers/7/form"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildFindForm(7L, personDto, Set.of(10L), "fr");
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

        mockMvc.perform(post("/api/v1/mobiliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordingUnitId\":\"1\",\"specimenTypeConceptId\":\"2\"}"))
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

        mockMvc.perform(patch("/api/v1/mobiliers/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("3"));
    }

    @Test
    void getFindForm_whenNotFound_returns404() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(recordingUnitOpenApiService.buildFindForm(anyLong(), any(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Find not found"));

        mockMvc.perform(get("/api/v1/finds/99/form"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Find not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/finds/99/form"));
    }

    @Test
    void getFindForm_whenBadRequest_returns400() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(recordingUnitOpenApiService.buildFindForm(anyLong(), any(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Find has no owning institution"));

        mockMvc.perform(get("/api/v1/mobiliers/12/form"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Find has no owning institution"));
    }

    @Test
    void getFindCreateForm_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/mobiliers/creation-form")
                        .param("recordingUnitId", "12")
                        .param("specimenTypeConceptId", "42"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void getFindCreateForm_success_returnsJson() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ConceptDTO type = new ConceptDTO();
        type.setId(42L);
        FindFormData payload = new FindFormData(type, null, Map.of(), Map.of());
        when(recordingUnitOpenApiService.buildFindCreateForm(
                eq("12"), eq("42"), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/mobiliers/creation-form")
                        .param("recordingUnitId", "12")
                        .param("specimenTypeConceptId", "42")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specimenType.id").value(42));

        verify(recordingUnitOpenApiService).buildFindCreateForm("12", "42", personDto, Set.of(10L), "fr");
    }

    @Test
    void getFindCreateForm_passesAcceptLanguageToService() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        when(recordingUnitOpenApiService.buildFindCreateForm(
                eq("5"), eq("3"), eq(personDto), eq(Set.of(10L)), eq("en")))
                .thenReturn(new FindFormData(type, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/mobiliers/creation-form")
                        .param("recordingUnitId", "5")
                        .param("specimenTypeConceptId", "3")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildFindCreateForm("5", "3", personDto, Set.of(10L), "en");
    }
}
