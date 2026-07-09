package fr.siamois.ui.api.openapi.v1.resource.document;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Valeurs d'un document existant (pré-remplissage édition). Absent en création pure.
 */
@Schema(description = "Valeurs courantes du document pour pré-remplissage (édition)")
public record DocumentFormCurrentValuesApi(

        @Schema(nullable = true)
        String title,

        @Schema(nullable = true)
        String description,

        @Schema(nullable = true)
        ConceptAutocompleteDTO nature,

        @Schema(nullable = true)
        ConceptAutocompleteDTO scale,

        @Schema(nullable = true)
        ConceptAutocompleteDTO format,

        @Schema(nullable = true)
        String fileName,

        @Schema(nullable = true)
        String mimeType
) {
}
