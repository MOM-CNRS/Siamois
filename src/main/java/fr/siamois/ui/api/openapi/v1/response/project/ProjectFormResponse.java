package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectFormData;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ProjectFormResponse extends Response<ProjectFormData> {

    public ProjectFormResponse(ProjectFormData data) {
        super(data);
    }
}
