package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Formulaire effectif pour le type d'UE (layout JSON aligné sur {@code custom_form.layout}).
 */
@Schema(description = "Formulaire personnalisé pour le type d'unité d'enregistrement")
public record FormResource(

        @Schema(description = "Resource type",
                example = "forms",
                allowableValues = {"forms"})
        String resourceType,
        @Schema(description = "Identifiant du formulaire (custom_form.form_id)")
        Long resourceId,
        String name,
        String description,
        @Schema(description = "Layout JSON (sections / lignes / colonnes / fieldId), sérialisé comme en base")
        String layoutJson
) {
    public FormResource(Long resourceId, String name, String description, String layoutJson) {
        this("forms", resourceId, name, description, layoutJson);
    }
}
