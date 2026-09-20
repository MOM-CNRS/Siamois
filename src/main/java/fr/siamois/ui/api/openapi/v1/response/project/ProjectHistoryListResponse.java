package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectHistoryEntryResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectHistoryListResponse extends ListResponse<ProjectHistoryEntryResource> {
    public ProjectHistoryListResponse(List<ProjectHistoryEntryResource> data, ListMeta meta) {
        super(data, meta);
    }
}
