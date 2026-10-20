package fr.siamois.ui.api.openapi.v1.resource.view;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Configuration des onglets d'une fiche, et droit de la modifier")
public record FicheConfigResponse(
        @Schema(description = "Les onglets configurés, absents tant que personne n'a configuré cette fiche")
        Map<String, Object> tabs,
        @Schema(description = "Sous-type pour lequel la configuration a été enregistrée ; absent pour tous les sous-types")
        Long typeId,
        @Schema(description = "Dernière modification") Instant updatedAt,
        @Schema(description = "L'utilisateur courant peut enregistrer la configuration") boolean canEdit
) {
}
