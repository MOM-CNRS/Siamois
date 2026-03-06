package fr.siamois.ui.api.openapi.v1.controller;


import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.dto.RecordingUnitResponse;
import fr.siamois.ui.api.openapi.v1.jsonapi.JsonApiResponse;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResourceMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

import static fr.siamois.ui.api.openapi.v1.jsonapi.JsonApiResponse.wrap;

@RestController
@RequestMapping("/api/organization")
@Tag(name = "Organization", description = "API des organisation")
public class OrganizationControllerApi {

    private final RecordingUnitService recordingUnitService;
    private final RecordingUnitResourceMapper recordingUnitResourceMapper;

    public OrganizationControllerApi(RecordingUnitService recordingUnitService, RecordingUnitResourceMapper recordingUnitResourceMapper) {
        this.recordingUnitService = recordingUnitService;
        this.recordingUnitResourceMapper = recordingUnitResourceMapper;
    }

    @Operation(summary = "Récupérer une unité d'enregistrement d'une organisation par son identifiant metier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "RecordingUnit trouvée"),
            @ApiResponse(responseCode = "404", description = "RecordingUnit non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-unit/{recordingUnitFullIdentifier}")
    public ResponseEntity<JsonApiResponse<RecordingUnitResource>> getById(
            @PathVariable Long id,
            @PathVariable String recordingUnitFullIdentifier) {

        RecordingUnitDTO recordingUnit =
                recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(
                        recordingUnitFullIdentifier,
                        id
                );

        return ResponseEntity.ok(
                wrap(
                        recordingUnitResourceMapper.convert(recordingUnit))
        );
    }

}
