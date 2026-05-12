package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private RecordingUnitResponseMapper recordingUnitResourceMapper;

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
                personMapper);
        ProjectControllerApi controller = new ProjectControllerApi(
                projectApiService,
                projectResponseMapper,
                recordingUnitResourceMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
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
        when(projectResponseMapper.toResource(eq(row), eq("en"))).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects")
                        .param("offset", "0")
                        .param("limit", "20")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(eq(row), eq("en"));
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
        when(actionUnitService.findAccessibleProjectByKey(eq("55"), eq(Set.of(100L))))
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
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), eq(Set.of(100L)))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("5");
        resource.setName("Projet test");
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Projet test"))
                .andExpect(jsonPath("$.data.resourceType").value("projects"))
                .andExpect(jsonPath("$.data.resourceId").value("5"));

        verify(actionUnitService).findAccessibleProjectByKey(eq("5"), eq(Set.of(100L)));
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
        when(actionUnitService.findAccessibleProjectByKey(eq("INST-PROJ-2025"), eq(Set.of(100L)))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setResourceType("projects");
        resource.setId("12");
        resource.setName("Par full id");
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/{id}", "INST-PROJ-2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Par full id"));

        verify(actionUnitService).findAccessibleProjectByKey(eq("INST-PROJ-2025"), eq(Set.of(100L)));
    }

    @Test
    void getProjectById_passesAcceptLanguageToMapper() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(3L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey(eq("3"), eq(Set.of(100L)))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setId("3");
        when(projectResponseMapper.toResource(eq(row), eq("de"))).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/3").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE"))
                .andExpect(status().isOk());

        verify(projectResponseMapper).toResource(eq(row), eq("de"));
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
        when(actionUnitService.findAccessibleProjectByKey(eq("C309_01"), eq(Set.of(100L)))).thenReturn(row);

        ProjectResource resource = new ProjectResource();
        resource.setId("33");
        resource.setName("Chartres");
        when(projectResponseMapper.toResource(eq(row), anyString())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/C309_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Chartres"));

        verify(actionUnitService).findAccessibleProjectByKey(eq("C309_01"), eq(Set.of(100L)));
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
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), eq(Set.of(100L))))
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
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), eq(Set.of(100L)))).thenReturn(row);

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(42L);
        ruDto.setFullIdentifier("INST-PROJ-UE42");
        PageImpl<RecordingUnitDTO> page = new PageImpl<>(
                List.of(ruDto),
                PageRequest.of(0, 10),
                1L);
        when(recordingUnitService.findByActionUnitId(eq(5L), eq(10), eq(0), any(Sort.class))).thenReturn(page);

        RecordingUnitResource ruRes = new RecordingUnitResource(
                "42",
                "INST-PROJ-UE42",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        ruRes.setResourceType("recording-units");
        ruRes.setId("42");
        when(recordingUnitResourceMapper.convert(eq(ruDto))).thenReturn(ruRes);

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
        when(actionUnitService.findAccessibleProjectByKey(eq("5"), eq(Set.of(100L)))).thenReturn(row);

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
}
