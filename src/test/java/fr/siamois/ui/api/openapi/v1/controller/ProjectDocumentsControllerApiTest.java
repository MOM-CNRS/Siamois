package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.project.ProjectDocumentsControllerApi;
import fr.siamois.ui.api.openapi.v1.mapper.DocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.service.DocumentListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A project's documents: the web client's paged list, and the historical unpaged one the mobile app reads. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectDocumentsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private DocumentWriteOpenApiService documentWriteOpenApiService;
    @Mock
    private DocumentOpenApiMapper documentOpenApiMapper;
    @Mock
    private DocumentListProjectionService documentListProjectionService;
    @Mock
    private ResourceBookmarkService resourceBookmarkService;
    @Mock
    private FieldQueryService fieldQueryService;

    private MockMvc mockMvc;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectDocumentsControllerApi(projectApiService,
                        documentWriteOpenApiService, documentOpenApiMapper, documentListProjectionService,
                        resourceBookmarkService, fieldQueryService))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
        when(projectApiService.requireCaller()).thenReturn(caller);
    }

    @Test
    void withoutLimit_returnsEveryDocumentUnpaged() throws Exception {
        DocumentResource one = new DocumentResource();
        one.setId("1");
        when(projectApiService.listDocumentsForAccessibleProject(caller, "7")).thenReturn(List.of(one));

        mockMvc.perform(get("/api/v1/projects/7/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verifyNoInteractions(documentListProjectionService);
    }

    @Test
    void withLimit_returnsAPagedListWithPermissionsAndTotal() throws Exception {
        DocumentDTO document = new DocumentDTO();
        document.setId(3L);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(7L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        project.setCreatedByInstitution(institution);
        document.setActionUnit(project);
        when(fieldQueryService.parse(eq(Document.class), any(), any(), any())).thenReturn(FieldQuery.NONE);
        when(projectApiService.pageDocumentsForProject(caller, "7", 0, 10, "identifier:asc", null, FieldQuery.NONE))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 10), 1));
        when(projectApiService.canEditDocumentsForProject(eq(caller), eq("7"), any())).thenReturn(true);
        when(projectApiService.canValidateForProject(eq(caller), eq("7"), any())).thenReturn(false);
        when(documentListProjectionService.build(any(), isNull(), any()))
                .thenReturn(DocumentListProjectionService.DocumentListProjection.empty());
        DocumentResource resource = new DocumentResource();
        resource.setId("3");
        when(documentOpenApiMapper.toResource(eq(document), any(), any())).thenReturn(resource);

        mockMvc.perform(get("/api/v1/projects/7/documents").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data[0].id").value("3"))
                .andExpect(jsonPath("$.data[0]._permissions.canEdit").value(true))
                .andExpect(jsonPath("$.meta.total").value(1));

        verify(projectApiService).validatePagedListRequest(0, 10);
    }

    @Test
    void withLimitAndAFieldsProjection_putsTheAnswersOnEachRow() throws Exception {
        DocumentDTO document = new DocumentDTO();
        document.setId(3L);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(7L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        project.setCreatedByInstitution(institution);
        document.setActionUnit(project);
        when(fieldQueryService.parse(eq(Document.class), any(), any(), any())).thenReturn(FieldQuery.NONE);
        when(projectApiService.pageDocumentsForProject(any(), any(), eq(0), eq(10), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 10), 1));
        when(documentListProjectionService.build(any(), eq("all"), any()))
                .thenReturn(new DocumentListProjectionService.DocumentListProjection(
                        java.util.Map.of(), java.util.Map.of(3L, java.util.Map.of("-708", "Plan"))));
        when(documentOpenApiMapper.toResource(eq(document), any(), any())).thenReturn(new DocumentResource());

        mockMvc.perform(get("/api/v1/projects/7/documents").param("limit", "10").param("fields", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].answers['-708']").value("Plan"));
    }
}
