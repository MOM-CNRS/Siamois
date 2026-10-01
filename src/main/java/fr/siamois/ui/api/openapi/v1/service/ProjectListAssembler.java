package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One page of accessible projects → the {@code GET /projects} list response: permissions and
 * bookmarks batched once per page, answers projection in one batch. For the scoped project lists
 * (a place's projects) that must answer exactly like the main list.
 */
@Component
@RequiredArgsConstructor
public class ProjectListAssembler {

    private final ProjectApiService projectApiService;
    private final ProjectResponseMapper projectResponseMapper;
    private final ProjectListProjectionService projectListProjectionService;

    public ProjectListResponse assemble(ProjectApiCaller caller, Page<AccessibleProjectForApi> rows,
                                        String fields, String lang, int limit, int offset) {
        Map<Long, ProjectResourcePermissions> permissionsByActionUnitId =
                projectApiService.permissionsFor(caller, rows.getContent());
        Set<String> bookmarkedUris = projectApiService.bookmarkedResourceUris(caller, rows.getContent(), lang);
        ProjectListProjectionService.ProjectListProjection projection =
                projectListProjectionService.build(rows.getContent(), fields, lang);

        List<ProjectResource> resources = rows.getContent().stream()
                .map(row -> {
                    Long id = row.actionUnit().getId();
                    ProjectResourcePermissions permissions = permissionsByActionUnitId.getOrDefault(
                            id, ProjectResourcePermissions.of(false));
                    boolean bookmarked = bookmarkedUris.contains(ProjectApiService.actionUnitResourceUri(id));
                    return projectResponseMapper.toResource(row, lang, permissions, bookmarked,
                            projection.resolvedLabels(), projection.answersFor(id));
                })
                .toList();
        return new ProjectListResponse(resources, new ListMeta(rows.getTotalElements(), limit, (long) offset));
    }
}
