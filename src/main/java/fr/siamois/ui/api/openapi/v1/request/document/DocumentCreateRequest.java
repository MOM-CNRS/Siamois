package fr.siamois.ui.api.openapi.v1.request.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Création d'un document (sans fichier) dans un projet. Même forme minimale que
 * {@code PhaseCreateRequest} : le projet, la catégorie, et le titre ; le reste se renseigne sur la fiche.
 */
@Data
@Schema(description = "Création d'un document")
public class DocumentCreateRequest {

    @Schema(
            description = "Clé du projet (unité d'action) de rattachement : identifiant numérique (action_unit_id), "
                    + "full_identifier ou identifiant court dans une organisation accessible.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String projectId;

    @Schema(description = "Identifiant du concept de catégorie (concept_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryId;

    @Schema(description = "Titre du document")
    private String title;
}
