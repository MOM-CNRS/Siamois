package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.specs.ContainerSpec;
import fr.siamois.infrastructure.database.repositories.specs.PhaseSpec;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Organization-wide lists ({@code GET /api/v1/recording-units|finds|phases|containers?organizationId=…})
 * — the React counterparts of JSF's RecordingUnitListPanel/SpecimenListPanel/PhaseListPanel/
 * ContainerListPanel. Search + sort + pagination, same reduction as the project-scoped tabs
 * ({@code f.<key>} only for recording units, whose project-scoped list already supports it).
 *
 * <p>Two rules the project-scoped lists never needed, since every row there shares one project:</p>
 * <ul>
 *   <li>visibility: a person without institution-wide access only sees rows of the projects they
 *   hold a profile on ({@code ProfilePermissionService#canViewProject}) — a row whose fiche would
 *   404 is never listed;</li>
 *   <li>{@code _permissions}: per row, computed once per distinct project of the page.</li>
 * </ul>
 * Kept out of {@link ProjectApiService} on purpose — its constructor already ripples to ~9 tests.
 */
@Service
@RequiredArgsConstructor
public class OrganizationListService {

    private final ProjectApiService projectApiService;
    private final ProfilePermissionService profilePermissionService;
    private final ActionUnitService actionUnitService;
    private final RecordingUnitService recordingUnitService;
    private final SpecimenService specimenService;
    private final PhaseService phaseService;
    private final ContainerService containerService;

    /** The permission triple checked for writing one entity type on a project. */
    public record EditPermissions(String instance, String organization, String project) {
        public static final EditPermissions RECORDING_UNITS = new EditPermissions(
                PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);
        public static final EditPermissions FINDS = new EditPermissions(
                PermissionConstants.INSTANCE_EDIT_FINDS,
                PermissionConstants.ORGANIZATION_EDIT_FINDS,
                PermissionConstants.PROJECT_EDIT_FINDS);
        public static final EditPermissions PHASES = new EditPermissions(
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES);
        public static final EditPermissions CONTAINERS = new EditPermissions(
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS);
    }

    public InstitutionDTO requireListOrganization(ProjectApiCaller caller, Long organizationId) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        return projectApiService.requireOrganization(organizationId, caller);
    }

    /**
     * {@code null} when the caller sees the whole institution; otherwise the ids of the projects they
     * may see (possibly empty).
     */
    List<Long> visibleProjectIdsOrNull(ProjectApiCaller caller, InstitutionDTO institution) {
        if (profilePermissionService.canViewInstitutionData(caller.person(), institution)) {
            return null;
        }
        return actionUnitService.findMemberProjectIds(caller.person().getId(), institution.getId());
    }

    @Transactional(readOnly = true)
    public Page<RecordingUnitDTO> pageRecordingUnits(ProjectApiCaller caller, InstitutionDTO institution,
                                                     int offset, int limit, String sortParam, String search,
                                                     RecordingUnitListFilter columnFilter) {
        return pageRecordingUnits(caller, institution, offset, limit, sortParam, search, columnFilter, FieldQuery.NONE);
    }

    @Transactional(readOnly = true)
    public Page<RecordingUnitDTO> pageRecordingUnits(ProjectApiCaller caller, InstitutionDTO institution,
                                                     int offset, int limit, String sortParam, String search,
                                                     RecordingUnitListFilter columnFilter, FieldQuery fieldQuery) {
        Pageable pageable = pageable(offset, limit, ProjectApiService.sortOr(fieldQuery, sortParam, ProjectApiService::parseRecordingUnitSort));
        // f.<key> columns are honoured here like on the project-scoped RU list (same parser), since
        // the React identifier column is filterable on both.
        FilterDTO filter = columnFilter.toFilterDTO(search);
        filter.setFieldQuery(fieldQuery);
        if (!restrictToVisibleProjects(caller, institution, filter, RecordingUnitSpec.ACTION_UNIT_FILTER)) {
            return Page.empty(pageable);
        }
        return recordingUnitService.searchRecordingUnit(institution, filter, pageable, false);
    }

    @Transactional(readOnly = true)
    public Page<SpecimenDTO> pageFinds(ProjectApiCaller caller, InstitutionDTO institution,
                                 int offset, int limit, String sortParam, String search) {
        return pageFinds(caller, institution, offset, limit, sortParam, search, FieldQuery.NONE);
    }

    @Transactional(readOnly = true)
    public Page<SpecimenDTO> pageFinds(ProjectApiCaller caller, InstitutionDTO institution,
                                 int offset, int limit, String sortParam, String search, FieldQuery fieldQuery) {
        Pageable pageable = pageable(offset, limit, ProjectApiService.sortOr(fieldQuery, sortParam, ProjectApiService::parseFindSort));
        FilterDTO filter = new FilterDTO();
        filter.setFieldQuery(fieldQuery);
        if (search != null && !search.isBlank()) {
            filter.add(SpecimenSpec.FULL_IDENTIFIER_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        if (!restrictToVisibleProjects(caller, institution, filter, SpecimenSpec.ACTION_UNIT_FILTER)) {
            return Page.empty(pageable);
        }
        return specimenService.searchSpecimen(institution, filter, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PhaseDTO> pagePhases(ProjectApiCaller caller, InstitutionDTO institution,
                                 int offset, int limit, String sortParam, String search) {
        return pagePhases(caller, institution, offset, limit, sortParam, search, FieldQuery.NONE);
    }

    @Transactional(readOnly = true)
    public Page<PhaseDTO> pagePhases(ProjectApiCaller caller, InstitutionDTO institution,
                                 int offset, int limit, String sortParam, String search, FieldQuery fieldQuery) {
        Pageable pageable = pageable(offset, limit, ProjectApiService.sortOr(fieldQuery, sortParam, ProjectApiService::parsePhaseSort));
        FilterDTO filter = new FilterDTO();
        filter.setFieldQuery(fieldQuery);
        if (search != null && !search.isBlank()) {
            filter.add(PhaseSpec.IDENTIFIER_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        if (!restrictToVisibleProjects(caller, institution, filter, PhaseSpec.ACTION_UNIT_FILTER)) {
            return Page.empty(pageable);
        }
        return phaseService.searchPhases(institution, filter, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ContainerDTO> pageContainers(ProjectApiCaller caller, InstitutionDTO institution,
                                 int offset, int limit, String sortParam, String search) {
        return pageContainers(caller, institution, offset, limit, sortParam, search, FieldQuery.NONE);
    }

    @Transactional(readOnly = true)
    public Page<ContainerDTO> pageContainers(ProjectApiCaller caller, InstitutionDTO institution,
                                 int offset, int limit, String sortParam, String search, FieldQuery fieldQuery) {
        Pageable pageable = pageable(offset, limit, ProjectApiService.sortOr(fieldQuery, sortParam, ProjectApiService::parseContainerSort));
        FilterDTO filter = new FilterDTO();
        filter.setFieldQuery(fieldQuery);
        if (search != null && !search.isBlank()) {
            filter.add(ContainerSpec.IDENTIFIER_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        if (!restrictToVisibleProjects(caller, institution, filter, ContainerSpec.ACTION_UNIT_FILTER)) {
            return Page.empty(pageable);
        }
        return containerService.searchContainers(institution, filter, pageable);
    }

    /**
     * Write permission per project, one {@code hasProjectPermission} call per DISTINCT project of
     * the page — never per row.
     */
    public Map<Long, Boolean> canEditByProject(ProjectApiCaller caller, InstitutionDTO institution,
                                               Collection<ActionUnitSummaryDTO> projects,
                                               String lang, EditPermissions permissions) {
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        Map<Long, Boolean> result = new HashMap<>();
        projects.stream()
                .filter(Objects::nonNull)
                .map(ActionUnitSummaryDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(projectId -> result.put(projectId, profilePermissionService.hasProjectPermission(
                        userInfo, projectId, permissions.instance(), permissions.organization(), permissions.project())));
        return result;
    }

    /** Same shape as {@link #canEditByProject}, for the "validateur" right on each project's entities. */
    public Map<Long, Boolean> canValidateByProject(ProjectApiCaller caller, InstitutionDTO institution,
                                                   Collection<ActionUnitSummaryDTO> projects, String lang) {
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        Map<Long, Boolean> result = new HashMap<>();
        projects.stream()
                .filter(Objects::nonNull)
                .map(ActionUnitSummaryDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(projectId -> result.put(projectId, profilePermissionService.hasValidatePermission(userInfo, projectId)));
        return result;
    }

    /** The row's project as a light ref for the list's "Projet" column. */
    public static ResourceRef projectRef(ActionUnitSummaryDTO project) {
        if (project == null || project.getId() == null) {
            return null;
        }
        String label = project.getFullIdentifier() != null && !project.getFullIdentifier().isBlank()
                ? project.getFullIdentifier()
                : project.getName();
        return new ResourceRef(String.valueOf(project.getId()), "projects", label);
    }

    /** @return false when the caller may see no project at all (the page is then empty). */
    private boolean restrictToVisibleProjects(ProjectApiCaller caller, InstitutionDTO institution,
                                              FilterDTO filter, String projectFilterKey) {
        List<Long> visible = visibleProjectIdsOrNull(caller, institution);
        if (visible == null) {
            return true;
        }
        if (visible.isEmpty()) {
            return false;
        }
        filter.add(projectFilterKey, visible, FilterDTO.FilterType.CONTAINS);
        return true;
    }

    private static Pageable pageable(int offset, int limit, Sort sort) {
        return PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
    }
}
