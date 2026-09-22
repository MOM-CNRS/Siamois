package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
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
import java.util.Map;
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
    @Mock
    private BookmarkService bookmarkService;
    @Mock
    private HistoryAuditService historyAuditService;

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
                recordingUnitOpenApiService, phaseService,
                bookmarkService, historyAuditService);

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
        when(profilePermissionService.hasActionUnitCreatePermission(any())).thenReturn(false);

        assertThatThrownBy(() -> service.createProject(caller, request, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void createProject_success_savesAndReturnsAccessibleProject() throws Exception {
        ProjectCreateRequest request = validCreateRequest();
        when(institutionService.findById(10L)).thenReturn(institution);
        when(profilePermissionService.hasActionUnitCreatePermission(any())).thenReturn(true);

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
        when(profilePermissionService.hasActionUnitCreatePermission(any())).thenReturn(true);
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
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(false);

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
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.setName("Updated");

        AccessibleProjectForApi result = service.patchProject(caller, "7", patch, "fr");

        assertThat(result).isSameAs(row);
        assertThat(au.getName()).isEqualTo("Updated");
    }

    // ------------------------------------------------------------------
    // patchProject — answers (plan §3 phase 4b)
    // ------------------------------------------------------------------

    @Test
    void patchProject_answers_writesAScalarTextField() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-109", new AnswerInput("OA-2024-01", null)); // OA_CODE_FIELD

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getOaCode()).isEqualTo("OA-2024-01");
    }

    @Test
    void patchProject_answers_appliedAfterFlatFieldsAndCanOverrideThem() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.setName("FromFlatField");
        patch.getAnswers().put("-102", new AnswerInput("FromAnswers", null)); // NAME_FIELD

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getName()).isEqualTo("FromAnswers");
    }

    @Test
    void patchProject_answers_writesADecimalField() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-128", new AnswerInput(0.42, null)); // OPENING_RATE_FIELD

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getOpeningRate()).isEqualTo(0.42);
    }

    @Test
    void patchProject_answers_clearsAScalarFieldOnNullValue() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        au.setOaCode("OLD");
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-109", new AnswerInput(null, null));

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getOaCode()).isNull();
    }

    @Test
    void patchProject_answers_resolvesAndWritesASelectOneConcept() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        Concept statusConcept = new Concept();
        statusConcept.setId(12L);
        ConceptDTO statusDto = new ConceptDTO();
        statusDto.setId(12L);
        when(conceptService.findById(12L)).thenReturn(Optional.of(statusConcept));
        when(conceptMapper.convert(statusConcept)).thenReturn(statusDto);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-118", new AnswerInput(12, null)); // STATUS_FIELD

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getStatus()).isEqualTo(statusDto);
    }

    @Test
    void patchProject_answers_unknownStatusConcept_throws404() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(conceptService.findById(999L)).thenReturn(Optional.empty());

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-118", new AnswerInput(999, null));

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void patchProject_answers_resolvesAndWritesASelectManyConceptList() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        // Concept.equals() also compares externalId+vocabulary, not id — give c1/c2 distinct
        // externalIds too, or Mockito's default equals-based argument matching treats
        // conceptMapper.convert(c1)/(c2) as the same stub and the later one wins for both calls.
        Concept c1 = new Concept();
        c1.setId(1L);
        c1.setExternalId("1");
        Concept c2 = new Concept();
        c2.setId(2L);
        c2.setExternalId("2");
        // ConceptDTO.equals() compares externalId+vocabulary, not id (see the class itself) — two
        // DTOs with the same (null) externalId collapse into one another in a Set, so give each a
        // distinct externalId here purely to keep them distinguishable for this assertion.
        ConceptDTO d1 = new ConceptDTO();
        d1.setId(1L);
        d1.setExternalId("1");
        ConceptDTO d2 = new ConceptDTO();
        d2.setId(2L);
        d2.setExternalId("2");
        when(conceptService.findById(1L)).thenReturn(Optional.of(c1));
        when(conceptService.findById(2L)).thenReturn(Optional.of(c2));
        when(conceptMapper.convert(c1)).thenReturn(d1);
        when(conceptMapper.convert(c2)).thenReturn(d2);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-115", new AnswerInput(null, List.of(1, 2))); // PERIODS_FIELD

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getPeriods()).containsExactlyInAnyOrder(d1, d2);
    }

    /**
     * "values: null" veut dire "ne pas toucher" pour un champ à sélection multiple — pas "vider",
     * contrairement à "value: null" pour un champ scalaire. Même convention que
     * RecordingUnitPatchRequest.answers.
     */
    @Test
    void patchProject_answers_nullValuesOnASelectManyField_leavesItUntouched() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        ConceptDTO existing = new ConceptDTO();
        existing.setId(9L);
        au.setPeriods(new java.util.LinkedHashSet<>(List.of(existing)));
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-115", new AnswerInput(null, null));

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getPeriods()).containsExactly(existing);
    }

    @Test
    void patchProject_answers_resolvesAndWritesASelectOneSpatialUnit() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(42L);
        place.setName("Lyon");
        when(spatialUnitService.findById(42L)).thenReturn(place);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-108", new AnswerInput(42, null)); // MAIN_LOCATION_FIELD

        service.patchProject(caller, "7", patch, "fr");

        assertThat(au.getMainLocation()).isNotNull();
        assertThat(au.getMainLocation().getId()).isEqualTo(42L);
    }

    @Test
    void patchProject_answers_typeField_overwritesTheLocalUsedForSave() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        au.setType(new ConceptDTO());
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
        when(actionUnitService.save(any(), same(au), any())).thenReturn(au);

        Concept typeConcept = new Concept();
        typeConcept.setId(5L);
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(5L);
        when(conceptService.findById(5L)).thenReturn(Optional.of(typeConcept));
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-101", new AnswerInput(5, null)); // ACTION_UNIT_TYPE_FIELD

        service.patchProject(caller, "7", patch, "fr");

        // save(userInfo, dto, type) must receive the answers-updated type, not the stale one read
        // before applyAnswerPatch ran.
        verify(actionUnitService).save(any(), same(au), eq(typeDto));
        assertThat(au.getType()).isEqualTo(typeDto);
    }

    @Test
    void patchProject_answers_unknownFieldId_throws400() throws Exception {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);

        ProjectPatchRequest patch = new ProjectPatchRequest();
        patch.getAnswers().put("-999999", new AnswerInput("x", null));

        assertThatThrownBy(() -> service.patchProject(caller, "7", patch, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(actionUnitService, never()).save(any(), any(), any());
    }

    @Test
    void deleteProject_withRecordingUnits_throws409() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 2L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);

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
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);

        service.deleteProject(caller, "7", "fr");

        verify(actionUnitService).deleteProjectWhenEmpty(7L);
    }

    @Test
    void deleteProject_domainConflict_throws409() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        when(profilePermissionService.hasActionUnitWritePermission(any(), any())).thenReturn(true);
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
        when(actionUnitService.findAccessibleProjects(
                eq(1L), eq(SCOPE), eq(10L), eq("fouille"), any(Pageable.class), isNull(), any()))
                .thenReturn(expected);

        Page<AccessibleProjectForApi> result = service.pageAccessibleProjects(
                caller, 10L, "fouille", 20, 10, "name:desc");

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(actionUnitService).findAccessibleProjects(
                eq(1L), eq(SCOPE), eq(10L), eq("fouille"), pageableCaptor.capture(), isNull(), any());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pageAccessibleProjects_appendsStableIdTiebreaker() {
        when(actionUnitService.findAccessibleProjects(any(), any(), any(), any(), any(Pageable.class), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pageAccessibleProjects(caller, null, null, 0, 10, "name:desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(actionUnitService).findAccessibleProjects(
                any(), any(), any(), any(), pageableCaptor.capture(), isNull(), any());
        Sort sort = pageableCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void pageAccessibleProjects_defaultsToNameAscWhenSortIsBlank() {
        when(actionUnitService.findAccessibleProjects(any(), any(), any(), any(), any(Pageable.class), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pageAccessibleProjects(caller, null, null, 0, 10, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(actionUnitService).findAccessibleProjects(
                any(), any(), any(), any(), pageableCaptor.capture(), isNull(), any());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void pageAccessibleProjects_rejectsUnknownSortField() {
        assertThatThrownBy(() -> service.pageAccessibleProjects(caller, null, null, 0, 10, "bogus:asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(actionUnitService);
    }

    @Test
    void pageAccessibleProjects_forwardsTheFilterToTheSevenArgOverload() {
        fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter filter =
                fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter.parse(
                        new org.springframework.util.LinkedMultiValueMap<>(java.util.Map.of("f.name", List.of("foss"))));
        when(actionUnitService.findAccessibleProjects(any(), any(), any(), any(), any(Pageable.class), isNull(), eq(filter)))
                .thenReturn(new PageImpl<>(List.of()));

        service.pageAccessibleProjects(caller, null, null, 0, 10, "name:asc", filter);

        verify(actionUnitService).findAccessibleProjects(any(), any(), any(), any(), any(Pageable.class), isNull(), eq(filter));
    }

    @Test
    void pageAccessibleProjects_sixArgOverload_forwardsAnEmptyFilter() {
        when(actionUnitService.findAccessibleProjects(
                any(), any(), any(), any(), any(Pageable.class), isNull(),
                eq(fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter.EMPTY)))
                .thenReturn(new PageImpl<>(List.of()));

        service.pageAccessibleProjects(caller, null, null, 0, 10, "name:asc");

        verify(actionUnitService).findAccessibleProjects(
                any(), any(), any(), any(), any(Pageable.class), isNull(),
                eq(fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter.EMPTY));
    }

    /**
     * {@code recordingUnitCount} n'est pas un chemin JPA : il doit sortir du {@code Pageable} (sinon
     * Hibernate échoue sur une propriété inconnue) et repartir comme direction vers le service, qui
     * l'applique via {@code ActionUnitSpec#orderByRecordingUnitCount}.
     */
    @Test
    void pageAccessibleProjects_carriesRecordingUnitCountSortOutsideThePageable() {
        when(actionUnitService.findAccessibleProjects(
                any(), any(), any(), any(), any(Pageable.class), eq(Sort.Direction.DESC), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pageAccessibleProjects(caller, null, null, 0, 10, "recordingUnitCount:desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(actionUnitService).findAccessibleProjects(
                any(), any(), any(), any(), pageableCaptor.capture(), eq(Sort.Direction.DESC), any());
        Sort sort = pageableCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("recordingUnitCount")).isNull();
        assertThat(sort.getOrderFor("id")).isNotNull();
    }

    @Test
    void pageRecordingUnitsForProject_delegatesWithDefaultSort() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        Page<RecordingUnitDTO> expected = new PageImpl<>(List.of());
        when(recordingUnitService.findByActionUnitId(eq(7L), eq(10), eq(0), any(Sort.class), any(FilterDTO.class)))
                .thenReturn(expected);

        Page<RecordingUnitDTO> result = service.pageRecordingUnitsForProject(
                caller, "7", 0, 10, null, null, RecordingUnitListFilter.EMPTY);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(recordingUnitService).findByActionUnitId(eq(7L), eq(10), eq(0), sortCaptor.capture(), any(FilterDTO.class));
        assertThat(sortCaptor.getValue().getOrderFor("creationTime").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pageRecordingUnitsForProject_buildsFilterDTOFromSearchAndFilter() {
        ActionUnitDTO au = projectWithInstitution();
        au.setId(7L);
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(row);
        Page<RecordingUnitDTO> expected = new PageImpl<>(List.of());
        when(recordingUnitService.findByActionUnitId(eq(7L), eq(10), eq(0), any(Sort.class), any(FilterDTO.class)))
                .thenReturn(expected);

        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(
                new org.springframework.util.LinkedMultiValueMap<>(Map.of("f.type", List.of("12"))));

        service.pageRecordingUnitsForProject(caller, "7", 0, 10, null, "UE1", filter);

        ArgumentCaptor<FilterDTO> filterCaptor = ArgumentCaptor.forClass(FilterDTO.class);
        verify(recordingUnitService).findByActionUnitId(eq(7L), eq(10), eq(0), any(Sort.class), filterCaptor.capture());
        FilterDTO dto = filterCaptor.getValue();
        assertThat(dto.containsColumn("fullIdentifier")).isTrue();
        assertThat(dto.valueOfAsString("fullIdentifier")).isEqualTo("UE1");
        assertThat(dto.containsColumn("type")).isTrue();
        assertThat(dto.valueAsIdListOf("type")).containsExactly(12L);
    }

    @Test
    void pageRecordingUnitsForProject_requiresAccessibleProjectBeforeAnyPaging() {
        when(actionUnitService.findAccessibleProjectByKey("404", SCOPE))
                .thenThrow(new fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException("missing"));

        assertThatThrownBy(() -> service.pageRecordingUnitsForProject(caller, "404", 0, 10, null, null, RecordingUnitListFilter.EMPTY))
                .isInstanceOf(fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException.class);

        verifyNoInteractions(recordingUnitService);
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
        when(profilePermissionService.hasActionUnitCreatePermission(any()))
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
        when(profilePermissionService.hasActionUnitWritePermission(any(), any()))
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
