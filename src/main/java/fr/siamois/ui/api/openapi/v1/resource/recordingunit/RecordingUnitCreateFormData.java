package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Formulaire et vocabulaires pour la création d'une nouvelle UE (sans entité persistée).
 */
@Schema(description = "Formulaire de création d'unité d'enregistrement et listes de concepts par field_code")
public record RecordingUnitCreateFormData(

        @Schema(description = "Type d'UE (concept) demandé")
        ResolvedConceptResource recordingUnitType,

        @Schema(description = "Formulaire effectif ; absent si aucune configuration pour ce type / institution")
        FormResource form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, FieldResource> fields

        // ne pas retourner les concepts ici, utiliser les endpoints dediés
) {
}
