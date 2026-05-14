package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitTypeOptionsData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitTypeOptionsResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitTypeOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recording-unit-types")
@Tag(name = OpenApiTags.RECORDING_UNIT, description = "Types d'unité d'enregistrement")
@RequiredArgsConstructor
public class RecordingUnitTypeSearchControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitTypeOpenApiService recordingUnitTypeOpenApiService;

    @GetMapping
    @Operation(
            summary = "Types d'unité d'enregistrement possibles",
            description = "Retourne les concepts du vocabulaire contrôlé `" + RecordingUnit.TYPE_FIELD_CODE + "` "
                    + "configurés pour l'organisation (même source que l'autocomplete web). "
                    + "Paramètre `q` optionnel pour filtrer sur le libellé. "
                    + "Pour créer une UE : `recordingUnitTypeConceptId` = identifiant du concept (`types[].conceptLabelToDisplay.concept.id`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok (liste vide si pas de config vocabulaire pour ce champ)"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitTypeOptionsResponse> listTypes(
            @Parameter(description = "Institution (doit être dans le périmètre JWT).", example = "10", required = true)
            @RequestParam long organizationId,
            @Parameter(description = "Filtre optionnel sur le libellé (autocomplete).")
            @RequestParam(required = false) String q,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var types = recordingUnitTypeOpenApiService.listRecordingUnitTypes(
                organizationId, caller.person(), lang, q);
        RecordingUnitTypeOptionsData data = new RecordingUnitTypeOptionsData(RecordingUnit.TYPE_FIELD_CODE, types);
        return ResponseEntity.ok(new RecordingUnitTypeOptionsResponse(data));
    }
}
