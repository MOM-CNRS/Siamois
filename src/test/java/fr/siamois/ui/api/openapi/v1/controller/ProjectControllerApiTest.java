package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
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
import fr.siamois.domain.models.actionunit.form.ActionUnitForm;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
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
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectAnswersProjector;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitAnswersProjector;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectControllerApiTest {

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
                new RecordingUnitListProjectionService(new RecordingUnitAnswersProjector(), conceptLabelBatchResolver), mock(ResourceBookmarkService.class));

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

    @Test
    void getAllProjects_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllProjects_invalidOffset_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/projects").param("offset", "-1").param("limit", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllProjects_offsetNotMultipleOfLimit_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/projects").param("offset", "5").param("limit", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllProjects_forbiddenOrganization_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(get("/api/v1/projects")
                        .param("organizationId", "999")
                        .param("offset", "0")
                        .param("limit", "20"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllProjects_success_returnsJsonAndTotalCountHeader() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(1L);
        au.setName("Fouille A");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 4L, 2L);

        when(actionUnitService.findAccessibleProjects(
                anyLong(),
                eq(Set.of(100L)),
                isNull(),
                isNull(),
                any(Pageable.class),
                isNull(),
                any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("1");
        resource.setName("Fouille A");
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects").param("offset", "0").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Fouille A"))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.offset").value(0));

        verify(actionUnitService).findAccessibleProjects(
                anyLong(),
                eq(Set.of(100L)),
                isNull(),
                isNull(),
                any(Pageable.class),
                isNull(),
                any());
    }

    @Test
    void getAllProjects_passesAcceptLanguageToMapper() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(1L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjects(
                anyLong(), eq(Set.of(100L)), isNull(), isNull(), any(Pageable.class), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ProjectResource resource = new ProjectResource();
        resource.setId("1");
        when(projectResponseMapper.toResource(eq(row), eq("en"), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(eq(row), eq("en"), any(), anyBoolean(), any(), any());
    }

    /**
     * Régression : {@code sort} était déclaré {@code @RequestParam(name = "name:asc")}, donc la clé de
     * query-string attendue était littéralement {@code name:asc} et {@code ?sort=} n'était jamais lu —
     * le tri par colonne n'a jamais fonctionné, masqué par le fait que le défaut coïncidait avec celui
     * du frontend. Aucun test ne couvrait ce paramètre.
     */
    @Test
    void getAllProjects_bindsSortParamAndAppliesDirection() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(actionUnitService.findAccessibleProjects(
                anyLong(), eq(Set.of(100L)), isNull(), isNull(), any(Pageable.class), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .param("sort", "fullIdentifier:desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(actionUnitService).findAccessibleProjects(
                anyLong(), eq(Set.of(100L)), isNull(), isNull(), pageable.capture(), isNull(), any());
        assertThat(pageable.getValue().getSort().getOrderFor("fullIdentifier").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    /**
     * {@code answers} est opt-in : sans {@code ?fields=}, la liste ne paie ni la projection ni le lot de
     * libellés, et la réponse n'a pas la clé du tout (le mapper reçoit {@code null}).
     */
    @Test
    void getAllProjects_withoutFieldsParam_projectsNoAnswers() throws Exception {
        AccessibleProjectForApi row = stubOneProjectRow();

        mockMvc.perform(get("/api/v1/projects").param("offset", "0").param("limit", "20"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(eq(row), anyString(), any(), anyBoolean(), any(), isNull());
    }

    @Test
    void getAllProjects_withFieldsDefault_projectsTheDefaultVisibleColumns() throws Exception {
        AccessibleProjectForApi row = stubOneProjectRow();

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .param("fields", "default"))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> answers = ArgumentCaptor.forClass(Map.class);
        verify(projectResponseMapper).toResource(
                eq(row), anyString(), any(), anyBoolean(), any(), answers.capture());
        assertThat(answers.getValue()).isNotNull()
                .containsKeys(String.valueOf(ActionUnitForm.STATUS_FIELD.getId()),
                        String.valueOf(ActionUnitForm.OA_CODE_FIELD.getId()))
                .doesNotContainKey(String.valueOf(ActionUnitForm.ZMIN_FIELD.getId()));
    }

    private AccessibleProjectForApi stubOneProjectRow() {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(1L);
        au.setOaCode("OA-1");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjects(
                anyLong(), eq(Set.of(100L)), isNull(), isNull(), any(Pageable.class), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ProjectResource resource = new ProjectResource();
        resource.setId("1");
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);
        return row;
    }

    /**
     * Régression sur le contrat de {@link fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter} :
     * les params {@code f.*} atteignent effectivement {@code pageAccessibleProjects}, la surface
     * ({@code f.name}=contains, {@code f.status}=concept-one-in) et le 400 sur un filtre inconnu.
     */
    @Test
    void getAllProjects_bindsFPrefixedFiltersOntoPageAccessibleProjects() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(actionUnitService.findAccessibleProjects(
                anyLong(), eq(Set.of(100L)), isNull(), isNull(), any(Pageable.class), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .param("f.name", "foss")
                        .param("f.status", "12"))
                .andExpect(status().isOk());

        ArgumentCaptor<fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter> filterCaptor =
                ArgumentCaptor.forClass(fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter.class);
        verify(actionUnitService).findAccessibleProjects(
                anyLong(), eq(Set.of(100L)), isNull(), isNull(), any(Pageable.class), isNull(), filterCaptor.capture());
        assertThat(filterCaptor.getValue().containsFilters()).containsEntry("name", "foss");
        assertThat(filterCaptor.getValue().conceptOneInFilters()).containsEntry("status", List.of(12L));
    }

    @Test
    void getAllProjects_unknownFilterKey_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .param("f.zmin", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(actionUnitService);
    }

    @Test
    void getAllProjects_unknownSortField_returns400() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .param("sort", "bogus:asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProjectById_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/projects/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProjectById_notFound_returns404() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(actionUnitService.findAccessibleProjectByKey("55", Set.of(100L)))
                .thenThrow(new ActionUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/projects/55"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectById_success_returnsWrappedResource() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        au.setName("Projet test");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 1L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("5");
        resource.setName("Projet test");
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Projet test"))
                .andExpect(jsonPath("$.data.resourceType").value("projects"))
                .andExpect(jsonPath("$.data.id").value("5"));

        verify(actionUnitService).findAccessibleProjectByKey("5", Set.of(100L));
    }

    /**
     * {@code answers} est opt-in sur le détail comme sur la liste : sans {@code ?fields=}, la réponse n'a
     * pas la clé du tout et ne paie ni la projection ni le lot de libellés.
     */
    @Test
    void getProjectById_withoutFieldsParam_projectsNoAnswers() throws Exception {
        AccessibleProjectForApi row = stubOneProjectById();

        mockMvc.perform(get("/api/v1/projects/5"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(eq(row), anyString(), any(), anyBoolean(), any(), isNull());
    }

    /**
     * Ce que demande la fiche projet React : tout le catalogue, pas une sélection de colonnes — c'est ce
     * qui lui permet d'afficher les 33 champs d'{@code ActionUnit.DETAILS_FORM} et pas seulement les sept
     * propriétés plates de {@link ProjectResource}.
     */
    @Test
    void getProjectById_withFieldsAll_projectsTheWholeCatalog() throws Exception {
        AccessibleProjectForApi row = stubOneProjectById();

        mockMvc.perform(get("/api/v1/projects/5").param("fields", "all"))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> answers = ArgumentCaptor.forClass(Map.class);
        verify(projectResponseMapper).toResource(
                eq(row), anyString(), any(), anyBoolean(), any(), answers.capture());
        assertThat(answers.getValue()).isNotNull()
                .containsKeys(String.valueOf(ActionUnitForm.OA_CODE_FIELD.getId()),
                        String.valueOf(ActionUnitForm.STATUS_FIELD.getId()),
                        // Hors des colonnes par défaut de la liste : présent uniquement avec "all".
                        String.valueOf(ActionUnitForm.ZMIN_FIELD.getId()));
    }

    @Test
    void getProjectById_withFieldsDefault_projectsOnlyTheDefaultVisibleColumns() throws Exception {
        AccessibleProjectForApi row = stubOneProjectById();

        mockMvc.perform(get("/api/v1/projects/5").param("fields", "default"))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> answers = ArgumentCaptor.forClass(Map.class);
        verify(projectResponseMapper).toResource(
                eq(row), anyString(), any(), anyBoolean(), any(), answers.capture());
        assertThat(answers.getValue()).isNotNull()
                .containsKey(String.valueOf(ActionUnitForm.OA_CODE_FIELD.getId()))
                .doesNotContainKey(String.valueOf(ActionUnitForm.ZMIN_FIELD.getId()));
    }

    private AccessibleProjectForApi stubOneProjectById() {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        au.setOaCode("OA-1");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setId("5");
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);
        return row;
    }

    /**
     * Clé métier non numérique (fullIdentifier, etc.) : une seule variable de chemin.
     * Les identifiants contenant « / » doivent être encodés ({@code %2F}) côté client ; beaucoup de piles Servlet
     * rejettent {@code %2F} dans le chemin (erreur Servlet en MockMvc / Tomcat), donc ce cas n’est pas rejoué ici.
     */
    @Test
    void getProjectById_nonNumericKey_passedToService() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(12L);
        au.setName("Par full id");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("INST-PROJ-2025", Set.of(100L))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("12");
        resource.setName("Par full id");
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/{id}", "INST-PROJ-2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Par full id"));

        verify(actionUnitService).findAccessibleProjectByKey("INST-PROJ-2025", Set.of(100L));
    }

    @Test
    void getProjectById_passesAcceptLanguageToMapper() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(3L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("3", Set.of(100L))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setId("3");
        when(projectResponseMapper.toResource(eq(row), eq("de"), any(), anyBoolean(), any(), any())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/3").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(eq(row), eq("de"), any(), anyBoolean(), any(), any());
    }

    @Test
    void getProjectById_shortIdentifierPassedToService() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(33L);
        au.setName("Chartres");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("C309_01", Set.of(100L))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setId("33");
        resource.setName("Chartres");
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/C309_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Chartres"));

        verify(actionUnitService).findAccessibleProjectByKey("C309_01", Set.of(100L));
    }

    @Test
    void getProjectRecordingUnits_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/projects/5/recording-units"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProjectRecordingUnits_invalidPagination_returns400() throws Exception {
        login();
        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("offset", "1").param("limit", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProjectRecordingUnits_projectNotFound_returns404() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L)))
                .thenThrow(new ActionUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/projects/5/recording-units"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectRecordingUnits_success_returnsListAndTotalHeader() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 1L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(42L);
        ruDto.setFullIdentifier("INST-PROJ-UE42");
        PageImpl<RecordingUnitDTO> page = new PageImpl<>(
                List.of(ruDto),
                PageRequest.of(0, 10),
                1L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class))).thenReturn(page);

        RecordingUnitResource ruRes = new RecordingUnitResource();
        ruRes.setIdentifier("42");
        ruRes.setFullIdentifier("INST-PROJ-UE42");
        ruRes.setResourceType("recording-units");
        ruRes.setId("42");
        when(recordingUnitResourceMapper.convert(ruDto)).thenReturn(ruRes);

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("offset", "0").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].fullIdentifier").value("INST-PROJ-UE42"));

        verify(recordingUnitService).findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class));
    }

    @Test
    void getProjectRecordingUnits_sortParam_passedToService() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        PageImpl<RecordingUnitDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/projects/5/recording-units")
                        .param("offset", "0")
                        .param("limit", "10")
                        .param("sort", "fullIdentifier:asc"))
                .andExpect(status().isOk());

        verify(recordingUnitService).findByActionUnitId(eq(5L), eq(10), eq(0),
                argThat((Sort s) -> {
                    for (Sort.Order o : s) {
                        if ("fullIdentifier".equals(o.getProperty()) && o.isAscending()) {
                            return true;
                        }
                    }
                    return false;
                }), any(fr.siamois.dto.FilterDTO.class));
    }

    @Test
    void getProjectRecordingUnits_unknownSortField_returns400() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("sort", "nope:asc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recordingUnitService);
    }

    @Test
    void getProjectRecordingUnits_synteticSortField_passedToService() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        PageImpl<RecordingUnitDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("sort", "parentsCount:desc"))
                .andExpect(status().isOk());

        verify(recordingUnitService).findByActionUnitId(eq(5L), eq(10), eq(0),
                argThat((Sort s) -> s.getOrderFor("parentsCount") != null
                        && s.getOrderFor("parentsCount").isDescending()),
                any(fr.siamois.dto.FilterDTO.class));
    }

    @Test
    void getProjectRecordingUnits_unknownFilterKey_returns400() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("f.description", "x"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recordingUnitService, actionUnitService);
    }

    @Test
    void getProjectRecordingUnits_actionUnitFilter_returns400_pathAlreadyScopesToOneProject() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("f.actionUnit", "9"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recordingUnitService, actionUnitService);
    }

    @Test
    void getProjectRecordingUnits_fPrefixedFilters_bindOntoRecordingUnitListFilter() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        PageImpl<RecordingUnitDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("f.type", "12"))
                .andExpect(status().isOk());

        ArgumentCaptor<fr.siamois.dto.FilterDTO> filterCaptor = ArgumentCaptor.forClass(fr.siamois.dto.FilterDTO.class);
        verify(recordingUnitService).findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), filterCaptor.capture());
        assertThat(filterCaptor.getValue().containsColumn("type")).isTrue();
        assertThat(filterCaptor.getValue().valueAsIdListOf("type")).containsExactly(12L);
    }

    @Test
    void getProjectRecordingUnits_withoutFieldsParam_rowsHaveNoAnswersKey() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(42L);
        ruDto.setFullIdentifier("INST-PROJ-UE42");
        PageImpl<RecordingUnitDTO> page = new PageImpl<>(List.of(ruDto), PageRequest.of(0, 10), 1L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class)))
                .thenReturn(page);

        RecordingUnitResource ruRes = new RecordingUnitResource();
        ruRes.setId("42");
        ruRes.setFullIdentifier("INST-PROJ-UE42");
        ruRes.setResourceType("recording-units");
        when(recordingUnitResourceMapper.convert(ruDto)).thenReturn(ruRes);

        mockMvc.perform(get("/api/v1/projects/5/recording-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].answers").doesNotExist());
    }

    @Test
    void getProjectRecordingUnits_permissionsPresent_reflectingCallerCanEdit() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(), eq(5L), anyString(), anyString(), anyString()))
                .thenReturn(true);

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(42L);
        ruDto.setFullIdentifier("INST-PROJ-UE42");
        PageImpl<RecordingUnitDTO> page = new PageImpl<>(List.of(ruDto), PageRequest.of(0, 10), 1L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class)))
                .thenReturn(page);

        RecordingUnitResource ruRes = new RecordingUnitResource();
        ruRes.setId("42");
        ruRes.setFullIdentifier("INST-PROJ-UE42");
        ruRes.setResourceType("recording-units");
        when(recordingUnitResourceMapper.convert(ruDto)).thenReturn(ruRes);

        mockMvc.perform(get("/api/v1/projects/5/recording-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]._permissions.canEdit").value(true));
    }

    @Test
    void getProjectRecordingUnits_fieldsDefault_projectsExactlyTheDefaultVisibleFieldIds() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L))).thenReturn(row);

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(42L);
        ruDto.setFullIdentifier("INST-PROJ-UE42");
        PageImpl<RecordingUnitDTO> page = new PageImpl<>(List.of(ruDto), PageRequest.of(0, 10), 1L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class), any(fr.siamois.dto.FilterDTO.class)))
                .thenReturn(page);

        RecordingUnitResource ruRes = new RecordingUnitResource();
        ruRes.setId("42");
        ruRes.setFullIdentifier("INST-PROJ-UE42");
        ruRes.setResourceType("recording-units");
        when(recordingUnitResourceMapper.convert(ruDto)).thenReturn(ruRes);

        Set<String> expectedIds = fr.siamois.ui.table.definitions.RecordingUnitTableColumnDefaults.defaultVisibleFieldIds();
        assertThat(expectedIds).isNotEmpty();

        mockMvc.perform(get("/api/v1/projects/5/recording-units").param("fields", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].answers", org.hamcrest.Matchers.aMapWithSize(expectedIds.size())));
    }

    @Test
    void getProjectDocuments_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/projects/5/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProjectDocuments_projectNotFound_returns404() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(actionUnitService.findAccessibleProjectByKey("5", Set.of(100L)))
                .thenThrow(new ActionUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/projects/5/documents"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectDocuments_success_returnsDocuments() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(100L))).thenReturn(row);

        Document doc = mock(Document.class);
        when(documentService.findForActionUnit(au)).thenReturn(List.of(doc));

        DocumentResource dr = new DocumentResource();
        dr.setResourceType("documents");
        dr.setId("100");
        dr.setTitle("Plan de fouille");
        when(projectDocumentOpenApiMapper.toResource(same(doc))).thenReturn(dr);

        mockMvc.perform(get("/api/v1/projects/7/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("100"))
                .andExpect(jsonPath("$.data[0].resourceType").value("documents"))
                .andExpect(jsonPath("$.data[0].title").value("Plan de fouille"));

        verify(documentService).findForActionUnit(au);
        verify(projectDocumentOpenApiMapper).toResource(same(doc));
    }

    @Test
    void getProjectDocuments_success_emptyList() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(2L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("2", Set.of(100L))).thenReturn(row);
        when(documentService.findForActionUnit(au)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects/2/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void createProject_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":100,\"name\":\"N\",\"identifier\":\"ID\",\"typeConceptId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProject_forbiddenWhenNotManager_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(institutionService.findById(100L)).thenReturn(institutionDto);
        when(profilePermissionService.hasActionUnitCreatePermission(any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":100,\"name\":\"Nouveau\",\"identifier\":\"NOU\",\"typeConceptId\":42}"))
                .andExpect(status().isForbidden());

        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void createProject_success_returns201AndCallsSave() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(institutionService.findById(100L)).thenReturn(institutionDto);
        when(profilePermissionService.hasActionUnitCreatePermission(any())).thenReturn(true);

        fr.siamois.domain.models.vocabulary.Concept typeConcept = mock(fr.siamois.domain.models.vocabulary.Concept.class);
        when(conceptService.findById(42L)).thenReturn(java.util.Optional.of(typeConcept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(42L);
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);

        ActionUnitDTO saved = new ActionUnitDTO();
        saved.setId(77L);
        saved.setName("Nouveau");
        saved.setIdentifier("NOU");
        saved.setCreatedByInstitution(institutionDto);
        when(actionUnitService.save(any(), any(), any())).thenReturn(saved);
        when(actionUnitService.findAccessibleProjectByKey("77", Set.of(100L)))
                .thenReturn(new AccessibleProjectForApi(saved, 0L, 0L));

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("77");
        resource.setName("Nouveau");
        when(projectResponseMapper.toResource(any(AccessibleProjectForApi.class), anyString(), any(), anyBoolean())).thenReturn(resource);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":100,\"name\":\"Nouveau\",\"identifier\":\"NOU\",\"typeConceptId\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Nouveau"));

        verify(actionUnitService).save(any(), argThat((ActionUnitDTO d) ->
                "Nouveau".equals(d.getName()) && "NOU".equals(d.getIdentifier())), eq(typeDto));
        verify(recordingUnitOpenApiService).applySystemProjectFormFieldAnswers(any(), any(), same(personDto), anyString());
    }

    @Test
    void patchProject_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(patch("/api/v1/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchProject_writeForbidden_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(1L);
        au.setName("P");
        au.setCreatedByInstitution(institutionDto);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("1", Set.of(100L))).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(false);

        mockMvc.perform(patch("/api/v1/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Autre\"}"))
                .andExpect(status().isForbidden());

        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void patchProject_success_returns200AndCallsSave() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(8L);
        au.setName("Avant");
        au.setCreatedByInstitution(institutionDto);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("8", Set.of(100L)))
                .thenReturn(row)
                .thenAnswer(invocation -> new AccessibleProjectForApi(au, 0L, 0L));
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), any(), any())).thenReturn(au);

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("8");
        resource.setName("Après");
        when(projectResponseMapper.toResource(any(AccessibleProjectForApi.class), anyString(), any(), anyBoolean())).thenReturn(resource);

        mockMvc.perform(patch("/api/v1/projects/8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Après\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Après"));

        verify(actionUnitService).save(any(), argThat((ActionUnitDTO d) -> "Après".equals(d.getName())), any());
    }


    @Test
    void deleteProject_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(delete("/api/v1/projects/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteProject_whenProjectHasRecordingUnits_returns409() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(3L);
        au.setCreatedByInstitution(institutionDto);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 2L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("3", Set.of(100L))).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/projects/3"))
                .andExpect(status().isConflict());

        verify(actionUnitService, never()).deleteProjectWhenEmpty(anyLong());
    }

    @Test
    void deleteProject_whenProjectHasChildProjects_returns409() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(4L);
        au.setCreatedByInstitution(institutionDto);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 1L);
        when(actionUnitService.findAccessibleProjectByKey("4", Set.of(100L))).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/projects/4"))
                .andExpect(status().isConflict());

        verify(actionUnitService, never()).deleteProjectWhenEmpty(anyLong());
    }

    @Test
    void deleteProject_success_returns204() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(9L);
        au.setCreatedByInstitution(institutionDto);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("9", Set.of(100L))).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/projects/9"))
                .andExpect(status().isNoContent());

        verify(actionUnitService).deleteProjectWhenEmpty(9L);
    }
}
