package fr.siamois.ui.api.openapi.v1;


import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.api.dto.RecordingUnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recording-units")
@Tag(name = "Recording Units", description = "API de gestion des Recording Units")
public class RecordingUnitControllerApi {

    private final RecordingUnitService recordingUnitService;

    public RecordingUnitControllerApi(RecordingUnitService recordingUnitService) {
        this.recordingUnitService = recordingUnitService;
    }

    @Operation(summary = "Récupérer une RecordingUnit par son fullIdentifier et institutionIdentifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "RecordingUnit trouvée"),
            @ApiResponse(responseCode = "404", description = "RecordingUnit non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{institutionIdentifier}/{fullIdentifier}")
    public ResponseEntity<RecordingUnitResponse> getById(
            @PathVariable Long institutionIdentifier,
            @PathVariable String fullIdentifier) {

        RecordingUnit recordingUnit =
                recordingUnitService.findByFullIdentifierAndInstitutionId(
                        fullIdentifier,
                        institutionIdentifier
                );
        return ResponseEntity.ok(toDto(recordingUnit));
    }

    // Mapper placé ici
    private RecordingUnitResponse toDto(RecordingUnit ru) {
        return RecordingUnitResponse.builder()
                .id(ru.getId())                      // recording_unit_id
                .identifier(ru.getIdentifier())      // identifier correct
                .fullIdentifier(ru.getFullIdentifier())
                .description(ru.getDescription())
                .openingDate(ru.getOpeningDate())      // start_date
                .closingDate(ru.getClosingDate())        // end_date
                .matrixComposition(ru.getMatrixComposition())
                .matrixColor(ru.getMatrixColor())
                .matrixTexture(ru.getMatrixTexture())
                .erosionShape(ru.getErosionShape())
                .erosionOrientation(ru.getErosionOrientation())
                .erosionProfile(ru.getErosionProfile())
                .taq(ru.getTaq())
                .tpq(ru.getTpq())
                .build();
    }
}
