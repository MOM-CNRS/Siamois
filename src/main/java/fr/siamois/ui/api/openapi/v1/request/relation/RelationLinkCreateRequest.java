package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Création d'un lien entre deux entités pour une relation configurable")
public class RelationLinkCreateRequest {

    @Schema(description = "Code de la relation", example = "SIAMOIS.RELATION.ORDER")
    private String relation;

    @Schema(description = "Extrémité source : l'entité dont la fiche porte le champ où le lien est saisi (pour l'ordre "
            + "stratigraphique, l'unité postérieure)")
    private RelationEndRequest source;

    @Schema(description = "Extrémité cible (pour l'ordre stratigraphique, l'unité antérieure)")
    private RelationEndRequest target;

    @Schema(description = "Type du lien vu du côté source (identifiant de concept), facultatif. S'il est donné et que la "
            + "relation a une correspondance de types, le type du côté cible en est déduit", example = "20")
    private Long sourceType;

    @Schema(description = "Type du lien vu du côté cible (identifiant de concept), facultatif", example = "21")
    private Long targetType;

    @Schema(description = "Extrémité depuis laquelle le lien est saisi. La cible est le miroir de la relation : en "
            + "lecture seule, sauf pour une relation symétrique ou dont le miroir est éditable",
            allowableValues = {"source", "target"}, defaultValue = "source")
    private String editedFrom;

    @Schema(description = "Valeurs des attributs du lien (formulaire du lien), clés = identifiants de champ en chaîne",
            example = "{\"-900\": true}")
    private Map<String, Object> attributes;
}
