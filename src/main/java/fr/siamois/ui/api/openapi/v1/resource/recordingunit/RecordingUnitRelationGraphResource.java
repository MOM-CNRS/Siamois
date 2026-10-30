package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The relations of one recording unit, grouped by where the other unit stands relative to it — what a
 * graph of the unit draws around it. The grouping is the contract: how each relation is stored
 * (hierarchy table, stratigraphic table, configurable links) stays behind it.
 */
@Schema(description = "Relations d'une UE regroupées par zone de graphe. Chaque zone est un aperçu "
        + "(values, total, complete) borné par le serveur ; les références stratigraphiques portent leur qualificatif.")
public record RecordingUnitRelationGraphResource(
        @Schema(description = "L'UE dont on dessine les relations")
        ResourceRef center,

        @Schema(description = "UE parentes (hiérarchie)")
        MultiValue parents,

        @Schema(description = "UE enfants (hiérarchie)")
        MultiValue children,

        @Schema(description = "UE antérieures à l'UE : plus anciennes, l'UE leur est postérieure")
        MultiValue anterior,

        @Schema(description = "UE synchrones de l'UE")
        MultiValue synchronous,

        @Schema(description = "UE postérieures à l'UE : plus récentes, l'UE leur est antérieure")
        MultiValue posterior,

        @Schema(description = "Autres relations (relations configurables) ; toujours vide tant qu'elles n'existent pas")
        MultiValue others,

        @Schema(description = "Champ de la fiche qui porte chaque zone stratigraphique : c'est lui qui donne la liste "
                + "des types de relation (GET /organizations/{id}/concepts?fieldId=)")
        Fields fields,

        @Schema(description = "Relations entre deux UE affichées du graphe (hors le centre), seulement avec full=true ; "
                + "vide sinon. Une relation va de l'UE la plus récente (ou du parent) vers la plus ancienne (ou l'enfant).")
        List<Link> links
) {

    /** A relation between two units of the graph, neither of them the center. */
    public record Link(
            @Schema(description = "UE d'où part la relation") String from,
            @Schema(description = "UE où elle arrive") String to,
            @Schema(description = "hierarchy, stratigraphy ou synchronous", allowableValues = {"hierarchy", "stratigraphy", "synchronous"}) String kind,
            @Schema(description = "Type de la relation stratigraphique, s'il y en a un") ResourceRef type,
            @Schema(description = "Relation incertaine") Boolean uncertain) {
    }

    /** The ids of the fields that hold the stratigraphic zones, as strings like every field id of the API. */
    public record Fields(
            @Schema(description = "Champ des UE antérieures") String anterior,
            @Schema(description = "Champ des UE synchrones") String synchronous,
            @Schema(description = "Champ des UE postérieures") String posterior) {
    }
}
