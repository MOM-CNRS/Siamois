package fr.siamois.ui.api.openapi.v1.response.spatialunit;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Résultat d'autocomplétion d'unité spatiale (ex. pour {@code spatialContextSpatialUnitIds} sur PATCH projet).
 */
@Schema(description = "Lieu / unité spatiale résumé pour autocomplétion")
public record SpatialUnitAutocompleteItemApi(
        @Schema(description = "spatial_unit_id à utiliser dans le patch projet", example = "42")
        Long id,
        String name,
        @Nullable String code,
        @Nullable
        @Schema(description = "Identifiant métier du concept de catégorie (lieu), si renseigné")
        Long categoryConceptId,
        @Nullable
        @Schema(description = "Identifiant externe thésaurus de la catégorie, si présent")
        String categoryExternalId
) {
}
