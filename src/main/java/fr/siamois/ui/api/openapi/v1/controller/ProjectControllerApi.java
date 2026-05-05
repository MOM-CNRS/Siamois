package fr.siamois.ui.api.openapi.v1.controller;


import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project", description = "Endpoints des projets")
public class ProjectControllerApi {


    @Operation(summary = "La liste des projets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/")
    public ResponseEntity<ProjectListResponse> getAll() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "Un projet via son identifiant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getById(
            @PathVariable Long id
    ) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }


    @Operation(summary = "La liste des mobiliers d'un projet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/finds")
    @Tag(name="Mobilier")
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "Le geopackage d'un projet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok",
                    content = @Content(
                            mediaType = "application/geopackage+sqlite3",
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping(value = "/{id}/geopackage", produces = "application/geopackage+sqlite3")
    public ResponseEntity<Resource> getGeoPackage(
            @PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }



    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un lieu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "404", description = "Institution non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units")
    @Tag(name = "Unité d'enregistrement")
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

}
