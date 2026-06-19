package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectFindTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectRecordingUnitTypeListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = MOBILE_APP)
@RequiredArgsConstructor
public class ProjectSettingsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;

    @GetMapping("/{id}/recording-unit-types")
    @Operation(summary = "Récupère tout les types d'UE d'un projet et leurs configurations (formulaires, settings, ..)",
            description = "Retourne la configuration du type par défaut (_default) et la liste des types configurés pour l'institution du projet.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectRecordingUnitTypeListResponse> getProjectRecordingUnitSettings(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Tri, ex. name:asc")
            @RequestParam(defaultValue = "name:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ProjectRecordingUnitTypeListResponse response = recordingUnitOpenApiService
                .buildProjectRecordingUnitTypeSettings(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/find-types")
    @Operation(summary = "Récupère tout les types de mobilier d'un projet et leurs configurations (formulaires, settings, ..)",
            description = "")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectFindTypeListResponse> getProjectFindSettings(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Tri, ex. name:asc")
            @RequestParam(defaultValue = "name:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ProjectFindTypeListResponse response = recordingUnitOpenApiService
                .buildProjectFindTypeSettings(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(response);
    }

}
