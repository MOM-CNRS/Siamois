package fr.siamois.ui.api.openapi.v1.request.place;

import fr.siamois.dto.entity.FullAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Mise à jour partielle d'un lieu. Champs absents ou null = inchangés.")
public class PlacePatchRequest {

    @Schema(description = "Nom du lieu")
    private String name;

    @Schema(description = "Identifiant du concept de type (SIASU.TYPE)")
    private Long typeConceptId;

    @Schema(description = "Adresse postale ou géolocalisée")
    private FullAddress address;
}
