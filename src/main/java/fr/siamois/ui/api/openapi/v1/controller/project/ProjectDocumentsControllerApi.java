package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{id}/documents")
@Tag(name = OpenApiTags.PROJECT)
@Tag(name = OpenApiTags.DOCUMENT)
@RequiredArgsConstructor
public class ProjectDocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à un projet",
            description = "Crée un document et le lie au projet. "
                    + "Champs multipart : title (obligatoire), file (obligatoire), description, "
                    + "natureConceptId, scaleConceptId, formatConceptId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> createProjectDocument(
            @PathVariable("id") String id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "natureConceptId", required = false) Long natureConceptId,
            @RequestParam(value = "scaleConceptId", required = false) Long scaleConceptId,
            @RequestParam(value = "formatConceptId", required = false) Long formatConceptId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var resource = documentWriteOpenApiService.createForProject(
                caller, id, title, description, natureConceptId, scaleConceptId, formatConceptId, file, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentResponse(resource));
    }

    @GetMapping()
    @Operation(
            summary = "Documents rattachés à un projet",
            description = "Liste des documents liés à l'unité d'action (projet) via action_unit_document. "
                    + "Même clé de projet que GET /api/v1/projects/{id} (id numérique, fullIdentifier, identifiant court)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentListResponse> getDocuments(@PathVariable("id") String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        // todo : add pagination
        List<DocumentResource> documents = projectApiService.listDocumentsForAccessibleProject(caller, id);
        return ResponseEntity.ok(new DocumentListResponse(documents, null));
    }


}
