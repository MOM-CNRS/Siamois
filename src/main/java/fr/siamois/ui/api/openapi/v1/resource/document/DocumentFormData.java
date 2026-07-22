package fr.siamois.ui.api.openapi.v1.resource.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Formulaire document pour clients API : définition des champs et valeurs courantes optionnelles.
 */
@Schema(description = "Formulaire création / édition de document")
public record DocumentFormData(

        @Schema(description = "Champs du formulaire dans l'ordre d'affichage recommandé")
        List<DocumentFormFieldApi> fields,

        @Schema(description = "Présent uniquement si documentId a été fourni et le document est accessible", nullable = true)
        DocumentFormCurrentValuesApi currentValues
) {
}
