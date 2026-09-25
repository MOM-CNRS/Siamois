package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.specs.ContainerSpec;
import fr.siamois.infrastructure.database.repositories.specs.PhaseSpec;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService.EditPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationListServiceTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private ActionUnitService actionUnitService;
    @Mock private RecordingUnitService recordingUnitService;
    @Mock private SpecimenService specimenService;
    @Mock private PhaseService phaseService;
    @Mock private ContainerService containerService;

    private OrganizationListService service;
    private ProjectApiCaller caller;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        service = new OrganizationListService(projectApiService, profilePermissionService, actionUnitService,
                recordingUnitService, specimenService, phaseService, containerService);
        institution = new InstitutionDTO();
        institution.setId(10L);
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of(institution));
    }

    @Test
    void requireListOrganization_withoutOrganizationId_throws400() {
        assertThatThrownBy(() -> service.requireListOrganization(caller, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(projectApiService);
    }

    @Test
    void requireListOrganization_delegatesScopeCheck() {
        when(projectApiService.requireOrganization(10L, caller)).thenReturn(institution);

        assertThat(service.requireListOrganization(caller, 10L)).isSameAs(institution);
    }

    @Test
    void pageRecordingUnits_institutionMember_seesEverything_withSearch() {
        when(profilePermissionService.canViewInstitutionData(caller.person(), institution)).thenReturn(true);
        when(recordingUnitService.searchRecordingUnit(eq(institution), any(), any(), eq(false))).thenReturn(Page.empty());

        service.pageRecordingUnits(caller, institution, 0, 10, "creationTime:desc", "US-1", RecordingUnitListFilter.EMPTY);

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        verify(recordingUnitService).searchRecordingUnit(eq(institution), filter.capture(), any(), eq(false));
        assertThat(filter.getValue().containsColumn(RecordingUnitSpec.ACTION_UNIT_FILTER)).isFalse();
        assertThat(filter.getValue().valueOfAsString(RecordingUnitSpec.FULL_IDENTIFIER)).isEqualTo("US-1");
        verifyNoInteractions(actionUnitService);
    }

    @Test
    void pageFinds_projectOnlyMember_isRestrictedToTheirProjects() {
        when(profilePermissionService.canViewInstitutionData(caller.person(), institution)).thenReturn(false);
        when(actionUnitService.findMemberProjectIds(1L, 10L)).thenReturn(List.of(7L, 8L));
        when(specimenService.searchSpecimen(eq(institution), any(), any())).thenReturn(Page.empty());

        service.pageFinds(caller, institution, 0, 10, null, null);

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        verify(specimenService).searchSpecimen(eq(institution), filter.capture(), any());
        assertThat(filter.getValue().valueAsIdListOf(SpecimenSpec.ACTION_UNIT_FILTER)).containsExactly(7L, 8L);
    }

    @Test
    void pagePhases_memberOfNoProject_getsAnEmptyPageWithoutQuerying() {
        when(profilePermissionService.canViewInstitutionData(caller.person(), institution)).thenReturn(false);
        when(actionUnitService.findMemberProjectIds(1L, 10L)).thenReturn(List.of());

        Page<?> page = service.pagePhases(caller, institution, 0, 10, null, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(phaseService);
    }

    @Test
    void pageContainers_searchesOnIdentifier_andPagesFromOffset() {
        when(profilePermissionService.canViewInstitutionData(caller.person(), institution)).thenReturn(true);
        when(containerService.searchContainers(eq(institution), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.pageContainers(caller, institution, 20, 10, "identifier:desc", "BOX");

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(containerService).searchContainers(eq(institution), filter.capture(), pageable.capture());
        assertThat(filter.getValue().valueOfAsString(ContainerSpec.IDENTIFIER_FILTER)).isEqualTo("BOX");
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().getOrderFor(ContainerSpec.IDENTIFIER_FILTER).isDescending()).isTrue();
    }

    @Test
    void pagePhases_unknownSortProperty_throws400() {
        assertThatThrownBy(() -> service.pagePhases(caller, institution, 0, 10, "nope:asc", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(phaseService);
    }

    @Test
    void pagePhases_searchesOnIdentifier() {
        when(profilePermissionService.canViewInstitutionData(caller.person(), institution)).thenReturn(true);
        when(phaseService.searchPhases(eq(institution), any(), any())).thenReturn(Page.empty());

        service.pagePhases(caller, institution, 0, 10, null, "PH");

        ArgumentCaptor<FilterDTO> filter = ArgumentCaptor.forClass(FilterDTO.class);
        verify(phaseService).searchPhases(eq(institution), filter.capture(), any());
        assertThat(filter.getValue().valueOfAsString(PhaseSpec.IDENTIFIER_FILTER)).isEqualTo("PH");
    }

    @Test
    void canEditByProject_checksEachDistinctProjectOnce() {
        ActionUnitSummaryDTO p7 = project(7L);
        ActionUnitSummaryDTO p8 = project(8L);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(8L), any(), any(), any())).thenReturn(false);

        Map<Long, Boolean> result = service.canEditByProject(caller, institution,
                Arrays.asList(p7, p8, p7, null, p7), "fr", EditPermissions.PHASES);

        assertThat(result).containsEntry(7L, true).containsEntry(8L, false).hasSize(2);
        verify(profilePermissionService, times(2)).hasProjectPermission(any(UserInfo.class), anyLong(),
                eq(EditPermissions.PHASES.instance()), eq(EditPermissions.PHASES.organization()), eq(EditPermissions.PHASES.project()));
    }

    @Test
    void canValidateByProject_checksEachDistinctProjectOnce() {
        ActionUnitSummaryDTO p7 = project(7L);
        ActionUnitSummaryDTO p8 = project(8L);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(7L))).thenReturn(true);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(8L))).thenReturn(false);

        Map<Long, Boolean> result = service.canValidateByProject(caller, institution,
                Arrays.asList(p7, p8, p7, null, p7), "fr");

        assertThat(result).containsEntry(7L, true).containsEntry(8L, false).hasSize(2);
        verify(profilePermissionService, times(2)).hasValidatePermission(any(UserInfo.class), anyLong());
    }

    @Test
    void projectRef_prefersFullIdentifier_thenName() {
        ActionUnitSummaryDTO withFull = project(7L);
        withFull.setFullIdentifier("OA-7");
        withFull.setName("Fouille");
        ActionUnitSummaryDTO nameOnly = project(8L);
        nameOnly.setName("Sondage");

        assertThat(OrganizationListService.projectRef(withFull)).isEqualTo(new ResourceRef("7", "projects", "OA-7"));
        assertThat(OrganizationListService.projectRef(nameOnly)).isEqualTo(new ResourceRef("8", "projects", "Sondage"));
        assertThat(OrganizationListService.projectRef(null)).isNull();
    }

    private static ActionUnitSummaryDTO project(Long id) {
        ActionUnitSummaryDTO dto = new ActionUnitSummaryDTO();
        dto.setId(id);
        return dto;
    }
}
