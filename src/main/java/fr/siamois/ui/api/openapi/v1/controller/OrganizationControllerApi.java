package fr.siamois.ui.api.openapi.v1.controller;


import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitResponse;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organization", description = "API des organisation")
public class OrganizationControllerApi {

    private final RecordingUnitService recordingUnitService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;

    public OrganizationControllerApi(RecordingUnitService recordingUnitService, RecordingUnitResponseMapper recordingUnitResourceMapper) {
        this.recordingUnitService = recordingUnitService;
        this.recordingUnitResourceMapper = recordingUnitResourceMapper;
    }

    @Operation(summary = "Récupérer une unité d'enregistrement d'une organisation par son identifiant metier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "RecordingUnit trouvée"),
            @ApiResponse(responseCode = "404", description = "RecordingUnit non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units/{recordingUnitFullIdentifier}")
    public ResponseEntity<RecordingUnitResponse> getById(
            @PathVariable Long id,
            @Parameter(
                    description = "Optional list of counts to include. " +
                            "Only 'specimen' is allowed.",
                    schema = @Schema(
                            type = "array",
                            allowableValues = {"specimen"},
                            defaultValue = "specimen"
                    ),
                    in = ParameterIn.QUERY
            )
            @RequestParam(required = false)
            List<String> counts,
            @PathVariable String recordingUnitFullIdentifier) {

        RecordingUnitDTO recordingUnit =
                recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(
                        recordingUnitFullIdentifier,
                        id,
                        counts
                );

        return ResponseEntity.ok(
                new RecordingUnitResponse(
                        recordingUnitResourceMapper.convert(recordingUnit))
        );
    }

    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'une institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "404", description = "Institution non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units")
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {

        Page<RecordingUnitDTO> page =
                recordingUnitService.findByInstitutionId(id, limit, offset);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(recordingUnitResourceMapper::convert)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        RecordingUnitListResponse response =
                new RecordingUnitListResponse(resources, meta);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(response);
    }

}
