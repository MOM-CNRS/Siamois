package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A reference to a recording unit that also carries its display label — what a list row needs to
 * show (and link to) its parent UE without a second request. Additive over
 * {@link RecordingUnitResourceIdentifier}: {@code resourceType}/{@code id} are unchanged.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RecordingUnitReference extends RecordingUnitResourceIdentifier {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Identifiant complet de l'UE", example = "OA-2024-US12")
    private String fullIdentifier;
}
