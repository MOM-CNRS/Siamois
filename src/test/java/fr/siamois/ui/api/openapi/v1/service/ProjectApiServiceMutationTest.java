package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.ProfilePermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
                recordingUnitOpenApiService);

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
        request.setOrganizationId(99L);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createProject_notManager_throws403() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(false);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void createProject_success_savesAndReturnsAccessibleProject() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(true);

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
        verify(recordingUnitOpenApiService).applySystemProjectFormFieldAnswers(any(), any(), same(personDto), eq("fr"));
    }

    @Test
    void createProject_duplicateIdentifier_throws409() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(true);
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
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(false);

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
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(true);
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
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(true);

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
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(true);

        service.deleteProject(caller, "7", "fr");

        verify(actionUnitService).deleteProjectWhenEmpty(7L);
    }

    @Test
    void deleteProject_domainConflict_throws409() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS))).thenReturn(true);
        doThrow(new IllegalStateException("blocked")).when(actionUnitService).deleteProjectWhenEmpty(7L);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private ProjectCreateRequest validCreateRequest() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setOrganizationId(10L);
        request.setName("Projet");
        request.setIdentifier("PRJ");
        request.setTypeConceptId(42L);
        return request;
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setCreatedByInstitution(institution);
        return au;
    }
}
