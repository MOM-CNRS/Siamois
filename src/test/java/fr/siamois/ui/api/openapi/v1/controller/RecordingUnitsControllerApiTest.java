package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitsControllerApi;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationsData;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitChildrenControllerApi;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitDocumentsControllerApi;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitFindsControllerApi;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitParentsControllerApi;
import fr.siamois.ui.api.openapi.v1.controller.recordingunit.RecordingUnitRelationsControllerApi;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private PersonMapper personMapper;
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

        RecordingUnitsControllerApi controller = new RecordingUnitsControllerApi(
                projectApiService,
                recordingUnitOpenApiService);
        RecordingUnitChildrenControllerApi childrenController = new RecordingUnitChildrenControllerApi(
                projectApiService,
                recordingUnitOpenApiService);
        RecordingUnitFindsControllerApi findsController = new RecordingUnitFindsControllerApi(projectApiService);
        RecordingUnitDocumentsControllerApi documentsController = new RecordingUnitDocumentsControllerApi(
                projectApiService, documentWriteOpenApiService);
        RecordingUnitRelationsControllerApi relationsController = new RecordingUnitRelationsControllerApi(
                projectApiService, recordingUnitOpenApiService);
        RecordingUnitParentsControllerApi parentsController = new RecordingUnitParentsControllerApi(
                projectApiService, recordingUnitOpenApiService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                controller, childrenController, findsController, documentsController,
                relationsController, parentsController)
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

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setResourceType("recording-units");
        resource.setId("5");
        when(recordingUnitOpenApiService.buildMobileDetail(
                "5", personDto, Set.of(10L), null, "fr"))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/recording-units/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceId").value("5"))
                .andExpect(jsonPath("$.data.resourceType").value("recording-units"));
    }

    @Test
    void post_create_returns201() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setResourceType("recording-units");
        resource.setId("99");
        when(recordingUnitOpenApiService.createRecordingUnit(any(), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(resource);

        mockMvc.perform(post("/api/v1/recording-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"5\",\"typeId\":\"42\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resourceId").value("99"));
    }

    @Test
    void patch_returns200() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setResourceType("recording-units");
        resource.setId("5");
        when(recordingUnitOpenApiService.patchRecordingUnit(eq("5"), any(), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(resource);

        mockMvc.perform(patch("/api/v1/recording-units/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceId").value("5"));
    }

    @Test
    void getById_withCounts_passesToService() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setResourceType("recording-units");
        resource.setId("5");
        when(recordingUnitOpenApiService.buildMobileDetail(
                "5", personDto, Set.of(10L), List.of("specimen"), "fr"))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/recording-units/5").param("counts", "specimen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceId").value("5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/recording-units/5",
            "/api/v1/recording-units/5/mobiliers",
            "/api/v1/recording-units/5/relations",
            "/api/v1/recording-units/5/documents"
    })
    void get_withoutAuth_returns401(String url) throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_withAcceptLanguage_passesResolvedLangToService() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setResourceType("recording-units");
        resource.setId("7");
        when(recordingUnitOpenApiService.buildMobileDetail(
                "7", personDto, Set.of(10L), null, "en"))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/recording-units/7")
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceId").value("7"));

        verify(recordingUnitOpenApiService).buildMobileDetail(
                "7", personDto, Set.of(10L), null, "en");
    }

    @Test
    void getById_withSeveralInstitutions_passesFullScopeToService() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setResourceType("recording-units");
        resource.setId("1");
        when(recordingUnitOpenApiService.buildMobileDetail(
                "x-id", personDto, Set.of(10L, 20L), null, "fr"))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/recording-units/x-id"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildMobileDetail(
                "x-id", personDto, Set.of(10L, 20L), null, "fr");
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
    void getFinds_notFound_returns404() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("missing"), eq(Set.of(10L)), isNull()))
                .thenThrow(new RecordingUnitNotFoundException("gone"));

        mockMvc.perform(get("/api/v1/recording-units/missing/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("gone"));
    }

    @Test
    void getFinds_badPagination_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/recording-units/5/mobiliers")
                        .param("offset", "1")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFinds_success_empty_returnsMeta() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(5L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("5"), eq(Set.of(10L)), isNull())).thenReturn(ru);

        PageImpl<SpecimenDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(5L), isNull(), isNull(), isNull(), eq("fr"), eq("creationTime:desc"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/recording-units/5/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.meta.limit").value(10))
                .andExpect(jsonPath("$.meta.offset").value(0));
    }

    @Test
    void getFinds_success_returnsMappedFinds() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(7L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("7"), eq(Set.of(10L)), isNull())).thenReturn(ru);

        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(99L);
        spec.setFullIdentifier("INST-UE-99");
        PageImpl<SpecimenDTO> page = new PageImpl<>(
                List.of(spec),
                PageRequest.of(0, 10),
                1L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(7L), isNull(), isNull(), isNull(), eq("fr"), eq("creationTime:desc"), any())).thenReturn(page);

        FindResource fr = new FindResource();
        fr.setResourceType("finds");
        fr.setId("99");
        fr.setFullIdentifier("INST-UE-99");
        when(findOpenApiMapper.toResource(same(spec))).thenReturn(fr);

        mockMvc.perform(get("/api/v1/recording-units/7/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].resourceId").value("99"))
                .andExpect(jsonPath("$.data[0].resourceType").value("finds"))
                .andExpect(jsonPath("$.data[0].fullIdentifier").value("INST-UE-99"));

        verify(findOpenApiMapper).toResource(same(spec));
    }

    @Test
    void getFinds_sortParam_passedToSpecimenQuery() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(3L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("3"), eq(Set.of(10L)), isNull())).thenReturn(ru);

        PageImpl<SpecimenDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(3L), isNull(), isNull(), isNull(), eq("fr"), eq("fullIdentifier:asc"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/recording-units/3/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10")
                        .param("sort", "fullIdentifier:asc"))
                .andExpect(status().isOk());

        verify(specimenService).findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(3L), isNull(), isNull(), isNull(), eq("fr"), eq("fullIdentifier:asc"), any(Pageable.class));
    }

    @Test
    void getFinds_passesAcceptLanguageToSpecimenQuery() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(1L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("1"), eq(Set.of(10L)), isNull())).thenReturn(ru);

        PageImpl<SpecimenDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(1L), isNull(), isNull(), isNull(), eq("de"), eq("creationTime:desc"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/recording-units/1/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "de-AT,de;q=0.9"))
                .andExpect(status().isOk());

        verify(specimenService).findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(1L), isNull(), isNull(), isNull(), eq("de"), eq("creationTime:desc"), any(Pageable.class));
    }

    @Test
    void getFinds_recordingUnitWithoutInstitution_returns400() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(8L);
        ru.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("8"), eq(Set.of(10L)), isNull())).thenReturn(ru);

        mockMvc.perform(get("/api/v1/recording-units/8/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFinds_withSeveralInstitutions_passesFullScopeToRecordingUnitResolution() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(2L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("k"), eq(Set.of(10L, 20L)), isNull()))
                .thenReturn(ru);

        PageImpl<SpecimenDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(2L), isNull(), isNull(), isNull(), eq("fr"), eq("creationTime:desc"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/recording-units/k/mobiliers")
                        .param("offset", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        verify(recordingUnitService).findAccessibleRecordingUnitByKey("k", Set.of(10L, 20L), null);
    }

    @Test
    void getRelations_returns200_withData() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        StratigraphicRelationshipDTO rel = new StratigraphicRelationshipDTO();
        when(recordingUnitOpenApiService.buildRecordingUnitRelations("5", Set.of(10L)))
                .thenReturn(new RecordingUnitRelationsData(List.of(rel), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/recording-units/5/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stratigraphicRelationships").isArray())
                .andExpect(jsonPath("$.data.stratigraphicRelationships.length()").value(1))
                .andExpect(jsonPath("$.data.parents").isArray())
                .andExpect(jsonPath("$.data.parents.length()").value(0))
                .andExpect(jsonPath("$.data.children").isArray())
                .andExpect(jsonPath("$.data.children.length()").value(0));

        verify(recordingUnitOpenApiService).buildRecordingUnitRelations("5", Set.of(10L));
    }

    @Test
    void getRelations_withSeveralInstitutions_passesFullScopeToService() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        when(recordingUnitOpenApiService.buildRecordingUnitRelations("ru-key", Set.of(10L, 20L)))
                .thenReturn(new RecordingUnitRelationsData(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/recording-units/ru-key/relations"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildRecordingUnitRelations("ru-key", Set.of(10L, 20L));
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
        when(recordingUnitOpenApiService.buildRecordingUnitRelations("5", Set.of(10L)))
                .thenReturn(new RecordingUnitRelationsData(List.of(), List.of(parent), List.of(child)));

        mockMvc.perform(get("/api/v1/recording-units/5/relations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parents[0].id").value(99))
                .andExpect(jsonPath("$.data.parents[0].fullIdentifier").value("INST-A-UE-P"))
                .andExpect(jsonPath("$.data.children[0].id").value(100))
                .andExpect(jsonPath("$.data.children[0].fullIdentifier").value("INST-A-UE-C"));
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

    @Test
    void getChildren_returns200_withEmptyList() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        when(recordingUnitOpenApiService.buildRecordingUnitChildren("5", Set.of(10L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recording-units/5/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(recordingUnitOpenApiService).buildRecordingUnitChildren("5", Set.of(10L));
    }

    @Test
    void getChildren_returns200_withChildrenInPayload() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource child = new RecordingUnitResource();
        child.setId("100");
        child.setFullIdentifier("INST-A-UE-C");
        child.setResourceType("recording-units");
        when(recordingUnitOpenApiService.buildRecordingUnitChildren("5", Set.of(10L)))
                .thenReturn(List.of(child));

        mockMvc.perform(get("/api/v1/recording-units/5/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resourceId").value("100"))
                .andExpect(jsonPath("$.data[0].fullIdentifier").value("INST-A-UE-C"));

        verify(recordingUnitOpenApiService).buildRecordingUnitChildren("5", Set.of(10L));
    }

    @Test
    void getChildren_returns200_withSeveralChildren() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitResource first = new RecordingUnitResource();
        first.setId("10");
        first.setResourceType("recording-units");
        RecordingUnitResource second = new RecordingUnitResource();
        second.setId("20");
        second.setResourceType("recording-units");
        when(recordingUnitOpenApiService.buildRecordingUnitChildren("1", Set.of(10L)))
                .thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/recording-units/1/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].resourceId").value("10"))
                .andExpect(jsonPath("$.data[1].resourceId").value("20"));

        verify(recordingUnitOpenApiService).buildRecordingUnitChildren("1", Set.of(10L));
    }

    @Test
    void getChildren_withSeveralInstitutions_passesFullScopeToService() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        when(recordingUnitOpenApiService.buildRecordingUnitChildren("ru-key", Set.of(10L, 20L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recording-units/ru-key/children"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildRecordingUnitChildren("ru-key", Set.of(10L, 20L));
    }

    @Test
    void getChildren_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/recording-units/5/children"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void getChildren_whenRecordingUnitNotFound_returns404() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(recordingUnitOpenApiService.buildRecordingUnitChildren(any(), any()))
                .thenThrow(new RecordingUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/recording-units/404-key/children"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing"))
                .andExpect(jsonPath("$.path").value("/api/v1/recording-units/404-key/children"));
    }

    @Test
    void addExistingChild_returns200_withRelations() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitSummaryDTO child = new RecordingUnitSummaryDTO();
        child.setId(99L);
        RecordingUnitRelationsData relations = new RecordingUnitRelationsData(List.of(), List.of(), List.of(child));
        when(recordingUnitOpenApiService.addExistingChild("5", 99L, personDto, Set.of(10L)))
                .thenReturn(relations);

        mockMvc.perform(post("/api/v1/recording-units/5/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedRecordingUnitId\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children", hasSize(1)))
                .andExpect(jsonPath("$.data.children[0].id").value(99));

        verify(recordingUnitOpenApiService).addExistingChild("5", 99L, personDto, Set.of(10L));
    }

    @Test
    void addExistingChild_withoutRelatedId_returns400() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(post("/api/v1/recording-units/5/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void removeExistingParent_returns200_withRelations() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitSummaryDTO parent = new RecordingUnitSummaryDTO();
        parent.setId(88L);
        RecordingUnitRelationsData relations = new RecordingUnitRelationsData(List.of(), List.of(parent), List.of());
        when(recordingUnitOpenApiService.removeExistingParent("5", 88L, personDto, Set.of(10L)))
                .thenReturn(relations);

        mockMvc.perform(delete("/api/v1/recording-units/5/parents/88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parents", hasSize(1)))
                .andExpect(jsonPath("$.data.parents[0].id").value(88));

        verify(recordingUnitOpenApiService).removeExistingParent("5", 88L, personDto, Set.of(10L));
    }

    @Test
    void getRecordingUnitDocuments_notFound_returns404() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("404-key"), eq(Set.of(10L)), isNull()))
                .thenThrow(new RecordingUnitNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/recording-units/404-key/documents"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing"));
    }

    @Test
    void getRecordingUnitDocuments_success_returnsDocuments() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(42L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("7"), eq(Set.of(10L)), isNull())).thenReturn(ruDto);

        Document doc = mock(Document.class);
        when(documentService.findForRecordingUnit(ruDto)).thenReturn(List.of(doc));

        DocumentResource dr = new DocumentResource();
        dr.setResourceType("documents");
        dr.setId("100");
        dr.setTitle("Photo de coupe");
        when(projectDocumentOpenApiMapper.toResource(same(doc))).thenReturn(dr);

        mockMvc.perform(get("/api/v1/recording-units/7/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].resourceId").value("100"))
                .andExpect(jsonPath("$.data[0].resourceType").value("documents"))
                .andExpect(jsonPath("$.data[0].title").value("Photo de coupe"));

        verify(documentService).findForRecordingUnit(ruDto);
        verify(projectDocumentOpenApiMapper).toResource(same(doc));
    }

    @Test
    void getRecordingUnitDocuments_success_emptyList() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(2L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("2"), eq(Set.of(10L)), isNull())).thenReturn(ruDto);
        when(documentService.findForRecordingUnit(ruDto)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recording-units/2/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getRecordingUnitDocuments_withSeveralInstitutions_passesFullScopeToService() throws Exception {
        InstitutionDTO inst20 = new InstitutionDTO();
        inst20.setId(20L);
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto))
                .thenReturn(Set.of(institutionDto, inst20));

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(1L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("ru-key"), eq(Set.of(10L, 20L)), isNull()))
                .thenReturn(ruDto);
        when(documentService.findForRecordingUnit(ruDto)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recording-units/ru-key/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(recordingUnitService).findAccessibleRecordingUnitByKey("ru-key", Set.of(10L, 20L), null);
    }

    @Test
    void getRecordingUnitDocuments_success_passesFullIdentifierPathSegment() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        String fullId = "INST-PROJ-UE42";
        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(500L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq(fullId), eq(Set.of(10L)), isNull()))
                .thenReturn(ruDto);
        when(documentService.findForRecordingUnit(ruDto)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recording-units/" + fullId + "/documents"))
                .andExpect(status().isOk());

        verify(recordingUnitService).findAccessibleRecordingUnitByKey(fullId, Set.of(10L), null);
    }

    @Test
    void getRecordingUnitDocuments_success_returnsMultipleDocumentsSortedByResourceId() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ruDto = new RecordingUnitDTO();
        ruDto.setId(1L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("1"), eq(Set.of(10L)), isNull())).thenReturn(ruDto);

        Document doc30 = mock(Document.class);
        when(doc30.getId()).thenReturn(30L);
        Document doc7 = mock(Document.class);
        when(doc7.getId()).thenReturn(7L);
        when(documentService.findForRecordingUnit(ruDto)).thenReturn(List.of(doc30, doc7));

        DocumentResource r30 = new DocumentResource();
        r30.setResourceType("documents");
        r30.setId("30");
        r30.setTitle("second");
        DocumentResource r7 = new DocumentResource();
        r7.setResourceType("documents");
        r7.setId("7");
        r7.setTitle("first");
        when(projectDocumentOpenApiMapper.toResource(same(doc30))).thenReturn(r30);
        when(projectDocumentOpenApiMapper.toResource(same(doc7))).thenReturn(r7);

        mockMvc.perform(get("/api/v1/recording-units/1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].resourceId").value("7"))
                .andExpect(jsonPath("$.data[0].title").value("first"))
                .andExpect(jsonPath("$.data[1].resourceId").value("30"))
                .andExpect(jsonPath("$.data[1].title").value("second"));
    }

    @Test
    void getRecordingUnitCreateForm_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "10")
                        .param("recordingUnitTypeConceptId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRecordingUnitCreateForm_orgForbidden_returns403() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "999")
                        .param("recordingUnitTypeConceptId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRecordingUnitCreateForm_success_returnsJson() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setId("3");
        RecordingUnitCreateFormData payload = new RecordingUnitCreateFormData(type, null, Map.of());
        when(recordingUnitOpenApiService.buildRecordingUnitCreateForm(10L, 3L, personDto, "fr"))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "10")
                        .param("recordingUnitTypeConceptId", "3")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordingUnitType.resourceId").value("3"));

        verify(recordingUnitOpenApiService).buildRecordingUnitCreateForm(10L, 3L, personDto, "fr");
    }

    @Test
    void getRecordingUnitCreateForm_whenServiceNotFound_returns404() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(recordingUnitOpenApiService.buildRecordingUnitCreateForm(anyLong(), anyLong(), eq(personDto), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording unit type not found"));

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "10")
                        .param("recordingUnitTypeConceptId", "99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRecordingUnitCreateForm_passesAcceptLanguageToService() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setId("1");
        when(recordingUnitOpenApiService.buildRecordingUnitCreateForm(10L, 1L, personDto, "en"))
                .thenReturn(new RecordingUnitCreateFormData(type, null, Map.of()));

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "10")
                        .param("recordingUnitTypeConceptId", "1")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildRecordingUnitCreateForm(10L, 1L, personDto, "en");
    }

    @Test
    void getRecordingUnitCreateForm_withoutAcceptLanguage_passesFrenchDefault() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setId("2");
        when(recordingUnitOpenApiService.buildRecordingUnitCreateForm(10L, 2L, personDto, "fr"))
                .thenReturn(new RecordingUnitCreateFormData(type, null, Map.of()));

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "10")
                        .param("recordingUnitTypeConceptId", "2"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildRecordingUnitCreateForm(10L, 2L, personDto, "fr");
    }

    @Test
    void getRecordingUnitCreateForm_success_returnsFormBundleAndFieldsInJson() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setId("8");
        FormResource bundle = new FormResource(50L, "Mon formulaire", "D", "{\"layout\":[]}");
        FieldResource field = new FieldResource("12", "fields", "Libellé", "TEXT", null, false, null);
        Map<String, FieldResource> fields = Map.of("12", field);
        RecordingUnitCreateFormData payload = new RecordingUnitCreateFormData(type, bundle, fields);
        when(recordingUnitOpenApiService.buildRecordingUnitCreateForm(10L, 8L, personDto, "fr"))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/recording-units/creation-form")
                        .param("projectId", "10")
                        .param("recordingUnitTypeConceptId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.form.resourceId").value(50))
                .andExpect(jsonPath("$.data.form.name").value("Mon formulaire"))
                .andExpect(jsonPath("$.data.fields['12'].id").value("12"))
                .andExpect(jsonPath("$.data.fields['12'].answerType").value("TEXT"));
    }

    @Test
    void deleteRecordingUnit_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(delete("/api/v1/recording-units/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRecordingUnit_forbidden_returns403() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(5L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(5L, Set.of(10L)))
                .thenReturn(ru);
        when(permissionService.hasWritePermission(any(), any())).thenReturn(false);

        mockMvc.perform(delete("/api/v1/recording-units/5"))
                .andExpect(status().isForbidden());

        verify(recordingUnitService, never()).deleteRecordingUnitById(anyLong());
    }

    @Test
    void deleteRecordingUnit_success_returns204() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(9L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(9L, Set.of(10L)))
                .thenReturn(ru);
        when(permissionService.hasWritePermission(any(), any())).thenReturn(true);
        doNothing().when(recordingUnitService).deleteRecordingUnitById(9L);

        mockMvc.perform(delete("/api/v1/recording-units/9"))
                .andExpect(status().isNoContent());

        verify(recordingUnitService).deleteRecordingUnitById(9L);
    }

    @Test
    void deleteRecordingUnit_whenDomainConflict_returns409() throws Exception {
        when(personMapper.convert(person)).thenReturn(personDto);
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(3L);
        ru.setCreatedByInstitution(institutionDto);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(3L, Set.of(10L)))
                .thenReturn(ru);
        when(permissionService.hasWritePermission(any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("mobiliers")).when(recordingUnitService).deleteRecordingUnitById(3L);

        mockMvc.perform(delete("/api/v1/recording-units/3"))
                .andExpect(status().isConflict());
    }
}
