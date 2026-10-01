package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectSiblingsResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectSiblingsResponse extends Response<ProjectSiblingsResource> {
    public ProjectSiblingsResponse(ProjectSiblingsResource data) {
        super(data);
    }
}
