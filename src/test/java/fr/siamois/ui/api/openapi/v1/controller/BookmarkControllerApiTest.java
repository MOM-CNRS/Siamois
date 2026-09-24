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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generic bookmark endpoints (plan §5) — not Project-specific, but the caller-scope/institution
 * plumbing they share with the project endpoints comes from ProjectApiService, so this is
 * exercised the same way the project controller tests are.
 */
@ExtendWith(MockitoExtension.class)
class BookmarkControllerApiTest {

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
                containerService,
                bookmarkService,
                historyAuditService);

        BookmarkControllerApi controller = new BookmarkControllerApi(projectApiService, bookmarkService);

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
    void create_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(post("/api/v1/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceUri\":\"/action-unit/1\",\"organizationId\":100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_missingResourceUri_returns400() throws Exception {
        login();

        mockMvc.perform(post("/api/v1/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_organizationOutOfScope_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(post("/api/v1/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceUri\":\"/action-unit/1\",\"organizationId\":999}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_success_delegatesToBookmarkServiceAndReturns201() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(post("/api/v1/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceUri\":\"/action-unit/1\",\"titleCode\":\"Mon projet\",\"organizationId\":100}"))
                .andExpect(status().isCreated());

        verify(bookmarkService).save(any(UserInfo.class), eq("/action-unit/1"), eq("Mon projet"));
    }

    @Test
    void delete_success_delegatesToBookmarkServiceAndReturns204() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(delete("/api/v1/bookmarks")
                        .param("resourceUri", "/action-unit/1")
                        .param("organizationId", "100"))
                .andExpect(status().isNoContent());

        verify(bookmarkService).deleteBookmark(any(UserInfo.class), eq("/action-unit/1"));
    }

    @Test
    void status_returnsTheCallersBookmarkFlagForTheResource() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(bookmarkService.isRessourceBookmarkedByUser(any(UserInfo.class), eq("/action-unit"))).thenReturn(true);

        mockMvc.perform(get("/api/v1/bookmarks/status")
                        .param("resourceUri", "/action-unit")
                        .param("organizationId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));
    }

    @Test
    void status_organizationOutOfScope_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(get("/api/v1/bookmarks/status")
                        .param("resourceUri", "/action-unit")
                        .param("organizationId", "999"))
                .andExpect(status().isForbidden());
    }
}
