package fr.siamois.ui.api.openapi.v1.response.project.type;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectFieldConfigResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/organizations/{id}/project-types}: the project's form, in one call — layout, field
 * configs and the shared field catalog. Project is not plugged into the per-type machinery yet, so the
 * form sits at the root rather than in a type of {@code data} (which stays empty).
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectTypeListResponse extends Response<List<ProjectType>> {

    @Schema(description = "Layout du formulaire du projet")
    private final FormResource form;

    @Schema(description = "Configuration des champs, référence dans le catalogue fields")
    private final List<ProjectFieldConfigResource> fieldConfigs;

    @Schema(description = "Défauts d'affichage (visibilité, ordre) des colonnes de la liste des projets, "
            + "hors colonnes structurelles (identifiant, nom, compteur d'unités d'enregistrement)")
    private final List<ProjectTableColumnResource> tableColumns;

    @Schema(description = "Catalogue des champs partagés, indexé par identifiant custom_field (chaîne numérique)")
    private final Map<String, FieldResource> fields;

    public ProjectTypeListResponse(List<ProjectType> data, FormResource form, List<ProjectFieldConfigResource> fieldConfigs,
                                   List<ProjectTableColumnResource> tableColumns, Map<String, FieldResource> fields) {
        super(data);
        this.form = form;
        this.fieldConfigs = fieldConfigs;
        this.tableColumns = tableColumns;
        this.fields = fields;
    }
}
