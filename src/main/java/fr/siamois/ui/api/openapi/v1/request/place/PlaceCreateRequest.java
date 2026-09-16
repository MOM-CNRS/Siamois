package fr.siamois.ui.api.openapi.v1.request.place;

import fr.siamois.dto.entity.FullAddress;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
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

    @Schema(description = "Numéro de regroupement du lieu (optionnel, non unique)")
    private Integer placeNumber;

    @Schema(description = "Géométrie du lieu (GeoJSON), dans le SRID fourni ; aucune reprojection n'est effectuée")
    private GeometryDTO geom;
}
