package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
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
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Complément de couverture pour {@link ProjectApiService} : branches non couvertes par
 * ProjectApiServiceMutationTest / ProjectApiServiceRecordingUnitFindsTest / ProjectApiServiceRecordingUnitDocumentsTest
 * / ProjectApiServiceOrganizationPageTest (validation createProject/patchProject/deleteProject, permissions,
 * primaryAcceptLanguage).
 */
@ExtendWith(MockitoExtension.class)
class ProjectApiServiceTest {

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
                recordingUnitOpenApiService,
                phaseService);

        personDto = new PersonDTO();
        personDto.setId(1L);
        caller = new ProjectApiCaller(personDto, SCOPE, List.of());

        institution = new InstitutionDTO();
        institution.setId(10L);
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setCreatedByInstitution(institution);
        return au;
    }

    private ProjectCreateRequest validCreateRequest() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setOrganizationId("10");
        request.setName("Projet");
        request.setIdentifier("PRJ");
        request.setTypeId("42");
        return request;
    }

    // ---- requireAccessibleProject ---------------------------------------

    @Test
    void requireAccessibleProject_whenCannotView_throws404() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(caller.person(), institution, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireAccessibleProject(caller, "7"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- createProject validation -----------------------------------------

    @Test
    void createProject_organizationNotFound_throws404() {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Organisation introuvable");
    }

    @Test
    void createProject_blankName_throws400() {
        ProjectCreateRequest request = validCreateRequest();
        request.setName("   ");
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("name est obligatoire");
    }

    @Test
    void createProject_blankIdentifier_throws400() {
        ProjectCreateRequest request = validCreateRequest();
        request.setIdentifier("   ");
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("identifier est obligatoire");
    }

    @Test
    void createProject_missingTypeId_throws400() {
        ProjectCreateRequest request = validCreateRequest();
        request.setTypeId(null);
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("typeConceptId est obligatoire");
    }

    @Test
    void createProject_unknownTypeConcept_throws404() {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(conceptService.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createProject_nullIdentifierOnSave_throws400() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(conceptService.findById(42L)).thenReturn(Optional.of(new Concept()));
        when(conceptMapper.convert(any())).thenReturn(new ConceptDTO());
        when(actionUnitService.save(any(), any(), any()))
                .thenThrow(new NullActionUnitIdentifierException("identifier is null"));

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createProject_failedSave_throws400() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(conceptService.findById(42L)).thenReturn(Optional.of(new Concept()));
        when(conceptMapper.convert(any())).thenReturn(new ConceptDTO());
        when(actionUnitService.save(any(), any(), any()))
                .thenThrow(new FailedActionUnitSaveException("save failed"));

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- patchProject -------------------------------------------------------

    @Test
    void patchProject_missingInstitution_throws400() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        au.setCreatedByInstitution(null);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);

        var patch = new ProjectPatchRequest();
        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Projet sans organisation de rattachement");
    }

    @Test
    void patchProject_typeIdProvided_conceptNotFound_throws404() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(conceptService.findById(99L)).thenReturn(Optional.empty());

        var patch = new ProjectPatchRequest();
        patch.setTypeId("99");

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void patchProject_typeIdProvided_success_updatesTypeAndDates() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        AccessibleProjectForApi resultRow = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row, resultRow);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);

        Concept concept = new Concept();
        concept.setId(99L);
        when(conceptService.findById(99L)).thenReturn(Optional.of(concept));
        ConceptDTO newType = new ConceptDTO();
        newType.setId(99L);
        when(conceptMapper.convert(concept)).thenReturn(newType);
        when(actionUnitService.save(any(), same(au), eq(newType))).thenReturn(au);

        var patch = new ProjectPatchRequest();
        patch.setTypeId("99");
        OffsetDateTime begin = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2021-01-01T00:00:00Z");
        patch.setBeginDate(begin);
        patch.setEndDate(end);

        AccessibleProjectForApi result = service.patchProject(caller, "7", patch, "fr");

        assertThat(result).isSameAs(resultRow);
        assertThat(au.getType()).isSameAs(newType);
        assertThat(au.getBeginDate()).isEqualTo(begin);
        assertThat(au.getEndDate()).isEqualTo(end);
        verify(actionUnitService).save(any(), same(au), eq(newType));
    }

    @Test
    void patchProject_duplicateIdentifier_throws409() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(actionUnitService.save(any(), any(), any()))
                .thenThrow(new ActionUnitAlreadyExistsException("identifier", "already exists"));

        var patch = new ProjectPatchRequest();
        patch.setName("Renamed");

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void patchProject_nullIdentifierOnSave_throws400() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(true);
        when(actionUnitService.save(any(), any(), any()))
                .thenThrow(new NullActionUnitIdentifierException("identifier is null"));

        var patch = new ProjectPatchRequest();
        patch.setName("Renamed");

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- deleteProject ---------------------------------------------------

    @Test
    void deleteProject_missingId_throws400() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(null);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Projet sans identifiant");
    }

    @Test
    void deleteProject_missingInstitution_throws400() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        au.setCreatedByInstitution(null);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Projet sans organisation de rattachement");
    }

    @Test
    void deleteProject_notManager_throws403() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))).thenReturn(false);

        assertThatThrownBy(() -> service.deleteProject(caller, "7", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(actionUnitService, never()).deleteProjectWhenEmpty(anyLong());
    }

    // ---- deleteRecordingUnit ----------------------------------------------

    @Test
    void deleteRecordingUnit_missingInstitution_throws400() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(55L);
        dto.setCreatedByInstitution(null);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(55L, SCOPE)).thenReturn(dto);

        assertThatThrownBy(() -> service.deleteRecordingUnit(caller, 55L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Unité d'enregistrement sans organisation");
    }

    // ---- requireRecordingUnitViewPermission (via listDocumentsForAccessibleRecordingUnit) ----

    @Test
    void listDocumentsForAccessibleRecordingUnit_whenCannotView_throws404() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(1L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("1"), eq(SCOPE), isNull())).thenReturn(ru);
        when(profilePermissionService.canViewRecordingUnit(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.listDocumentsForAccessibleRecordingUnit(caller, "1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(documentService);
    }

    // ---- validatePagedListRequest (offset/limit bounds) --------------------

    @Test
    void validatePagedListRequest_negativeOffset_throws400() {
        assertThatThrownBy(() -> service.validatePagedListRequest(-1, 10))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Paramètres de pagination invalides");
                });
    }

    @Test
    void validatePagedListRequest_nonPositiveLimit_throws400() {
        assertThatThrownBy(() -> service.validatePagedListRequest(0, 0))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Paramètres de pagination invalides");
        assertThatThrownBy(() -> service.validatePagedListRequest(0, -5))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Paramètres de pagination invalides");
    }

    @Test
    void validatePagedListRequest_limitAboveMax_throws400() {
        assertThatThrownBy(() -> service.validatePagedListRequest(0, ProjectApiService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Paramètres de pagination invalides");
                });
    }

    // ---- patchProject mainLocation / spatialContext ------------------------

    private void stubPatchProjectAccess(ActionUnitDTO au) {
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.canViewProject(any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasOrganizationPermission(any(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)))
                .thenReturn(true);
    }

    @Test
    void patchProject_withMainLocationAndSpatialContext_updatesPlaces() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        ConceptDTO type = new ConceptDTO();
        au.setType(type);
        stubPatchProjectAccess(au);
        when(actionUnitService.save(any(), same(au), eq(type))).thenReturn(au);

        SpatialUnitDTO mainPlace = new SpatialUnitDTO();
        mainPlace.setId(100L);
        mainPlace.setName("Commune");
        when(spatialUnitService.findById(100L)).thenReturn(mainPlace);

        SpatialUnitDTO contextPlace = new SpatialUnitDTO();
        contextPlace.setId(200L);
        contextPlace.setName("Parcelle");
        when(spatialUnitService.findById(200L)).thenReturn(contextPlace);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.setMainLocationId("100");
        patch.setSpatialContextSpatialUnitIds(java.util.Arrays.asList("200", " ", null));

        AccessibleProjectForApi result = service.patchProject(caller, "7", patch, "fr");

        assertThat(result.actionUnit()).isSameAs(au);
        assertThat(au.getMainLocation()).isNotNull();
        assertThat(au.getMainLocation().getId()).isEqualTo(100L);
        assertThat(au.getSpatialContext()).extracting(su -> su.getId()).containsExactly(200L);
        verify(spatialUnitService).findById(100L);
        verify(spatialUnitService).findById(200L);
        verify(spatialUnitService, times(2)).findById(anyLong());
    }

    @Test
    void patchProject_mainLocationNotFound_throws404() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        stubPatchProjectAccess(au);
        when(spatialUnitService.findById(404L)).thenThrow(new SpatialUnitNotFoundException("missing"));

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.setMainLocationId("404");

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).isEqualTo("Lieu introuvable: 404");
                });
        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void patchProject_blankMainLocationId_clearsMainLocation() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        ConceptDTO type = new ConceptDTO();
        au.setType(type);
        SpatialUnitDTO previous = new SpatialUnitDTO();
        previous.setId(1L);
        au.setMainLocation(new SpatialUnitSummaryDTO(previous));
        stubPatchProjectAccess(au);
        when(actionUnitService.save(any(), same(au), eq(type))).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.setMainLocationId("  ");

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getMainLocation()).isNull();
        verify(spatialUnitService, never()).findById(anyLong());
    }

    @Test
    void patchProject_spatialContextNotFound_throws404() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        stubPatchProjectAccess(au);
        when(spatialUnitService.findById(501L)).thenThrow(new RuntimeException("gone"));

        ProjectPatchRequest patch = new ProjectPatchRequest();
        List<String> placeIds = List.of("501");
        patch.setSpatialContextSpatialUnitIds(placeIds);

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).isEqualTo("Lieu introuvable: 501");
                });
        verify(actionUnitService, never()).save(any(), any(), any());
    }

    // ---- primaryAcceptLanguage (static helper) -----------------------------

    @Test
    void primaryAcceptLanguage_null_returnsFrenchDefault() {
        assertThat(ProjectApiService.primaryAcceptLanguage(null)).isEqualTo("fr");
    }

    @Test
    void primaryAcceptLanguage_blank_returnsFrenchDefault() {
        assertThat(ProjectApiService.primaryAcceptLanguage("   ")).isEqualTo("fr");
    }

    @Test
    void primaryAcceptLanguage_withQualityValueAndRegion_stripsQualityAndRegion() {
        assertThat(ProjectApiService.primaryAcceptLanguage("de-DE;q=0.7,fr;q=0.3")).isEqualTo("de");
    }

    @Test
    void primaryAcceptLanguage_withQualityValueNoRegion_stripsQuality() {
        assertThat(ProjectApiService.primaryAcceptLanguage("en;q=0.8")).isEqualTo("en");
    }

    @Test
    void primaryAcceptLanguage_simpleLanguageTag_returnsLowercased() {
        assertThat(ProjectApiService.primaryAcceptLanguage("IT")).isEqualTo("it");
    }
}
