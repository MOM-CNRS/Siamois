package fr.siamois.ui.api.openapi.v1.response.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Résultat de la recherche globale : une section par type d'entité ayant au moins une correspondance.
 */
@Schema(description = "Résultats de la recherche globale, groupés par type d'entité")
public record GlobalSearchResponse(List<Section> sections) {

    @Schema(description = "Correspondances d'un type d'entité")
    public record Section(
            @Schema(description = "project | recordingUnit | find | place") String type,
            @Schema(description = "Nombre total de correspondances (au-delà des items renvoyés)") long total,
            List<Item> items) {
    }

    @Schema(description = "Une correspondance")
    public record Item(Long id, String label, String subtitle, Long projectId) {
    }
}
