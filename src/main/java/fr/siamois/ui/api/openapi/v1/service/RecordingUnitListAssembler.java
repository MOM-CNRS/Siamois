package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns one page of recording units that all belong to the same project into the list response
 * every scoped RU list shares (a recording unit's children, a phase's recording units): answers
 * projection in one batch, one {@code _permissions} for the page (same project), one bookmark query.
 * Same output as {@code GET /projects/{id}/recording-units}.
 */
@Component
@RequiredArgsConstructor
public class RecordingUnitListAssembler {

    private final ProjectApiService projectApiService;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;
    private final RecordingUnitListProjectionService recordingUnitListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;

    public RecordingUnitListResponse assemble(ProjectApiCaller caller, Page<RecordingUnitDTO> page, Long projectId,
                                              String fields, String lang, int limit, int offset) {
        boolean canEdit = projectId != null
                && projectApiService.canEditRecordingUnitsForProject(caller, String.valueOf(projectId), lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(canEdit);
        RecordingUnitListProjectionService.RecordingUnitListProjection projection =
                recordingUnitListProjectionService.build(page.getContent(), fields, lang);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(dto -> {
                    RecordingUnitResource resource = recordingUnitResponseMapper.convert(dto);
                    resource.setAnswers(projection.answersFor(dto.getId()));
                    resource.setPermissions(permissions);
                    return resource;
                })
                .toList();
        if (!page.isEmpty()) {
            resourceBookmarkService.markBookmarked(caller.person(), page.getContent().get(0).getCreatedByInstitution(), resources, lang);
        }
        return new RecordingUnitListResponse(resources, new ListMeta(page.getTotalElements(), limit, (long) offset));
    }
}
