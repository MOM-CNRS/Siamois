package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.project.ProjectControllerApi;
import fr.siamois.ui.api.openapi.v1.controller.project.ProjectDocumentsControllerApi;
import fr.siamois.ui.api.openapi.v1.controller.project.ProjectRecordingUnitsControllerApi;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectAnswersProjector;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitAnswersProjector;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/projects/{id}/siblings} — the "fiche précédente/suivante" navigation
 * endpoint. Setup mirrors {@link ProjectControllerApiTest}: only {@link ActionUnitService} is
 * mocked, {@link ProjectApiService} runs for real so the sort-field validation
 * (CURSORABLE_PROJECT_SORT_FIELDS) is exercised, not stubbed away.
 */
@ExtendWith(MockitoExtension.class)
class ProjectSiblingsControllerApiTest {

    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private ProjectResponseMapper projectResponseMapper;
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
    private RecordingUnitResponseMapper recordingUnitResourceMapper;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private PhaseService phaseService;
    @Mock
    private ContainerService containerService;
    @Mock
    private DocumentWriteOpenApiService documentWriteOpenApiService;
    @Mock
    private BookmarkService bookmarkService;
    @Mock
    private HistoryAuditService historyAuditService;
    @Mock
    private ConceptLabelBatchResolver conceptLabelBatchResolver;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;
    private InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {
        lenient().when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

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
                containerService,
                bookmarkService,
                historyAuditService);
        ProjectControllerApi controller = new ProjectControllerApi(
                projectApiService,
                projectResponseMapper,
                recordingUnitResourceMapper,
                documentWriteOpenApiService,
                new ProjectListProjectionService(new ProjectAnswersProjector(), conceptLabelBatchResolver));

        ProjectRecordingUnitsControllerApi recordingUnitsController = new ProjectRecordingUnitsControllerApi(
                projectApiService,
                recordingUnitResourceMapper,
                new RecordingUnitListProjectionService(new RecordingUnitAnswersProjector(), conceptLabelBatchResolver));

        ProjectDocumentsControllerApi documentsController = new ProjectDocumentsControllerApi(
                projectApiService,
                documentWriteOpenApiService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller, recordingUnitsController, documentsController)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        person = new Person();
        person.setId(7L);
        person.setUsername("tester");
        person.setPassword("secret");
        person.setEmail("tester@example.org");

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
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                person, person.getPassword(), AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void stubCaller() {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
    }

    private AccessibleProjectForApi rowOf(long id, String name, OffsetDateTime creationTime) {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(id);
        au.setName(name);
        au.setCreationTime(creationTime);
        return new AccessibleProjectForApi(au, 0L, 0L);
    }

    @Test
    void getSiblings_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/projects/5/siblings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSiblings_projectNotAccessible_returns404() throws Exception {
        login();
        stubCaller();
        when(actionUnitService.findAccessibleProjectByKey(eq("55"), any()))
                .thenThrow(new fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/projects/55/siblings"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSiblings_synthethicSortField_returns400() throws Exception {
        login();
        stubCaller();
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), any()))
                .thenReturn(rowOf(5L, "Projet test", OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/projects/5/siblings").param("sort", "recordingUnitCount:asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSiblings_nullableSortField_returns400() throws Exception {
        login();
        stubCaller();
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), any()))
                .thenReturn(rowOf(5L, "Projet test", OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/projects/5/siblings").param("sort", "fullIdentifier:asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSiblings_unknownFilter_returns400() throws Exception {
        // ProjectListFilter.parse rejects an unknown f.<key> before the controller ever calls
        // projectApiService.findSiblings, so findAccessibleProjectByKey is never consulted — no
        // stub needed here, unlike the other 400 cases above.
        login();
        stubCaller();

        mockMvc.perform(get("/api/v1/projects/5/siblings").param("f.bogus", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSiblings_defaultSort_usesCreationTimeAscending() throws Exception {
        login();
        stubCaller();
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        AccessibleProjectForApi current = rowOf(5L, "Projet courant", now);
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), any())).thenReturn(current);

        ActionUnitDTO previousDto = new ActionUnitDTO();
        previousDto.setId(4L);
        previousDto.setName("Projet précédent");
        previousDto.setFullIdentifier("OA-4");

        ActionUnitDTO nextDto = new ActionUnitDTO();
        nextDto.setId(6L);
        nextDto.setName("Projet suivant");

        when(actionUnitService.findSiblingProject(
                eq(7L), any(), any(), any(), any(), eq("creationTime"), any(), any(), eq(5L), eq(false)))
                .thenReturn(Optional.of(previousDto));
        when(actionUnitService.findSiblingProject(
                eq(7L), any(), any(), any(), any(), eq("creationTime"), any(), any(), eq(5L), eq(true)))
                .thenReturn(Optional.of(nextDto));

        mockMvc.perform(get("/api/v1/projects/5/siblings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previous.id").value("4"))
                .andExpect(jsonPath("$.data.previous.label").value("OA-4"))
                .andExpect(jsonPath("$.data.previous.resourceUri").value("/action-unit/4"))
                .andExpect(jsonPath("$.data.next.id").value("6"))
                .andExpect(jsonPath("$.data.next.label").value("Projet suivant"))
                .andExpect(jsonPath("$.data.next.resourceUri").value("/action-unit/6"));
    }

    @Test
    void getSiblings_noOtherAccessibleProject_bothSidesNull() throws Exception {
        login();
        stubCaller();
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), any()))
                .thenReturn(rowOf(5L, "Seul projet", OffsetDateTime.now()));
        when(actionUnitService.findSiblingProject(
                eq(7L), any(), any(), any(), any(), anyString(), any(), any(), eq(5L), eq(false)))
                .thenReturn(Optional.empty());
        when(actionUnitService.findSiblingProject(
                eq(7L), any(), any(), any(), any(), anyString(), any(), any(), eq(5L), eq(true)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/5/siblings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previous").doesNotExist())
                .andExpect(jsonPath("$.data.next").doesNotExist());
    }

    @Test
    void getSiblings_passesSearchAndFilterThrough() throws Exception {
        login();
        stubCaller();
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), any()))
                .thenReturn(rowOf(5L, "Projet courant", OffsetDateTime.now()));
        when(actionUnitService.findSiblingProject(
                eq(7L), any(), any(), eq("foss"), any(), anyString(), any(), any(), eq(5L), anyBoolean()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/5/siblings").param("search", "foss"))
                .andExpect(status().isOk());

        verify(actionUnitService).findSiblingProject(
                eq(7L), any(), any(), eq("foss"), any(), eq("creationTime"), any(), any(), eq(5L), eq(false));
        verify(actionUnitService).findSiblingProject(
                eq(7L), any(), any(), eq("foss"), any(), eq("creationTime"), any(), any(), eq(5L), eq(true));
    }
}
