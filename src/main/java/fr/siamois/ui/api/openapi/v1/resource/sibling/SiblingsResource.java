package fr.siamois.ui.api.openapi.v1.resource.sibling;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The "fiche précédente/suivante" pair. Loops at the ends (the next of the last is the first); a
 * side is {@code null} only when there is no other entity in the scope at all.
 */
public record SiblingsResource(
        @Schema(description = "La fiche précédente, ou null s'il n'y en a pas d'autre")
        SiblingResource previous,
        @Schema(description = "La fiche suivante, ou null s'il n'y en a pas d'autre")
        SiblingResource next
) {
}
