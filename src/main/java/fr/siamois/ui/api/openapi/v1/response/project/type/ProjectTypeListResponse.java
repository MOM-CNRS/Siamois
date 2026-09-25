package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/organizations/{id}/project-types} (plan §5/§6) — replaces the removed
 * {@code GET /api/v1/projects/form}: one call now carries layout, fields and field configs
 * together, modeled on {@code GET /api/v1/projects/{id}/recording-unit-types}'s {@code data[]} +
 * sibling {@code _default} shape, but organization-scoped (project types are org-level, not
 * project-level) and with a root-level shared {@code fields} catalog alongside {@code _default}
 * (RU's shape keeps fields nested per-type instead — Project's fields are genuinely shared/reused
 * across its one type today, so hoisting them to the root avoids duplicating the catalog once a
 * second real type exists).
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectTypeListResponse extends Response<List<ProjectType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type par défaut (formulaire et field configs), sans concept associé.")
    private final ProjectDefaultType defaultType;

    @Schema(description = "Catalogue des champs partagés, indexé par identifiant custom_field (chaîne numérique)")
    private final Map<String, FieldResource> fields;

    public ProjectTypeListResponse(List<ProjectType> data, ProjectDefaultType defaultType, Map<String, FieldResource> fields) {
        super(data);
        this.defaultType = defaultType;
        this.fields = fields;
    }
}
