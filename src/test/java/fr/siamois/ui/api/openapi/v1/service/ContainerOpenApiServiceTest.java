package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerOpenApiServiceTest {

    @Mock
    private ContainerService containerService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private UnitDefinitionMapper unitDefinitionMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private ContainerOpenApiMapper containerOpenApiMapper;
    @Mock
    private ContainerListProjectionService containerListProjectionService;

    private ContainerOpenApiService service;

    private PersonDTO personDto;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        service = new ContainerOpenApiService(containerService, actionUnitService, conceptService, conceptMapper,
                spatialUnitService, unitDefinitionMapper, profilePermissionService, containerOpenApiMapper,
                containerListProjectionService, mock(ResourceBookmarkService.class), mock(EntitySiblingsService.class), org.mockito.Mockito.mock(fr.siamois.ui.api.openapi.v1.service.ValidationOpenApiService.class));

        personDto = new PersonDTO();
        personDto.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);

        lenient().when(containerListProjectionService.buildOne(any(), any()))
                .thenReturn(new ContainerListProjectionService.ContainerListProjection(Map.of(), Map.of()));
        lenient().when(containerOpenApiMapper.toResource(any(), any(), any())).thenAnswer(inv -> {
            ContainerDTO dto = inv.getArgument(0);
            ContainerResource r = new ContainerResource();
            r.setResourceType("containers");
            r.setId(dto.getId() != null ? String.valueOf(dto.getId()) : null);
            return r;
        });
    }

    private ContainerDTO containerOn(ActionUnitDTO au) {
        ContainerDTO container = new ContainerDTO();
        container.setId(5L);
        container.setActionUnit(new ActionUnitSummaryDTO(au));
        return container;
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        au.setCreatedByInstitution(institution);
        return au;
    }

    // --- getContainerById ---

    @Test
    void getContainerById_notFound_throws404() {
        when(containerService.findById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.getContainerById(5L, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getContainerById_outsideInstitutionScope_throws404() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);

        assertThatThrownBy(() -> service.getContainerById(5L, personDto, Set.of(999L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getContainerById_cannotViewProject_throws404() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.getContainerById(5L, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getContainerById_setsPermissionsFromWriteCheck() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_CONTAINERS), eq(PermissionConstants.ORGANIZATION_EDIT_CONTAINERS),
                eq(PermissionConstants.PROJECT_EDIT_CONTAINERS)))
                .thenReturn(true);

        ContainerResource result = service.getContainerById(5L, personDto, Set.of(10L), "fr");

        assertThat(result.getPermissions().canEdit()).isTrue();
    }

    // --- createContainer ---

    @Test
    void createContainer_blankProjectId_throws400() {
        ContainerCreateRequest req = new ContainerCreateRequest();
        req.setProjectId(" ");
        req.setTypeId("2");

        assertThatThrownBy(() -> service.createContainer(req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createContainer_withoutWritePermission_throws403() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_CONTAINERS), eq(PermissionConstants.ORGANIZATION_EDIT_CONTAINERS),
                eq(PermissionConstants.PROJECT_EDIT_CONTAINERS)))
                .thenReturn(false);

        ContainerCreateRequest req = new ContainerCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        assertThatThrownBy(() -> service.createContainer(req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(containerService);
    }

    @Test
    void createContainer_unknownType_throws404() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.empty());

        ContainerCreateRequest req = new ContainerCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        assertThatThrownBy(() -> service.createContainer(req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createContainer_success_savesWithActionUnitAndType() {
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

        ContainerDTO saved = containerOn(au);
        when(containerService.save(any(ContainerDTO.class))).thenReturn(saved);

        ContainerCreateRequest req = new ContainerCreateRequest();
        req.setProjectId("7");
        req.setTypeId("2");

        ContainerResource result = service.createContainer(req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<ContainerDTO> captor = ArgumentCaptor.forClass(ContainerDTO.class);
        verify(containerService).save(captor.capture());
        assertThat(captor.getValue().getActionUnit().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getType()).isEqualTo(typeDto);
        assertThat(result.getId()).isEqualTo("5");
    }

    // --- patchContainer ---

    @Test
    void patchContainer_withoutWritePermission_throws403() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_CONTAINERS), eq(PermissionConstants.ORGANIZATION_EDIT_CONTAINERS),
                eq(PermissionConstants.PROJECT_EDIT_CONTAINERS)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.patchContainer(5L, new ContainerPatchRequest(), personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(containerService, never()).save(any());
    }

    @Test
    void patchContainer_unknownFieldId_throws400() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);

        ContainerPatchRequest req = new ContainerPatchRequest();
        req.setAnswers(Map.of("999999", new AnswerInput("x", null)));

        assertThatThrownBy(() -> service.patchContainer(5L, req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchContainer_withSpatialUnitAnswer_resolvesAndWritesSpatialUnit() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(containerService.save(any(ContainerDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        SpatialUnitDTO spatialUnit = new SpatialUnitDTO();
        spatialUnit.setId(33L);
        spatialUnit.setName("Zone A");
        when(spatialUnitService.findById(33L)).thenReturn(spatialUnit);

        // -603 is ContainerForm.spatialUnitField's own hardcoded id.
        ContainerPatchRequest req = new ContainerPatchRequest();
        req.setAnswers(Map.of("-603", new AnswerInput("33", null)));

        service.patchContainer(5L, req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<ContainerDTO> captor = ArgumentCaptor.forClass(ContainerDTO.class);
        verify(containerService).save(captor.capture());
        assertThat(captor.getValue().getSpatialUnit().getId()).isEqualTo(33L);
    }

    @Test
    void patchContainer_withUnknownSpatialUnit_throws404() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(spatialUnitService.findById(33L)).thenThrow(new RuntimeException("not found"));

        ContainerPatchRequest req = new ContainerPatchRequest();
        req.setAnswers(Map.of("-603", new AnswerInput("33", null)));

        assertThatThrownBy(() -> service.patchContainer(5L, req, personDto, Set.of(10L), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void patchContainer_withWeightAnswer_coercesMeasurementFromNumber() {
        ContainerDTO container = containerOn(projectWithInstitution());
        when(containerService.findById(5L)).thenReturn(container);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(containerService.save(any(ContainerDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        // -608 is ContainerForm.weightField's own hardcoded id.
        ContainerPatchRequest req = new ContainerPatchRequest();
        req.setAnswers(Map.of("-608", new AnswerInput(12.5, null)));

        service.patchContainer(5L, req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<ContainerDTO> captor = ArgumentCaptor.forClass(ContainerDTO.class);
        verify(containerService).save(captor.capture());
        assertThat(captor.getValue().getWeight().getNumericValue()).isEqualTo(12.5);
    }
}
