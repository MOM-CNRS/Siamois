package fr.siamois.ui.api.openapi.v1.controller.place;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/places")
@Tag(name = OpenApiTags.SPATIAL_UNIT)
@Tag(name = OpenApiTags.DOCUMENT)
@RequiredArgsConstructor
public class PlaceDocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à un lieu",
            description = "Crée un document et le lie à l'unité spatiale (spatial_unit_document). "
                    + "Champs multipart : title (obligatoire), file (obligatoire), description, "
                    + "natureConceptId, scaleConceptId, formatConceptId. "
                    + "Droit requis : ORGANIZATION_MANAGE_PLACES."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Lieu ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> createPlaceDocument(
            @Parameter(description = "Identifiant numérique du lieu (spatial_unit_id).", example = "15")
            @PathVariable("id") long id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "natureConceptId", required = false) Long natureConceptId,
            @RequestParam(value = "scaleConceptId", required = false) Long scaleConceptId,
            @RequestParam(value = "formatConceptId", required = false) Long formatConceptId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var resource = documentWriteOpenApiService.createForPlace(
                caller, id, title, description, natureConceptId, scaleConceptId, formatConceptId, file, lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DocumentResponse(resource));
    }
}
