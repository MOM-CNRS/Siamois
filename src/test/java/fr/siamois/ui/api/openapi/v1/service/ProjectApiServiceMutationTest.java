package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectApiServiceMutationTest {

    private static final Set<Long> SCOPE = Set.of(10L);

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
    private PersonMapper personMapper;
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

    private ProjectApiService service;
    private ProjectApiCaller caller;
    private PersonDTO personDto;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        lenient().when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        service = new ProjectApiService(
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
                recordingUnitOpenApiService, phaseService);

        personDto = new PersonDTO();
        personDto.setId(1L);
        caller = new ProjectApiCaller(personDto, SCOPE, List.of());

        institution = new InstitutionDTO();
        institution.setId(10L);
    }

    @Test
    void createProject_missingOrganizationId_throws400() {
        ProjectCreateRequest request = new ProjectCreateRequest();

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("organizationId est obligatoire");
    }

    @Test
    void createProject_organizationOutOfScope_throws403() {
        ProjectCreateRequest request = validCreateRequest();
        request.setOrganizationId("99");

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createProject_notManager_throws403() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(false);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void createProject_success_savesAndReturnsAccessibleProject() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        Concept typeConcept = new Concept();
        typeConcept.setId(42L);
        when(conceptService.findById(42L)).thenReturn(Optional.of(typeConcept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(42L);
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);

        ActionUnitDTO saved = new ActionUnitDTO();
        saved.setId(77L);
        when(actionUnitService.save(any(), any(), eq(typeDto))).thenReturn(saved);
        AccessibleProjectForApi row = new AccessibleProjectForApi(saved, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("77", SCOPE)).thenReturn(row);

        AccessibleProjectForApi result = service.createProject(caller, request, "fr");

        assertThat(result).isSameAs(row);
    }

    @Test
    void createProject_duplicateIdentifier_throws409() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(conceptService.findById(42L)).thenReturn(Optional.of(new Concept()));
        when(conceptMapper.convert(any())).thenReturn(new ConceptDTO());
        when(actionUnitService.save(any(), any(), any()))
                .thenThrow(new ActionUnitAlreadyExistsException("identifier", "exists"));

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void patchProject_writeForbidden_throws403() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(false);

        var patchRequest = new ProjectPatchRequest();
        assertThatThrownBy(() -> service.patchProject(caller, "7", patchRequest, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void patchProject_success_updatesProject() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.setName("Updated");

        AccessibleProjectForApi result = service.patchProject(caller, "7", patch, "fr");

        assertThat(result).isSameAs(row);
        assertThat(au.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteProject_withRecordingUnits_throws409() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 2L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deleteProject_success_deletesWhenEmpty() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        service.deleteProject(caller, "7", "fr");

        verify(actionUnitService).deleteProjectWhenEmpty(7L);
    }

    @Test
    void deleteProject_domainConflict_throws409() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        doThrow(new IllegalStateException("blocked")).when(actionUnitService).deleteProjectWhenEmpty(7L);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void listPhasesForAccessibleProject_mapsPhaseServiceResults() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);

        PhaseDTO withTitle = new PhaseDTO();
        withTitle.setId(1L);
        withTitle.setIdentifier("P1");
        withTitle.setTitle("Phase titre");

        PhaseDTO blankTitle = new PhaseDTO();
        blankTitle.setId(2L);
        blankTitle.setIdentifier("P2");
        blankTitle.setTitle("  ");

        PhaseDTO nullId = new PhaseDTO();
        nullId.setIdentifier("P3");
        nullId.setTitle("Sans id");

        when(phaseService.findAllByActionUnitId(7L)).thenReturn(List.of(withTitle, blankTitle, nullId));

        List<PhaseResource> result = service.listPhasesForAccessibleProject(caller, "7");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo("1");
        assertThat(result.get(0).getLabel()).isEqualTo("Phase titre");
        assertThat(result.get(1).getLabel()).isEqualTo("P2");
        assertThat(result.get(2).getId()).isNull();
    }

    @Test
    void deleteRecordingUnit_withoutInstitution_throws400() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(5L);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(5L, SCOPE)).thenReturn(dto);

        assertThatThrownBy(() -> service.deleteRecordingUnit(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deleteRecordingUnit_withoutWritePermission_throws403() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(5L);
        dto.setCreatedByInstitution(institution);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(5L, SCOPE)).thenReturn(dto);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), same(dto))).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRecordingUnit(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deleteRecordingUnit_domainIllegalState_throws409() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(5L);
        dto.setCreatedByInstitution(institution);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(5L, SCOPE)).thenReturn(dto);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), same(dto))).thenReturn(true);
        doThrow(new IllegalStateException("has children")).when(recordingUnitService).deleteRecordingUnitById(5L);

        assertThatThrownBy(() -> service.deleteRecordingUnit(caller, 5L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void validatePagedListRequest_invalidOffsetOrLimit_throws400() {
        assertThatThrownBy(() -> service.validatePagedListRequest(-1, 10))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.validatePagedListRequest(0, 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.validatePagedListRequest(5, 10))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("offset doit être un multiple de limit");
    }

    @Test
    void primaryAcceptLanguage_parsesQualityAndRegion() {
        assertThat(ProjectApiService.primaryAcceptLanguage(null)).isEqualTo("fr");
        assertThat(ProjectApiService.primaryAcceptLanguage("en-US,fr;q=0.8")).isEqualTo("en");
        assertThat(ProjectApiService.primaryAcceptLanguage("fr;q=0.9")).isEqualTo("fr");
    }

    @Test
    void requireAccessibleProject_whenCannotView_throws404() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.requireAccessibleProject(caller, "7"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void pageAccessibleProjects_forwardsOrgSearchSortAndPage() {
        Page<AccessibleProjectForApi> expected = new PageImpl<>(List.of());
        when(actionUnitService.findAccessibleProjects(eq(1L), eq(SCOPE), eq(10L), eq("fouille"), any(Pageable.class)))
                .thenReturn(expected);

        Page<AccessibleProjectForApi> result = service.pageAccessibleProjects(
                caller, 10L, "fouille", 20, 10, List.of("name:desc"));

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(actionUnitService).findAccessibleProjects(eq(1L), eq(SCOPE), eq(10L), eq("fouille"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pageRecordingUnitsForProject_delegatesWithDefaultSort() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        Page<RecordingUnitDTO> expected = new PageImpl<>(List.of());
        when(recordingUnitService.findByActionUnitId(eq(7L), eq(10), eq(0), any(Sort.class))).thenReturn(expected);

        Page<RecordingUnitDTO> result = service.pageRecordingUnitsForProject(caller, "7", 0, 10, null);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(recordingUnitService).findByActionUnitId(eq(7L), eq(10), eq(0), sortCaptor.capture());
        assertThat(sortCaptor.getValue().getOrderFor("creationTime").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listDocumentsForAccessibleProject_mapsAndSortsById() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);

        Document doc2 = new Document();
        doc2.setId(2L);
        Document doc1 = new Document();
        doc1.setId(1L);
        when(documentService.findForActionUnit(au)).thenReturn(List.of(doc2, doc1));
        when(projectDocumentOpenApiMapper.toResource(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            DocumentResource resource = new DocumentResource();
            resource.setId(String.valueOf(doc.getId()));
            return resource;
        });

        List<DocumentResource> result = service.listDocumentsForAccessibleProject(caller, "7");

        assertThat(result).extracting(DocumentResource::getId).containsExactly("1", "2");
    }

    @Test
    void createProject_blankNameOrIdentifierOrMissingType_throws400() {
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)))
                .thenReturn(true);

        ProjectCreateRequest blankName = validCreateRequest();
        blankName.setName("  ");
        assertThatThrownBy(() -> service.createProject(caller, blankName, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("name est obligatoire");

        ProjectCreateRequest blankId = validCreateRequest();
        blankId.setIdentifier("");
        assertThatThrownBy(() -> service.createProject(caller, blankId, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("identifier est obligatoire");

        ProjectCreateRequest missingType = validCreateRequest();
        missingType.setTypeId(null);
        assertThatThrownBy(() -> service.createProject(caller, missingType, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("typeConceptId est obligatoire");

        verify(conceptService, never()).findById(anyLong());
    }

    @Test
    void deleteProject_withChildProjects_throws409() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 2L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(actionUnitService, never()).deleteProjectWhenEmpty(anyLong());
    }

    @Test
    void deleteRecordingUnit_success_delegatesToService() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(5L);
        dto.setCreatedByInstitution(institution);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(5L, SCOPE)).thenReturn(dto);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), same(dto))).thenReturn(true);

        service.deleteRecordingUnit(caller, 5L, "fr");

        verify(recordingUnitService).deleteRecordingUnitById(5L);
    }

    private ProjectCreateRequest validCreateRequest() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setOrganizationId("10");
        request.setName("Projet");
        request.setIdentifier("PRJ");
        request.setTypeId("42");
        return request;
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setCreatedByInstitution(institution);
        return au;
    }
}
