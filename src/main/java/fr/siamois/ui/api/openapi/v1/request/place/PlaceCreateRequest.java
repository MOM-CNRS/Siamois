package fr.siamois.ui.api.openapi.v1.request.place;

import fr.siamois.dto.entity.FullAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Création d'un lieu (unité spatiale)")
public class PlaceCreateRequest {

    @Schema(description = "Institution propriétaire (doit être dans le périmètre JWT).", example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "Nom du lieu", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Identifiant du concept de type (SIASU.TYPE).", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long typeConceptId;

    @Schema(description = "Adresse postale ou géolocalisée (optionnel)")
    private FullAddress address;
}
