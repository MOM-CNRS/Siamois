package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindMobilierFormData;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void getById_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/mobiliers/5"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void getById_success_returnsFormAndFieldsOnly() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        FindMobilierFormData payload = new FindMobilierFormData(null, Map.of());
        when(recordingUnitOpenApiService.buildFindMobilierForm(eq("5"), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/mobiliers/5")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields").isEmpty())
                .andExpect(jsonPath("$.data.specimenType").doesNotExist())
                .andExpect(jsonPath("$.data.vocabulariesByFieldCode").doesNotExist());

        verify(recordingUnitOpenApiService).buildFindMobilierForm("5", personDto, Set.of(10L), "fr");
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(recordingUnitOpenApiService.buildFindMobilierForm(anyString(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        mockMvc.perform(get("/api/v1/mobiliers/99"))
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

        mockMvc.perform(post("/api/v1/mobiliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordingUnitId\":\"1\",\"specimenTypeConceptId\":\"2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resourceId").value("55"));
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
                .andExpect(jsonPath("$.data.resourceId").value("3"));
    }

    @Test
    void delete_returns204() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        mockMvc.perform(delete("/api/v1/mobiliers/7"))
                .andExpect(status().isNoContent());

        verify(findOpenApiService).deleteFind(eq(7L), eq(personDto), eq(Set.of(10L)), eq("fr"));
    }

    @Test
    void delete_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(delete("/api/v1/mobiliers/7"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(findOpenApiService);
    }
}
