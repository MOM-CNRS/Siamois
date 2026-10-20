package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The user's recent pages: caller-scope plumbing comes from ProjectApiService, as in the bookmark tests. */
@ExtendWith(MockitoExtension.class)
class HistoryControllerApiTest {

    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private DocumentService documentService;
    @Mock
    private SpecimenService specimenService;
    @Mock
    private ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    @Mock
    private FindOpenApiMapper findOpenApiMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private PhaseService phaseService;
    @Mock
    private ContainerService containerService;
    @Mock
    private BookmarkService bookmarkService;
    @Mock
    private fr.siamois.domain.services.UserHistoryService historyService;
    @Mock
    private HistoryAuditService historyAuditService;

    private MockMvc mockMvc;
    private Person person;
    private PersonDTO personDto;
    private InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {
        ProjectApiService projectApiService = new ProjectApiService(
                institutionService,
                actionUnitService,
                recordingUnitService,
                spatialUnitService,
                documentService,
                specimenService,
                projectDocumentOpenApiMapper,
                findOpenApiMapper,
                personMapper,
                profilePermissionService,
                conceptService,
                conceptMapper,
                recordingUnitOpenApiService,
                phaseService,
                containerService, mock(fr.siamois.domain.services.study.StudyService.class),
                bookmarkService,
                historyAuditService, mock(fr.siamois.ui.api.openapi.v1.service.ValidationOpenApiService.class));

        HistoryControllerApi controller = new HistoryControllerApi(projectApiService, historyService);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        person = new Person();
        person.setId(7L);
        person.setPassword("secret");

        personDto = new PersonDTO();
        personDto.setId(7L);

        institutionDto = new InstitutionDTO();
        institutionDto.setId(100L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(person, person.getPassword(), AuthorityUtils.NO_AUTHORITIES));
    }

    @Test
    void latest_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("k", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/history").param("organizationId", "100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void latest_listsThePagesWithTheOverviewTitleWhenThereIsOne() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        fr.siamois.domain.models.UserHistoryEntry alone = new fr.siamois.domain.models.UserHistoryEntry();
        alone.setId(1L);
        alone.setMainUri("/action-unit/2");
        alone.setMainTitle("Projet A");
        alone.setMainIcon("bi bi-arrow-down-square");
        fr.siamois.domain.models.UserHistoryEntry paired = new fr.siamois.domain.models.UserHistoryEntry();
        paired.setId(2L);
        paired.setMainUri("/action-unit/2");
        paired.setMainTitle("Projet A");
        paired.setSecondaryUri("/recording-unit/9");
        paired.setSecondaryTitle("US 9");
        when(historyService.latest(any(UserInfo.class), eq(10))).thenReturn(java.util.List.of(alone, paired));

        mockMvc.perform(get("/api/v1/history").param("organizationId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Projet A"))
                .andExpect(jsonPath("$.data[0].secondaryUri").doesNotExist())
                .andExpect(jsonPath("$.data[1].title").value("US 9"))
                .andExpect(jsonPath("$.data[1].mainUri").value("/action-unit/2"))
                .andExpect(jsonPath("$.data[1].secondaryUri").value("/recording-unit/9"));
    }
}
