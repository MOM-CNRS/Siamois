package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The relations of one recording unit, grouped by where the other unit stands relative to it — what a
 * graph of the unit draws around it. The grouping is the contract: how each relation is stored
 * (hierarchy table, stratigraphic table, configurable links) stays behind it.
 */
@Schema(description = "Relations d'une UE regroupées par zone de graphe. Chaque zone est un aperçu "
        + "(values, total, complete) borné par le serveur ; les références stratigraphiques portent leur qualificatif.")
public record RecordingUnitRelationGraphResource(
        @Schema(description = "L'UE dont on dessine les relations")
        ResourceRef center,

        @Schema(description = "UE parentes (hiérarchie)")
        MultiValue parents,

        @Schema(description = "UE enfants (hiérarchie)")
        MultiValue children,

        @Schema(description = "UE antérieures à l'UE : plus anciennes, l'UE leur est postérieure")
        MultiValue anterior,

        @Schema(description = "UE synchrones de l'UE")
        MultiValue synchronous,

        @Schema(description = "UE postérieures à l'UE : plus récentes, l'UE leur est antérieure")
        MultiValue posterior,

        @Schema(description = "Autres relations (relations configurables) ; toujours vide tant qu'elles n'existent pas")
        MultiValue others
) {
}
