package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import io.swagger.v3.oas.annotations.media.Schema;

/** One relation of a recording unit: the unit it is with, and where that unit stands relative to this one. */
@Schema(description = "Une relation d'une UE avec une autre")
public record RecordingUnitRelationRow(
        @Schema(description = "Où l'autre UE se tient par rapport à l'UE", allowableValues = {"parents", "children", "anterior", "posterior", "synchronous"})
        String kind,

        @Schema(description = "L'autre UE ; une relation stratigraphique porte son qualificatif (type, incertitude)")
        ResourceRef unit
) {
}
