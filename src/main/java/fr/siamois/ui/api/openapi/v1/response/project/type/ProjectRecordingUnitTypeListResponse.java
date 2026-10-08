package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/projects/{id}/recording-unit-types}: the project's types of recording unit, each with
 * its own form, plus the flat catalog of their fields and the default columns of the list.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectRecordingUnitTypeListResponse extends Response<List<RecordingUnitType>> {

    // Union of every configured type's fields (the last type wins on a shared field id) — the flat catalog a
    // mixed-type table needs for its column toggler, without the caller walking every
    // RecordingUnitType.getFields() itself. The per-type nested `fields` stay in place: the create/edit form
    // for one specific type still reads them from there.
    @Schema(description = "Catalogue de champs, union des fields de chaque type — ce que le sélecteur de colonnes "
            + "React consomme pour une table mêlant plusieurs types d'UE.")
    private final Map<String, FieldResource> fields;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Défauts d'affichage des colonnes de la liste des unités d'enregistrement "
            + "(RecordingUnitTableColumnDefaults, source unique de vérité partagée avec la table JSF)")
    private final List<ProjectTableColumnResource> tableColumns;

    public ProjectRecordingUnitTypeListResponse(List<RecordingUnitType> data, Map<String, FieldResource> fields,
                                                 List<ProjectTableColumnResource> tableColumns) {
        super(data);
        this.fields = fields;
        this.tableColumns = tableColumns;
    }
}
