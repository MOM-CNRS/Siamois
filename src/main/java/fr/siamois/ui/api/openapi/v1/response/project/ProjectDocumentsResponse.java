package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectDocumentsData;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ProjectDocumentsResponse extends Response<ProjectDocumentsData> {

    public ProjectDocumentsResponse(ProjectDocumentsData data) {
        super(data);
    }
}
