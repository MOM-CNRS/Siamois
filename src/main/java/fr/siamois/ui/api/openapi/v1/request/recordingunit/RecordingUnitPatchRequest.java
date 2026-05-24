package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Mise à jour partielle : seules les entrées présentes dans {@code fieldAnswers} sont fusionnées.
 * Le formulaire effectif dépend du type d'UE et de l'institution (comme pour le détail GET).
 */
@Data
@Schema(description = "Mise à jour partielle d'une unité d'enregistrement (réponses formulaire)")
public class RecordingUnitPatchRequest {

    @Schema(
            description = "Révision attendue (valeur de syncRevision au moment du chargement client). "
                    + "Si absente, aucun contrôle de conflit (comportement legacy). "
                    + "Si présente et différente de la révision serveur → HTTP 409."
    )
    private Long expectedRevision;

    @Schema(description = "Valeurs par id de champ (string numérique)")
    private Map<String, Object> fieldAnswers = new HashMap<>();
}
