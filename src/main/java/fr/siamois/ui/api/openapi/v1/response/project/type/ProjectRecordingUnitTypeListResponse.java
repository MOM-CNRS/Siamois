package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectRecordingUnitTypeListResponse extends Response<List<RecordingUnitType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type par défaut (formulaire et identifiant) sans concept associé.")
    private final RecordingUnitDefaultType defaultType;

    // Union of _default's own fields and every configured type's fields (per-type overrides win
    // on a shared field id) — the flat catalog a mixed-type table needs for its column toggler,
    // without the caller having to walk every RecordingUnitType.getFields() itself. The per-type
    // nested `fields` stay in place: the create/edit form for one specific type still reads them
    // from there.
    @Schema(description = "Catalogue de champs, union de _default.fields et des fields de chaque type " +
            "(un champ redéfini par un type l'emporte sur sa version par défaut) — ce que le sélecteur " +
            "de colonnes React consomme pour une table mêlant plusieurs types d'UE.")
    private final Map<String, FieldResource> fields;

    public ProjectRecordingUnitTypeListResponse(List<RecordingUnitType> data, RecordingUnitDefaultType defaultType,
                                                 Map<String, FieldResource> fields) {
        super(data);
        this.defaultType = defaultType;
        this.fields = fields;
    }
}
