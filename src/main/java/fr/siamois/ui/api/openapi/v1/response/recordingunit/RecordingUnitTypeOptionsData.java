package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Types d'unité d'enregistrement autorisés pour une organisation (vocabulaire SIARU.TYPE)")
public record RecordingUnitTypeOptionsData(
        @Schema(description = "Code métier du champ « type d'UE »", example = "SIARU.TYPE")
        String fieldCode,
        @Schema(description = "Concepts avec libellés ; utiliser conceptLabelToDisplay.concept.id pour recordingUnitTypeConceptId")
        List<ConceptAutocompleteDTO> types
) {
}
