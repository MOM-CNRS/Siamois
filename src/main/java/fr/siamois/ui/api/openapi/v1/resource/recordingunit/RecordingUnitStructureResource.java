package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Une UE et ses descendants (hiérarchie parent → enfant), à plat, du plus proche au plus lointain")
public record RecordingUnitStructureResource(
        @Schema(description = "L'UE racine") Node root,
        @Schema(description = "Ses descendants, un parent avant ses enfants") List<Node> descendants,
        @Schema(description = "Vrai si l'arbre a été coupé à la limite de nœuds") boolean truncated) {

    @Schema(description = "Un nœud de la structure")
    public record Node(Long id, String label, Long parentId) {
    }
}
