package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Résultat d'une duplication de structure")
public record RecordingUnitDuplicationResource(
        @Schema(description = "Les copies de l'UE elle-même, une par exemplaire, dans l'ordre") List<Copy> copies,
        @Schema(description = "Nombre total d'UE créées, descendants compris") int createdCount) {

    @Schema(description = "Une copie de l'UE racine")
    public record Copy(Long id, String label) {
    }
}
