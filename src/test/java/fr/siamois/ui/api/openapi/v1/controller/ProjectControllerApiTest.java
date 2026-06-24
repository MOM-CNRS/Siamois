package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
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
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectFormData;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private PermissionService permissionService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private RecordingUnitResponseMapper recordingUnitResourceMapper;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private DocumentWriteOpenApiService documentWriteOpenApiService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;
    private InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {
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
                permissionService,
                conceptService,
                conceptMapper,
                recordingUnitOpenApiService);
        ProjectControllerApi controller = new ProjectControllerApi(
                projectApiService,
                projectResponseMapper,
                recordingUnitResourceMapper,
                recordingUnitOpenApiService,
                documentWriteOpenApiService);

        ProjectRecordingUnitsControllerApi recordingUnitsController = new ProjectRecordingUnitsControllerApi(
                projectApiService,
                recordingUnitResourceMapper);

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
                eq(Set.of(100L)),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("1");
        resource.setName("Fouille A");
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects").param("offset", "0").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Fouille A"))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.offset").value(0));

        verify(actionUnitService).findAccessibleProjects(
                eq(Set.of(100L)),
                isNull(),
                isNull(),
                any(Pageable.class));
    }

    @Test
    void getAllProjects_passesAcceptLanguageToMapper() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(1L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjects(eq(Set.of(100L)), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ProjectResource resource = new ProjectResource();
        resource.setId("1");
        when(projectResponseMapper.toResource(row, "en")).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(row, "en");
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
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Projet test"))
                .andExpect(jsonPath("$.data.resourceType").value("projects"))
                .andExpect(jsonPath("$.data.id").value("5"));

        verify(actionUnitService).findAccessibleProjectByKey("5", Set.of(100L));
    }

    @Test
    void getProjectUiForm_success_returnsPayload() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ProjectFormData formData = new ProjectFormData(
                new FormResource(null, "Details", "", "{}"),
                Map.of());
        when(recordingUnitOpenApiService.buildProjectUiForm(100L, personDto, "fr"))
                .thenReturn(formData);

        mockMvc.perform(get("/api/v1/projects/form")
                        .param("organizationId", "100")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.form.name").value("Details"))
                .andExpect(jsonPath("$.data.vocabulariesByFieldCode").doesNotExist());

        verify(recordingUnitOpenApiService).buildProjectUiForm(100L, personDto, "fr");
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
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

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
        when(projectResponseMapper.toResource(row, "de")).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/3").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(row, "de");
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
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

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
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class))).thenReturn(page);

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

        verify(recordingUnitService).findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class));
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
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class))).thenReturn(page);

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
                }));
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
        when(permissionService.isInstitutionManager(any())).thenReturn(false);
        when(permissionService.isActionManager(any())).thenReturn(false);

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
        when(permissionService.isInstitutionManager(any())).thenReturn(true);

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
        when(projectResponseMapper.toResource(any(AccessibleProjectForApi.class), anyString())).thenReturn(resource);

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
        when(permissionService.hasWritePermission(any(), any())).thenReturn(false);

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
        when(permissionService.hasWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), any(), any())).thenReturn(au);

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("8");
        resource.setName("Après");
        when(projectResponseMapper.toResource(any(AccessibleProjectForApi.class), anyString())).thenReturn(resource);

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
        when(permissionService.hasWritePermission(any(), any())).thenReturn(true);

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
        when(permissionService.hasWritePermission(any(), any())).thenReturn(true);

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
        when(permissionService.hasWritePermission(any(), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/projects/9"))
                .andExpect(status().isNoContent());

        verify(actionUnitService).deleteProjectWhenEmpty(9L);
    }
}
