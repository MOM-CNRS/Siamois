package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Mise à jour d'un lien : ses types et/ou ses attributs")
public class RelationLinkUpdateRequest {

    @Schema(description = "Vrai pour remplacer les types du lien par sourceType / targetType (un type absent est alors "
            + "effacé, ou déduit de la correspondance de la relation)", defaultValue = "false")
    private boolean changeTypes;

    @Schema(description = "Nouveau type vu du côté source (identifiant de concept)", example = "20")
    private Long sourceType;

    @Schema(description = "Nouveau type vu du côté cible (identifiant de concept)", example = "21")
    private Long targetType;

    @Schema(description = "Extrémité depuis laquelle le lien est modifié", allowableValues = {"source", "target"},
            defaultValue = "source")
    private String editedFrom;

    @Schema(description = "Attributs à écrire, clés = identifiants de champ en chaîne ; une valeur null efface la réponse",
            example = "{\"-900\": false}")
    private Map<String, Object> attributes;
}
