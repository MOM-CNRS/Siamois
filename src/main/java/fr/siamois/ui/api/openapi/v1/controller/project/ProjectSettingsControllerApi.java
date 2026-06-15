package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.mapper.IdentifierConfigMapper;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectRecordingUnitTypeListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static fr.siamois.ui.api.openapi.v1.OpenApiTags.MOBILE_APP;
import static fr.siamois.ui.api.openapi.v1.OpenApiTags.PROJECT_CONFIG;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = OpenApiTags.PROJECT)
@Tag(name = PROJECT_CONFIG)
@Tag(name= MOBILE_APP)
@RequiredArgsConstructor
public class ProjectSettingsControllerApi {

    private final ProjectApiService projectApiService;
    private final IdentifierConfigMapper identifierConfigMapper;


    @GetMapping("/{id}/recording-unit-types")
    @Operation(summary = "Récupère tout les types d'UE d'un projet et leurs configurations (formulaires, settings, ..)",
            description = "")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    // TODO : implement this only when the types configurations are designed and implemented.
    //  use default endpoint below for now.
    public ResponseEntity<ProjectRecordingUnitTypeListResponse> getProjectRecordingUnitSettings(
            @PathVariable("id") String id,
            @PathVariable("typeId") String typeId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        // todo : get form + identifier config for each type, including type _default
        // form : get form for each type
        // identifier config: get action unit config, same for each type in this version
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }


}
