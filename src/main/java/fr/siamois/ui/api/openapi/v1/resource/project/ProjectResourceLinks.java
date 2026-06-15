package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProjectResourceLinks {

    @Schema(description = "URL du projet")
    private String self;

    @Schema(description = "URL des unités d'enregistrement du projet")
    private String recordingUnits;

    @Schema(description = "URL des sous-projets")
    private String children;

    @Schema(description = "URL du formulaire de création d'une unité d'enregistrement pour ce projet")
    private String creationForm;

    public static ProjectResourceLinks of(String projectId) {
        String base = "/projects/" + projectId;
        return new ProjectResourceLinks(
                base,
                base + "/recording-units",
                base + "/children",
                base + "/recording-units/creation-form"
        );
    }
}
