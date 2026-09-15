package fr.siamois.ui.api.openapi.v1.resource.container;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Contenant (boîte, caisse...) d'un projet")
public class ContainerResource {

    @Schema(description = "Type de ressource", example = "containers")
    private String resourceType = "containers";

    @Schema(description = "Identifiant technique (container_id)")
    private String id;

    @Schema(description = "Identifiant métier généré du contenant")
    private String identifier;

    @Schema(description = "Libellé d'affichage (identifiant)")
    private String label;
}
