package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.study.StudyService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.exception.IdentifierConflictException;
import fr.siamois.ui.api.openapi.v1.mapper.StudyOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.study.StudyCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.study.StudyPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.api.openapi.v1.resource.study.StudyResource;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyOpenApiServiceTest {

    @Mock
    private StudyService studyService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private StudyOpenApiMapper studyOpenApiMapper;
    @Mock
    private StudyListProjectionService studyListProjectionService;

    @Mock
    private EntitySiblingsService entitySiblingsService;

    @Mock
    private FieldAnswerPatchService fieldAnswerPatchService;
    @Mock
    private EffectiveFormResolver effectiveFormResolver;
    @Mock
    private FieldAnswerWireService fieldAnswerWireService;
    @Mock
    private fr.siamois.domain.services.form.CustomFieldAnswerService customFieldAnswerService;

    private StudyOpenApiService service;

    private PersonDTO personDto;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        service = new StudyOpenApiService(studyService, new ProjectTypedCreationService(actionUnitService, conceptService, conceptMapper, profilePermissionService, org.mockito.Mockito.mock(fr.siamois.domain.services.form.EffectiveFormResolver.class), org.mockito.Mockito.mock(FieldAnswerPatchService.class)),
                profilePermissionService, studyOpenApiMapper,
                studyListProjectionService, ListQueryStubs.multiValueAnswers(), mock(ResourceBookmarkService.class), entitySiblingsService, mock(fr.siamois.ui.api.openapi.v1.service.ValidationOpenApiService.class),
                fieldAnswerPatchService, fieldAnswerWireService, customFieldAnswerService, effectiveFormResolver);

        lenient().when(fieldAnswerWireService.additionalAnswers(any(), any())).thenReturn(Map.of());
        personDto = new PersonDTO();
        personDto.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);

        lenient().when(studyListProjectionService.buildOne(any(), any()))
                .thenReturn(new StudyListProjectionService.StudyListProjection(Map.of(), Map.of()));
        lenient().when(studyOpenApiMapper.toResource(any(), any(), any())).thenAnswer(inv -> {
            StudyDTO dto = inv.getArgument(0);
            StudyResource r = new StudyResource();
            r.setResourceType("studies");
            r.setId(dto.getId() != null ? String.valueOf(dto.getId()) : null);
            return r;
        });
    }

    private StudyDTO studyOn(ActionUnitDTO au) {
        StudyDTO study = new StudyDTO();
        study.setId(5L);
        study.setActionUnit(new ActionUnitSummaryDTO(au));
        return study;
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        au.setCreatedByInstitution(institution);
        return au;
    }

    // --- getStudyById ---

    @Test
    void getStudyById_notFound_throws404() {
        when(studyService.findById(5L)).thenReturn(null);

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.getStudyById(5L, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getStudyById_outsideInstitutionScope_throws404() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);

        var arg3 = Set.of(999L);
        assertThatThrownBy(() -> service.getStudyById(5L, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getStudyById_cannotViewProject_throws404() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(false);

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.getStudyById(5L, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getStudyById_setsPermissionsFromWriteCheck() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_STUDIES), eq(PermissionConstants.ORGANIZATION_EDIT_STUDIES),
                eq(PermissionConstants.PROJECT_EDIT_STUDIES)))
                .thenReturn(true);

        StudyResource result = service.getStudyById(5L, personDto, Set.of(10L), "fr");

        assertThat(result.getPermissions().canEdit()).isTrue();
    }

    // --- createStudy ---

    @Test
    void createStudy_blankProjectId_throws400() {
        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId(" ");
        req.setTypeId("2");

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.createStudy(req, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createStudy_withoutWritePermission_throws403() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_STUDIES), eq(PermissionConstants.ORGANIZATION_EDIT_STUDIES),
                eq(PermissionConstants.PROJECT_EDIT_STUDIES)))
                .thenReturn(false);

        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.createStudy(req, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(studyService);
    }

    @Test
    void createStudy_unknownType_throws404() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.empty());

        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.createStudy(req, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createStudy_projectWithoutOrganization_throws400() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(new AccessibleProjectForApi(au, 0L, 0L));
        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        var scope = Set.of(10L);
        assertThatThrownBy(() -> service.createStudy(req, personDto, scope, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createStudy_nonNumericType_throws400() {
        ActionUnitDTO au = projectWithInstitution();
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(new AccessibleProjectForApi(au, 0L, 0L));
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any())).thenReturn(true);
        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId("7");
        req.setTypeId("not-a-number");

        var scope = Set.of(10L);
        assertThatThrownBy(() -> service.createStudy(req, personDto, scope, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void requireAccessible_returnsAStudyTheCallerCanSee() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);

        assertThat(service.requireAccessible(5L, personDto, Set.of(10L))).isSameAs(study);
    }

    @Test
    void requireAccessible_hidesAStudyOfAProjectTheCallerCannotView() {
        when(studyService.findById(5L)).thenReturn(studyOn(projectWithInstitution()));
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(false);

        var scope = Set.of(10L);
        assertThatThrownBy(() -> service.requireAccessible(5L, personDto, scope))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void findSiblings_asksForTheNeighboursInTheSameProject() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource siblings =
                mock(fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource.class);
        when(entitySiblingsService.findSiblings(EntitySiblingsService.Kind.STUDY, 7L, 5L)).thenReturn(siblings);

        assertThat(service.findSiblings(5L, personDto, Set.of(10L))).isSameAs(siblings);
    }

    @Test
    void createStudy_success_savesWithActionUnitAndType() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);

        Concept typeConcept = new Concept();
        typeConcept.setId(2L);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.of(typeConcept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(2L);
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);

        StudyDTO saved = studyOn(au);
        when(studyService.save(any(StudyDTO.class), anyMap())).thenReturn(saved);

        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        StudyResource result = service.createStudy(req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<StudyDTO> captor = ArgumentCaptor.forClass(StudyDTO.class);
        verify(studyService).save(captor.capture(), anyMap());
        assertThat(captor.getValue().getActionUnit().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getType()).isEqualTo(typeDto);
        assertThat(result.getId()).isEqualTo("5");
        // TraceableEntity's NOT NULL author and organization: the caller, in the project's organization.
        assertThat(captor.getValue().getCreatedBy()).isSameAs(personDto);
        assertThat(captor.getValue().getCreatedByInstitution()).isSameAs(institution);
    }

    // --- patchStudy ---

    @Test
    void patchStudy_withoutWritePermission_throws403() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_STUDIES), eq(PermissionConstants.ORGANIZATION_EDIT_STUDIES),
                eq(PermissionConstants.PROJECT_EDIT_STUDIES)))
                .thenReturn(false);

        var arg2 = new StudyPatchRequest();
        var arg4 = Set.of(10L);
        assertThatThrownBy(() -> service.patchStudy(5L, arg2, personDto, arg4, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(studyService, never()).save(any());
    }


    @Test
    void patchStudy_appliesAnswersOnTheTypesEffectiveFormAndSavesTheAdditionalOnes() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        FormUiDto effectiveForm = new FormUiDto();
        when(effectiveFormResolver.resolveEffectiveForm(7L,
                ConfigurableTable.STUDY, null)).thenReturn(effectiveForm);
        Map<CustomField, CustomFieldAnswerViewModel> additional = Map.of(new CustomFieldText(), new CustomFieldAnswerTextViewModel());
        Map<String, AnswerInput> answers = Map.of("-608", new AnswerInput(12.5, null));
        when(fieldAnswerPatchService.apply(study, effectiveForm, answers, 7L)).thenReturn(additional);

        StudyPatchRequest req = new StudyPatchRequest();
        req.setAnswers(answers);
        service.patchStudy(5L, req, personDto, Set.of(10L), "fr");

        verify(studyService).save(study, additional);
    }

    @Test
    void patchStudy_identifierProvided_isTrimmedSavedAndChecked() {
        StudyDTO study = studyOn(projectWithInstitution());
        study.setIdentifier("OLD");
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(effectiveFormResolver.resolveEffectiveForm(7L, ConfigurableTable.STUDY, null))
                .thenReturn(new FormUiDto());

        StudyPatchRequest req = new StudyPatchRequest();
        req.setIdentifier(" NEW ");
        service.patchStudy(5L, req, personDto, Set.of(10L), "fr");

        assertThat(study.getIdentifier()).isEqualTo("NEW");
        verify(studyService).save(eq(study), any());
    }

    @Test
    void patchStudy_identifierAlreadyUsed_throws409() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(studyService.identifierAlreadyExistInAction(study)).thenReturn(true);

        StudyPatchRequest req = new StudyPatchRequest();
        req.setIdentifier("TAKEN");
        var ids = Set.of(10L);
        assertThatThrownBy(() -> service.patchStudy(5L, req, personDto, ids, "fr"))
                .isInstanceOf(IdentifierConflictException.class);
        verify(studyService, never()).save(any(), any());
    }

    @Test
    void patchStudy_blankIdentifier_throws400() {
        StudyDTO study = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(study);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);

        StudyPatchRequest req = new StudyPatchRequest();
        req.setIdentifier("   ");
        var ids = Set.of(10L);
        assertThatThrownBy(() -> service.patchStudy(5L, req, personDto, ids, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- deleteStudy ---

    @Test
    void deleteStudy_notDeleter_throws403_andDeletesNothing() {
        StudyDTO target = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(target);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasDeleteEntitiesPermission(any(UserInfo.class), eq(7L))).thenReturn(false);

        Set<Long> scope = Set.of(10L);
        assertThatThrownBy(() -> service.deleteStudy(5L, personDto, scope, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(studyService, never()).deleteById(anyLong());
    }

    @Test
    void deleteStudy_deleter_deletes() {
        StudyDTO target = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(target);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasDeleteEntitiesPermission(any(UserInfo.class), eq(7L))).thenReturn(true);

        service.deleteStudy(5L, personDto, Set.of(10L), "fr");

        verify(studyService).deleteById(5L);
    }

    @Test
    void deleteStudy_withRelationships_throws409() {
        StudyDTO target = studyOn(projectWithInstitution());
        when(studyService.findById(5L)).thenReturn(target);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasDeleteEntitiesPermission(any(UserInfo.class), eq(7L))).thenReturn(true);
        doThrow(new IllegalStateException("liens")).when(studyService).deleteById(5L);

        Set<Long> scope = Set.of(10L);
        assertThatThrownBy(() -> service.deleteStudy(5L, personDto, scope, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- identifier chosen in the creation form ---

    private ConceptDTO allowCreationOfType2InProject7() {
        ActionUnitDTO au = projectWithInstitution();
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(new AccessibleProjectForApi(au, 0L, 0L));
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any())).thenReturn(true);
        Concept typeConcept = new Concept();
        typeConcept.setId(2L);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.of(typeConcept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(2L);
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);
        return typeDto;
    }

    private StudyCreateRequest creationRequest(String identifier) {
        StudyCreateRequest req = new StudyCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");
        req.setIdentifier(identifier);
        return req;
    }

    @Test
    void createStudy_chosenIdentifier_isSavedTrimmedOnceCheckedFree() {
        allowCreationOfType2InProject7();
        StudyDTO saved = new StudyDTO();
        saved.setId(5L);
        saved.setActionUnit(new ActionUnitSummaryDTO(projectWithInstitution()));
        when(studyService.save(any(StudyDTO.class), anyMap())).thenReturn(saved);

        service.createStudy(creationRequest("  CHOSEN-1 "), personDto, Set.of(10L), "fr");

        ArgumentCaptor<StudyDTO> captor = ArgumentCaptor.forClass(StudyDTO.class);
        verify(studyService).save(captor.capture(), anyMap());
        // Set before the save: the service then skips its own generation.
        assertThat(captor.getValue().getIdentifier()).isEqualTo("CHOSEN-1");
        verify(studyService).identifierAlreadyExistInAction(captor.getValue());
    }

    @Test
    void createStudy_chosenIdentifierAlreadyTaken_throws409WithoutSaving() {
        allowCreationOfType2InProject7();
        when(studyService.identifierAlreadyExistInAction(any(StudyDTO.class))).thenReturn(true);

        var req = creationRequest("TAKEN");
        var scope = Set.of(10L);
        assertThatThrownBy(() -> service.createStudy(req, personDto, scope, "fr"))
                .isInstanceOf(IdentifierConflictException.class);
        verify(studyService, never()).save(any(StudyDTO.class));
    }

    @Test
    void createStudy_blankIdentifier_leavesItToTheServer() {
        allowCreationOfType2InProject7();
        StudyDTO saved = new StudyDTO();
        saved.setId(5L);
        saved.setActionUnit(new ActionUnitSummaryDTO(projectWithInstitution()));
        when(studyService.save(any(StudyDTO.class), anyMap())).thenReturn(saved);

        service.createStudy(creationRequest("   "), personDto, Set.of(10L), "fr");

        ArgumentCaptor<StudyDTO> captor = ArgumentCaptor.forClass(StudyDTO.class);
        verify(studyService).save(captor.capture(), anyMap());
        assertThat(captor.getValue().getIdentifier()).isNull();
        verify(studyService, never()).identifierAlreadyExistInAction(any(StudyDTO.class));
    }

}
