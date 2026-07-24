package fr.siamois.ui.api.openapi.v1.resource.find;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Gabarit UI pour la création ou la modification d'un mobilier (métadonnées des champs, sans valeurs)
 */
@Schema(description = "Formulaire de création/modification d'un mobilier")
public record FindCreateFormData(

        @Schema(description = "Type de mobilier (concept) demandé")
        ResolvedConceptResource findType,

        @Schema(description = "Formulaire effectif ; absent si aucune configuration pour ce type / organisation")
        FormResource form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, FieldResource> fields
) {
}
