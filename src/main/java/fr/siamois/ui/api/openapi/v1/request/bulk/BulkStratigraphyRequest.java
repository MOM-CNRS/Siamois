package fr.siamois.ui.api.openapi.v1.request.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Corps d'un ajout (ou d'un retrait) en lot d'une relation stratigraphique entre chaque élément et une même UE.
 */
@Data
@Schema(description = "Ajoute à chaque UE une relation stratigraphique avec une même UE, ou retire cette relation de chacune")
public class BulkStratigraphyRequest {

    @Schema(description = "Identifiants numériques des UE", example = "[12, 13, 14]")
    private List<Long> ids = new ArrayList<>();

    @Schema(description = "Identifiant numérique de l'autre UE", example = "42")
    private Long relatedRecordingUnitId;

    @Schema(description = "LINK : où se tient l'autre UE par rapport à chaque UE (anterior, posterior, synchronous)",
            allowableValues = {"anterior", "posterior", "synchronous"})
    private String position;

    @Schema(description = "LINK : concept du type de relation (vocabulaire SIARU.STRATI) ; facultatif")
    private Long typeConceptId;

    @Schema(description = "LINK : relation incertaine", defaultValue = "false")
    private Boolean uncertain;

    @Schema(description = "LINK : ajouter la relation ; UNLINK : la retirer (les UE ne sont pas supprimées)")
    private BulkLinkMode mode;
}
