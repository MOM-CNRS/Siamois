package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.phase.PhaseCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhaseOpenApiServiceTest {

    @Mock
    private PhaseService phaseService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private PhaseOpenApiMapper phaseOpenApiMapper;
    @Mock
    private PhaseListProjectionService phaseListProjectionService;

    private PhaseOpenApiService service;

    private PersonDTO personDto;
    private InstitutionDTO institution;

    @Mock
    private fr.siamois.domain.services.ValidationStatusService validationStatusService;

    @BeforeEach
    void setUp() {
        service = new PhaseOpenApiService(phaseService, actionUnitService, conceptService, conceptMapper,
                profilePermissionService, phaseOpenApiMapper, phaseListProjectionService, mock(ResourceBookmarkService.class), mock(EntitySiblingsService.class), new ValidationOpenApiService(validationStatusService));

        personDto = new PersonDTO();
        personDto.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);

        lenient().when(phaseListProjectionService.buildOne(any(), any()))
                .thenReturn(new PhaseListProjectionService.PhaseListProjection(Map.of(), Map.of()));
        lenient().when(phaseOpenApiMapper.toResource(any(), any(), any())).thenAnswer(inv -> {
            PhaseDTO dto = inv.getArgument(0);
            PhaseResource r = new PhaseResource();
            r.setResourceType("phases");
            r.setId(dto.getId() != null ? String.valueOf(dto.getId()) : null);
            return r;
        });
    }

    private PhaseDTO phaseOn(ActionUnitDTO au) {
        PhaseDTO phase = new PhaseDTO();
        phase.setId(5L);
        phase.setActionUnit(new ActionUnitSummaryDTO(au));
        return phase;
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        au.setCreatedByInstitution(institution);
        return au;
    }

    // --- getPhaseById ---

    @Test
    void getPhaseById_notFound_throws404() {
        when(phaseService.findById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.getPhaseById(5L, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPhaseById_outsideInstitutionScope_throws404() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);

        assertThatThrownBy(() -> service.getPhaseById(5L, personDto, Set.of(999L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPhaseById_cannotViewProject_throws404() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.getPhaseById(5L, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPhaseById_setsPermissionsFromWriteCheck() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_PHASES), eq(PermissionConstants.ORGANIZATION_EDIT_PHASES),
                eq(PermissionConstants.PROJECT_EDIT_PHASES)))
                .thenReturn(true);

        PhaseResource result = service.getPhaseById(5L, personDto, Set.of(10L), "fr");

        assertThat(result.getPermissions().canEdit()).isTrue();
    }

    // --- createPhase ---

    @Test
    void createPhase_blankProjectId_throws400() {
        PhaseCreateRequest req = new PhaseCreateRequest();
        req.setProjectId(" ");
        req.setTypeId("2");

        assertThatThrownBy(() -> service.createPhase(req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createPhase_withoutWritePermission_throws403() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_PHASES), eq(PermissionConstants.ORGANIZATION_EDIT_PHASES),
                eq(PermissionConstants.PROJECT_EDIT_PHASES)))
                .thenReturn(false);

        PhaseCreateRequest req = new PhaseCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        assertThatThrownBy(() -> service.createPhase(req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(phaseService);
    }

    @Test
    void createPhase_unknownType_throws404() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.empty());

        PhaseCreateRequest req = new PhaseCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        assertThatThrownBy(() -> service.createPhase(req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createPhase_success_savesWithActionUnitAndType() {
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

        PhaseDTO saved = phaseOn(au);
        when(phaseService.save(any(PhaseDTO.class))).thenReturn(saved);

        PhaseCreateRequest req = new PhaseCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");
        req.setTitle("Phase 1");

        PhaseResource result = service.createPhase(req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<PhaseDTO> captor = ArgumentCaptor.forClass(PhaseDTO.class);
        verify(phaseService).save(captor.capture());
        assertThat(captor.getValue().getActionUnit().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getType()).isEqualTo(typeDto);
        assertThat(captor.getValue().getTitle()).isEqualTo("Phase 1");
        assertThat(result.getId()).isEqualTo("5");
    }

    // --- patchPhase ---

    @Test
    void patchPhase_withoutWritePermission_throws403() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_PHASES), eq(PermissionConstants.ORGANIZATION_EDIT_PHASES),
                eq(PermissionConstants.PROJECT_EDIT_PHASES)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.patchPhase(5L, new PhasePatchRequest(), personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(phaseService, never()).save(any());
    }

    // `validated` rides the same PATCH: the edit right covers en cours/terminé/annulé, the validator
    // right covers validé — alone, a validator without the edit right may change the status only.
    @Test
    void patchPhase_validatorWithoutEditRight_mayChangeOnlyTheStatus() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_PHASES), eq(PermissionConstants.ORGANIZATION_EDIT_PHASES),
                eq(PermissionConstants.PROJECT_EDIT_PHASES))).thenReturn(false);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(7L))).thenReturn(true);

        PhasePatchRequest statusOnly = new PhasePatchRequest();
        statusOnly.setValidated(fr.siamois.domain.models.ValidationStatus.VALIDATED);
        service.patchPhase(5L, statusOnly, personDto, Set.of(10L), "fr");

        verify(validationStatusService).setStatus(fr.siamois.domain.models.phase.Phase.class, 5L,
                fr.siamois.domain.models.ValidationStatus.VALIDATED, personDto.getId());
        verify(phaseService, never()).save(any());

        PhasePatchRequest withAnswers = new PhasePatchRequest();
        withAnswers.setValidated(fr.siamois.domain.models.ValidationStatus.VALIDATED);
        withAnswers.setAnswers(Map.of("-503", new AnswerInput("Titre", null)));
        assertThatThrownBy(() -> service.patchPhase(5L, withAnswers, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void patchPhase_editorWithoutValidatorRight_cannotValidate() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(7L))).thenReturn(false);

        PhasePatchRequest req = new PhasePatchRequest();
        req.setValidated(fr.siamois.domain.models.ValidationStatus.VALIDATED);

        assertThatThrownBy(() -> service.patchPhase(5L, req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(validationStatusService, never()).setStatus(any(), anyLong(), any(), any());
    }

    @Test
    void patchPhase_unknownFieldId_throws400() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);

        PhasePatchRequest req = new PhasePatchRequest();
        req.setAnswers(Map.of("999999", new AnswerInput("x", null)));

        assertThatThrownBy(() -> service.patchPhase(5L, req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchPhase_withTitleAnswer_writesTitleAndSaves() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(phaseService.save(any(PhaseDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        // -503 is PhaseForm.titleField's own hardcoded id.
        PhasePatchRequest req = new PhasePatchRequest();
        req.setAnswers(Map.of("-503", new AnswerInput("Nouveau titre", null)));

        service.patchPhase(5L, req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<PhaseDTO> captor = ArgumentCaptor.forClass(PhaseDTO.class);
        verify(phaseService).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Nouveau titre");
    }

    @Test
    void patchPhase_withOrderNumberAnswer_coercesInteger() {
        PhaseDTO phase = phaseOn(projectWithInstitution());
        when(phaseService.findById(5L)).thenReturn(phase);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(phaseService.save(any(PhaseDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        // -505 is PhaseForm.orderNumberField's own hardcoded id.
        PhasePatchRequest req = new PhasePatchRequest();
        req.setAnswers(Map.of("-505", new AnswerInput("3", null)));

        service.patchPhase(5L, req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<PhaseDTO> captor = ArgumentCaptor.forClass(PhaseDTO.class);
        verify(phaseService).save(captor.capture());
        assertThat(captor.getValue().getOrderNumber()).isEqualTo(3);
    }
}
