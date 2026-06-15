package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResourceResponse;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitDocumentsData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitDocumentsResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitDocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à une unité d'enregistrement",
            description = "Crée un document et le lie à l'UE (recording_unit_document). "
                    + "Champs multipart : title (obligatoire), file (obligatoire), description, "
                    + "natureConceptId, scaleConceptId, formatConceptId. "
                    + "Modification, suppression et téléchargement : /api/v1/documents/{id}."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResourceResponse> createRecordingUnitDocument(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) .",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
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
        var resource = documentWriteOpenApiService.createForRecordingUnit(
                caller, id, title, description, natureConceptId, scaleConceptId, formatConceptId, file, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentResourceResponse(resource));
    }




    @GetMapping("/{id}/documents")
    @Operation(
            summary = "Documents rattachés à une unité d'enregistrement",
            description = "Liste des documents liés à l'UE via recording_unit_document. "
                    + "Même clé d'UE que GET /api/v1/recording-units/{id} (identifiant numérique ou full_identifier)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitDocumentsResponse> getDocuments(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProjectDocumentResource> documents = projectApiService.listDocumentsForAccessibleRecordingUnit(caller, id);
        return ResponseEntity.ok(new RecordingUnitDocumentsResponse(new RecordingUnitDocumentsData(documents)));
    }






}
