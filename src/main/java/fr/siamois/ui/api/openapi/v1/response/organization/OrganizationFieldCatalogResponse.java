package fr.siamois.ui.api.openapi.v1.response.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/organizations/{id}/{recording-unit|find|phase|container}-types} — the column
 * catalog of an organization-wide list. Same shape as the project catalogs
 * ({@code GET /api/v1/projects/{id}/…-types}) so the client reads both the same way, but no type
 * entry: {@code data} is always empty, and {@code _default.fields} holds the table's system fields
 * plus the union of the additional fields active in any of the organization's projects.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class OrganizationFieldCatalogResponse extends Response<List<Object>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Catalogue agrégé : champs système et champs additionnels de tous les projets")
    private final DefaultEntry defaultType;

    @Schema(description = "Même catalogue que _default.fields, indexé par identifiant custom_field (chaîne numérique)")
    private final Map<String, FieldResource> fields;

    public OrganizationFieldCatalogResponse(Map<String, FieldResource> fields, List<ProjectTableColumnResource> tableColumns) {
        super(List.of());
        this.defaultType = new DefaultEntry(fields, tableColumns);
        this.fields = fields;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DefaultEntry(
            @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
            Map<String, FieldResource> fields,
            @Schema(description = "Défauts d'affichage des colonnes, quand la liste en a (unités d'enregistrement)")
            List<ProjectTableColumnResource> tableColumns) {
    }
}
