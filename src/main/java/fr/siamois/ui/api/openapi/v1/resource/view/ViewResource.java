package fr.siamois.ui.api.openapi.v1.resource.view;

import fr.siamois.dto.view.TableViewState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Vue de liste")
public record ViewResource(
        @Schema(description = "Identifiant de la vue") Long id,
        @Schema(description = "Type d'entité (clé du registre front)") String resourceType,
        @Schema(description = "Titre") String title,
        @Schema(description = "USER ou DEFAULT", allowableValues = {"USER", "DEFAULT"}) String kind,
        @Schema(description = "Type de la fiche parente, pour une liste liée à une fiche") String scopeEntityType,
        @Schema(description = "Projet dont c'est la vue par défaut") Long projectId,
        @Schema(description = "Type d'enregistrement dont c'est la vue par défaut") Long typeId,
        @Schema(description = "État de la liste") TableViewState state,
        @Schema(description = "Identifiant du propriétaire (absent pour une vue par défaut)") Long ownerId,
        @Schema(description = "Nom du propriétaire") String ownerName,
        @Schema(description = "L'utilisateur courant peut modifier et supprimer la vue") boolean canEdit,
        Instant createdAt,
        Instant updatedAt
) {
}
