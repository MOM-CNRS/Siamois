package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Champs ajoutés à la volée à la fiche d'un enregistrement, et le panneau de chacun")
public record OnTheFlyFieldsResponse(
        @Schema(description = "Définition de chaque champ, indexée par id") Map<String, FieldResource> fields,
        @Schema(description = "Où chaque champ s'affiche, dans l'ordre d'ajout") List<Placement> placements) {

    @Schema(description = "Un champ et le panneau où il s'affiche (null : le dernier panneau)")
    public record Placement(String fieldId, String panel) {
    }
}
