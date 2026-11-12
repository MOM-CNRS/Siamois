package fr.siamois.ui.api.openapi.v1.response.bulk;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Le bilan d'une action en lot : chaque élément est traité indépendamment des autres.
 *
 * @param succeeded identifiants des éléments traités
 * @param failed    éléments en échec, avec leur motif
 */
@Schema(description = "Bilan d'une action en lot (chaque élément est traité dans sa propre transaction)")
public record BulkResultResource(
        @Schema(description = "Identifiants des éléments traités") List<Long> succeeded,
        @Schema(description = "Éléments en échec") List<BulkFailureResource> failed) {
}
