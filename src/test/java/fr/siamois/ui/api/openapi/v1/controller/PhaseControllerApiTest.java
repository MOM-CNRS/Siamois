package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.service.ListQueryStubs;
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
    @Mock
    private fr.siamois.ui.api.openapi.v1.service.RecordingUnitListAssembler recordingUnitListAssembler;

    private MockMvc mockMvc;

    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        PhaseControllerApi controller = new PhaseControllerApi(projectApiService, phaseOpenApiService, recordingUnitListAssembler, ListQueryStubs.none());
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

    @Test
    void getRecordingUnits_checksPhaseAccess_thenPagesItsRecordingUnitsForItsProject() throws Exception {
        fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller caller =
                new fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller(personDto, java.util.Set.of(10L), java.util.List.of());
        org.mockito.Mockito.when(projectApiService.requireCaller()).thenReturn(caller);
        fr.siamois.dto.entity.PhaseDTO phase = new fr.siamois.dto.entity.PhaseDTO();
        phase.setId(3L);
        fr.siamois.dto.entity.ActionUnitSummaryDTO project = new fr.siamois.dto.entity.ActionUnitSummaryDTO();
        project.setId(6L);
        phase.setActionUnit(project);
        org.mockito.Mockito.when(phaseOpenApiService.requireAccessible(3L, personDto, java.util.Set.of(10L))).thenReturn(phase);
        org.springframework.data.domain.Page<fr.siamois.dto.entity.RecordingUnitDTO> page = org.springframework.data.domain.Page.empty();
        org.mockito.Mockito.when(projectApiService.pageRecordingUnitsForPhase(
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq("creationTime:desc"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(fr.siamois.dto.FieldQuery.class))).thenReturn(page);
        org.mockito.Mockito.when(recordingUnitListAssembler.assemble(caller, page, 6L, null, "fr", 10, 0))
                .thenReturn(new fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse(
                        java.util.List.of(), new fr.siamois.ui.api.openapi.v1.generic.response.ListMeta(0L, 10, 0L)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/phases/3/recording-units")
                        .param("limit", "10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.meta.total").value(0));

        org.mockito.Mockito.verify(recordingUnitListAssembler).assemble(caller, page, 6L, null, "fr", 10, 0);
    }
}
