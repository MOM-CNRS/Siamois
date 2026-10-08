package fr.siamois.ui.api.openapi.v1.response.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * catalog of an organization-wide list. No type entry: {@code data} is always empty, and
 * {@code fields} holds the table's system fields plus the union of the additional fields active in any of
 * the organization's projects.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class OrganizationFieldCatalogResponse extends Response<List<Object>> {

    @Schema(description = "Catalogue agrégé : champs système et champs additionnels de tous les projets, "
            + "indexé par identifiant custom_field (chaîne numérique)")
    private final Map<String, FieldResource> fields;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Défauts d'affichage des colonnes, quand la liste en a (unités d'enregistrement)")
    private final List<ProjectTableColumnResource> tableColumns;

    public OrganizationFieldCatalogResponse(Map<String, FieldResource> fields, List<ProjectTableColumnResource> tableColumns) {
        super(List.of());
        this.fields = fields;
        this.tableColumns = tableColumns;
    }
}
