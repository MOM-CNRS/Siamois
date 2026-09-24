package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.service.PhaseOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhaseControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private PhaseOpenApiService phaseOpenApiService;

    private MockMvc mockMvc;

    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        PhaseControllerApi controller = new PhaseControllerApi(projectApiService, phaseOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        Person person = new Person();
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

        mockMvc.perform(get("/api/v1/phases/5"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(phaseOpenApiService);
    }

    @Test
    void getById_success_returnsResourceData() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        PhaseResource payload = new PhaseResource();
        payload.setResourceType("phases");
        payload.setId("5");
        when(phaseOpenApiService.getPhaseById(5L, personDto, Set.of(10L), "fr")).thenReturn(payload);

        mockMvc.perform(get("/api/v1/phases/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("5"));

        verify(phaseOpenApiService).getPhaseById(5L, personDto, Set.of(10L), "fr");
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(phaseOpenApiService.getPhaseById(anyLong(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase introuvable ou hors périmètre"));

        mockMvc.perform(get("/api/v1/phases/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_create_returns201() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        PhaseResource res = new PhaseResource();
        res.setResourceType("phases");
        res.setId("55");
        when(phaseOpenApiService.createPhase(any(), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/phases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"1\",\"typeId\":\"2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("55"));
    }

    @Test
    void post_create_withoutWritePermission_returns403() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(phaseOpenApiService.createPhase(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de phase non autorisée sur ce projet"));

        mockMvc.perform(post("/api/v1/phases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"1\",\"typeId\":\"2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_returns200() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        PhaseResource res = new PhaseResource();
        res.setResourceType("phases");
        res.setId("3");
        when(phaseOpenApiService.patchPhase(eq(3L), any(), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(res);

        mockMvc.perform(patch("/api/v1/phases/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("3"));
    }
}
