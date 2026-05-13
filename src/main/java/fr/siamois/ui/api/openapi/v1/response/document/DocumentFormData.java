package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Formulaire document pour clients API : champs + listes de concepts par field_code.
 */
@Schema(description = "Formulaire création / édition de document et vocabulaires contrôlés")
public record DocumentFormData(

        @Schema(description = "Champs du formulaire dans l'ordre d'affichage recommandé")
        List<DocumentFormFieldApi> fields,

        @Schema(description = "Options de thésaurus par code de champ (SIAD.NATURE, SIAD.SCALE, SIAD.FORMAT)")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode,

        @Schema(description = "Présent uniquement si documentId a été fourni et le document est accessible", nullable = true)
        DocumentFormCurrentValuesApi currentValues
) {
}
