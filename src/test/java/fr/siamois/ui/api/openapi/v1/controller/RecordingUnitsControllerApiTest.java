package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecordingUnitsControllerApiTest {

    @Mock
    private InstitutionService institutionService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private DocumentService documentService;
    @Mock
    private ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;

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
                documentService,
                projectDocumentOpenApiMapper,
                personMapper);

        RecordingUnitsControllerApi controller = new RecordingUnitsControllerApi(
                projectApiService,
                recordingUnitOpenApiService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        person = new Person();
        person.setId(1L);
        personDto = new PersonDTO();
        personDto.setId(1L);
        institutionDto = new InstitutionDTO();
        institutionDto.setId(10L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                person, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getById_returns200_withData() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource(
                null, null, null, null, null, null, null, null, null, null, null);
        resource.setResourceType("recording-units");
        resource.setId("5");
        when(recordingUnitOpenApiService.buildMobileDetail(
                eq("5"), eq(personDto), eq(Set.of(10L)), eq(null), eq("fr")))
                .thenReturn(new RecordingUnitMobileDetailData(resource, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/recording-units/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordingUnit.resourceId").value("5"))
                .andExpect(jsonPath("$.data.recordingUnit.resourceType").value("recording-units"));
    }

    @Test
    void getById_withCounts_passesToService() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource(
                null, null, null, null, null, null, null, null, null, null, null);
        resource.setResourceType("recording-units");
        resource.setId("5");
        when(recordingUnitOpenApiService.buildMobileDetail(
                eq("5"), eq(personDto), eq(Set.of(10L)), eq(List.of("specimen")), eq("fr")))
                .thenReturn(new RecordingUnitMobileDetailData(resource, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/recording-units/5").param("counts", "specimen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordingUnit.resourceId").value("5"));
    }

    @Test
    void getById_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/recording-units/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_withAcceptLanguage_passesResolvedLangToService() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource(
                null, null, null, null, null, null, null, null, null, null, null);
        resource.setResourceType("recording-units");
        resource.setId("7");
        when(recordingUnitOpenApiService.buildMobileDetail(
                eq("7"), eq(personDto), eq(Set.of(10L)), eq(null), eq("en")))
                .thenReturn(new RecordingUnitMobileDetailData(resource, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/recording-units/7")
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordingUnit.resourceId").value("7"));

        verify(recordingUnitOpenApiService).buildMobileDetail(
                eq("7"), eq(personDto), eq(Set.of(10L)), eq(null), eq("en"));
    }

    @Test
    void getById_withSeveralInstitutions_passesFullScopeToService() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        RecordingUnitResource resource = new RecordingUnitResource(
                null, null, null, null, null, null, null, null, null, null, null);
        resource.setResourceType("recording-units");
        resource.setId("1");
        when(recordingUnitOpenApiService.buildMobileDetail(
                eq("x-id"), eq(personDto), eq(Set.of(10L, 20L)), eq(null), eq("fr")))
                .thenReturn(new RecordingUnitMobileDetailData(resource, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/recording-units/x-id"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildMobileDetail(
                eq("x-id"), eq(personDto), eq(Set.of(10L, 20L)), eq(null), eq("fr"));
    }

    @Test
    void getById_whenRecordingUnitNotFound_returns404() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(recordingUnitOpenApiService.buildMobileDetail(any(), any(), any(), any(), any()))
                .thenThrow(new RecordingUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/recording-units/404-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing"));
    }

    @Test
    void getAll_returns501() throws Exception {
        mockMvc.perform(get("/api/v1/recording-units"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getFinds_returns501() throws Exception {
        mockMvc.perform(get("/api/v1/recording-units/5/finds"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void getRelations_returns200_withData() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        StratigraphicRelationshipDTO rel = new StratigraphicRelationshipDTO();
        when(recordingUnitOpenApiService.buildRecordingUnitRelations(eq("5"), eq(Set.of(10L))))
                .thenReturn(new RecordingUnitRelationsData(List.of(rel), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/recording-units/5/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stratigraphicRelationships").isArray())
                .andExpect(jsonPath("$.data.stratigraphicRelationships.length()").value(1))
                .andExpect(jsonPath("$.data.parents").isArray())
                .andExpect(jsonPath("$.data.parents.length()").value(0))
                .andExpect(jsonPath("$.data.children").isArray())
                .andExpect(jsonPath("$.data.children.length()").value(0));

        verify(recordingUnitOpenApiService).buildRecordingUnitRelations(eq("5"), eq(Set.of(10L)));
    }

    @Test
    void getRelations_withSeveralInstitutions_passesFullScopeToService() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        when(recordingUnitOpenApiService.buildRecordingUnitRelations(eq("ru-key"), eq(Set.of(10L, 20L))))
                .thenReturn(new RecordingUnitRelationsData(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/recording-units/ru-key/relations"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildRecordingUnitRelations(eq("ru-key"), eq(Set.of(10L, 20L)));
    }

    @Test
    void getRelations_returns200_withParentsAndChildrenInPayload() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitSummaryDTO parent = new RecordingUnitSummaryDTO();
        parent.setId(99L);
        parent.setFullIdentifier("INST-A-UE-P");
        RecordingUnitSummaryDTO child = new RecordingUnitSummaryDTO();
        child.setId(100L);
        child.setFullIdentifier("INST-A-UE-C");
        when(recordingUnitOpenApiService.buildRecordingUnitRelations(eq("5"), eq(Set.of(10L))))
                .thenReturn(new RecordingUnitRelationsData(List.of(), List.of(parent), List.of(child)));

        mockMvc.perform(get("/api/v1/recording-units/5/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parents[0].id").value(99))
                .andExpect(jsonPath("$.data.parents[0].fullIdentifier").value("INST-A-UE-P"))
                .andExpect(jsonPath("$.data.children[0].id").value(100))
                .andExpect(jsonPath("$.data.children[0].fullIdentifier").value("INST-A-UE-C"));
    }

    @Test
    void getRelations_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/recording-units/5/relations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRelations_whenRecordingUnitNotFound_returns404() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(recordingUnitOpenApiService.buildRecordingUnitRelations(any(), any()))
                .thenThrow(new RecordingUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/recording-units/404-key/relations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing"));
    }
}
