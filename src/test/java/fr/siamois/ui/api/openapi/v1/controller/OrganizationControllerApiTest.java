package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.OrganizationOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerApiTest {

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
    private PermissionService permissionService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private RecordingUnitResponseMapper recordingUnitResponseMapper;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;

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

        OrganizationControllerApi controller = new OrganizationControllerApi(
                recordingUnitService,
                recordingUnitResponseMapper,
                projectApiService,
                new OrganizationOpenApiMapper());

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
    void getOrganizations_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrganizations_invalidOffset_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/organizations").param("offset", "-1").param("limit", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrganizations_limitZero_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrganizations_limitExceedsMax_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/organizations")
                        .param("offset", "0")
                        .param("limit", String.valueOf(ProjectApiService.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrganizations_offsetNotMultipleOfLimit_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/organizations").param("offset", "5").param("limit", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrganizations_emptyList_returns200WithEmptyData() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Collections.emptySet());

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.offset").value(0));
    }

    @Test
    void getOrganizations_defaultParams_usesDefaults() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Collections.emptySet());

        mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.offset").value(0));
    }

    @Test
    void getOrganizations_success_sortedByNameDesc() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO a = new InstitutionDTO();
        a.setId(1L);
        a.setName("Alpha");
        a.setIdentifier("A");
        InstitutionDTO b = new InstitutionDTO();
        b.setId(2L);
        b.setName("Beta");
        b.setIdentifier("B");

        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(a, b));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20").param("sort", "name:desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Beta"))
                .andExpect(jsonPath("$.data[1].name").value("Alpha"));
    }

    @Test
    void getOrganizations_success_includesResourceType() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO one = new InstitutionDTO();
        one.setId(99L);
        one.setName("Org");
        one.setIdentifier("O-1");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(one));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resourceType").value("organizations"))
                .andExpect(jsonPath("$.data[0].identifier").value("O-1"));
    }

    @Test
    void getOrganizations_success_sortedByNameAsc() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO beta = new InstitutionDTO();
        beta.setId(2L);
        beta.setName("Beta");
        beta.setIdentifier("B");
        InstitutionDTO alpha = new InstitutionDTO();
        alpha.setId(1L);
        alpha.setName("Alpha");
        alpha.setIdentifier("A");

        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(beta, alpha));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20").param("sort", "name:asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Alpha"))
                .andExpect(jsonPath("$.data[0].resourceId").value("1"))
                .andExpect(jsonPath("$.data[1].name").value("Beta"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.offset").value(0));

        verify(institutionService).findInstitutionsOfPerson(eq(personDto));
    }

    @Test
    void getOrganizations_pagination_secondPage() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO a = new InstitutionDTO();
        a.setId(1L);
        a.setName("A");
        InstitutionDTO b = new InstitutionDTO();
        b.setId(2L);
        b.setName("B");

        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(a, b));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "1").param("limit", "1").param("sort", "name:asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("B"));
    }

    @Test
    void getOrganizations_offsetBeyondTotal_returnsEmptyDataWithTotal() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO only = new InstitutionDTO();
        only.setId(1L);
        only.setName("Solo");
        only.setIdentifier("S");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(only));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "10").param("limit", "10").param("sort", "name:asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void getOrganizations_unknownSortProperty_fallsBackToNameWithDirection() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO a = new InstitutionDTO();
        a.setId(1L);
        a.setName("Aaa");
        InstitutionDTO b = new InstitutionDTO();
        b.setId(2L);
        b.setName("Bbb");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(a, b));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20").param("sort", "notAField:desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Bbb"))
                .andExpect(jsonPath("$.data[1].name").value("Aaa"));
    }

    @Test
    void getOrganizations_success_sortedByIdAsc() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO id10 = new InstitutionDTO();
        id10.setId(10L);
        id10.setName("Z");
        id10.setIdentifier("z");
        InstitutionDTO id2 = new InstitutionDTO();
        id2.setId(2L);
        id2.setName("Y");
        id2.setIdentifier("y");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(id10, id2));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20").param("sort", "id:asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resourceId").value("2"))
                .andExpect(jsonPath("$.data[1].resourceId").value("10"));
    }

    @Test
    void getOrganizations_success_sortedByIdentifierDesc() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO alpha = new InstitutionDTO();
        alpha.setId(1L);
        alpha.setName("Alpha");
        alpha.setIdentifier("B");
        InstitutionDTO beta = new InstitutionDTO();
        beta.setId(2L);
        beta.setName("Beta");
        beta.setIdentifier("A");

        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(alpha, beta));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20").param("sort", "identifier:desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].identifier").value("B"))
                .andExpect(jsonPath("$.data[1].identifier").value("A"));
    }

    @Test
    void getOrganizations_success_sortedByCreationDateAsc() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO older = new InstitutionDTO();
        older.setId(1L);
        older.setName("Old");
        older.setIdentifier("O");
        older.setCreationDate(java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        InstitutionDTO newer = new InstitutionDTO();
        newer.setId(2L);
        newer.setName("New");
        newer.setIdentifier("N");
        newer.setCreationDate(java.time.OffsetDateTime.parse("2024-01-01T00:00:00Z"));

        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(newer, older));

        mockMvc.perform(get("/api/v1/organizations").param("offset", "0").param("limit", "20").param("sort", "creationDate:asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Old"))
                .andExpect(jsonPath("$.data[1].name").value("New"));
    }

    @Test
    void getById_returns501() throws Exception {
        login();
        mockMvc.perform(get("/api/v1/organizations/1"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getProjects_returns501() throws Exception {
        login();
        mockMvc.perform(get("/api/v1/organizations/1/projects"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getPlaces_returns501() throws Exception {
        login();
        mockMvc.perform(get("/api/v1/organizations/1/places"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getFinds_returns501() throws Exception {
        login();
        mockMvc.perform(get("/api/v1/organizations/1/mobiliers"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getRecordingUnitByFullIdentifier_success() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO org = new InstitutionDTO();
        org.setId(10L);
        org.setName("Org");
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(org));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(100L);
        ru.setFullIdentifier("RU-100");
        when(recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(eq("RU-100"), eq(10L), isNull()))
                .thenReturn(ru);

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setId("100");
        resource.setFullIdentifier("RU-100");
        when(recordingUnitResponseMapper.convert(ru)).thenReturn(resource);

        mockMvc.perform(get("/api/v1/organizations/10/recording-units/RU-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullIdentifier").value("RU-100"));
    }

    @Test
    void getRecordingUnitByFullIdentifier_withCounts() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO org = new InstitutionDTO();
        org.setId(10L);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(org));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(100L);
        when(recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(eq("RU-100"), eq(10L), eq(List.of("specimen"))))
                .thenReturn(ru);
        when(recordingUnitResponseMapper.convert(ru)).thenReturn(new RecordingUnitResource());

        mockMvc.perform(get("/api/v1/organizations/10/recording-units/RU-100").param("counts", "specimen"))
                .andExpect(status().isOk());

        verify(recordingUnitService).findByFullIdentifierAndInstitutionIdDTO("RU-100", 10L, List.of("specimen"));
    }

    @Test
    void getRecordingUnitByFullIdentifier_outOfScope_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO org = new InstitutionDTO();
        org.setId(10L);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(org));

        mockMvc.perform(get("/api/v1/organizations/99/recording-units/RU-100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRecordingUnits_success() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO org = new InstitutionDTO();
        org.setId(10L);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(org));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(1L);
        ru.setFullIdentifier("RU-1");
        when(recordingUnitService.findByInstitutionId(10L, 10, 0))
                .thenReturn(new PageImpl<>(List.of(ru), PageRequest.of(0, 10), 1));

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setId("1");
        when(recordingUnitResponseMapper.convert(ru)).thenReturn(resource);

        mockMvc.perform(get("/api/v1/organizations/10/recording-units").param("offset", "0").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void getRecordingUnits_outOfScope_returns403() throws Exception {
        login();
        when(personMapper.convert(person)).thenReturn(personDto);

        InstitutionDTO org = new InstitutionDTO();
        org.setId(10L);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(org));

        mockMvc.perform(get("/api/v1/organizations/99/recording-units"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRecordingUnits_invalidPagination_returns400() throws Exception {
        login();

        mockMvc.perform(get("/api/v1/organizations/10/recording-units").param("offset", "-1").param("limit", "10"))
                .andExpect(status().isBadRequest());
    }
}
