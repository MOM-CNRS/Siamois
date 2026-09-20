package fr.siamois.ui.api.openapi.v1.resource.project;

import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The {@code _default} project type (plan §6) — no concept-typed type exists for Project yet
 * (unlike RU's {@code RecordingUnitType}, which is keyed by a type concept), so this carries only
 * the form/layout and field configs, no id/label. Phase 1 populates only this; real entries in
 * {@code ProjectTypeListResponse.data} come later, once Project is plugged into the
 * {@code ConfigurableTable}/{@code FieldFormConfig} machinery.
 */
@Data
@NoArgsConstructor
public class ProjectDefaultType {
    @Schema(description = "Layout du formulaire (form.layoutJson)")
    private FormResource form;
    @Schema(description = "Configuration des champs pour ce type, référence dans le catalogue fields")
    private List<ProjectFieldConfigResource> fieldConfigs;

    public ProjectDefaultType(FormResource form, List<ProjectFieldConfigResource> fieldConfigs) {
        this.form = form;
        this.fieldConfigs = fieldConfigs;
    }
}
