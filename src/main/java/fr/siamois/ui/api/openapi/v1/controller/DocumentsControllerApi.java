package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentContentOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.DocumentFormOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document", description = "Fichiers et contenus des documents SIAMOIS")
@RequiredArgsConstructor
@Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
public class DocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final DocumentFormOpenApiService documentFormOpenApiService;

    @GetMapping("/form")
    @Operation(
            summary = "Formulaire création / édition d'un document",
            description = "Définition des champs (titre, description, nature, échelle, format, fichier) et listes de concepts "
                    + "pour SIAD.NATURE, SIAD.SCALE et SIAD.FORMAT, selon la configuration utilisateur / institution de l'organisation. "
                    + "Paramètre optionnel `documentId` : valeurs courantes pour pré-remplissage si le document est accessible "
                    + "(même règle d'institution que le téléchargement). "
                    + "Langue des libellés : en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation inconnue ou document introuvable (documentId)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentFormResponse> getDocumentForm(
            @Parameter(description = "Institution pour la résolution des vocabulaires (doit être dans le périmètre JWT).", example = "10")
            @RequestParam("organizationId") long organizationId,
            @Parameter(description = "Optionnel : identifiant du document à éditer (pré-remplissage).", example = "42")
            @RequestParam(value = "documentId", required = false) Long documentId,
            @Parameter(description = "Langue préférée pour les libellés de vocabulaire (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var data = documentFormOpenApiService.buildForm(
                caller.person(), organizationId, caller.accessibleInstitutionIds(), lang, documentId);
        return ResponseEntity.ok(new DocumentFormResponse(data));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Télécharger le fichier d'un document",
            description = "Flux binaire du fichier associé au document (`document_id`). "
                    + "L'institution de création du document doit être dans le périmètre JWT. "
                    + "404 si document absent, hors périmètre ou fichier introuvable sur le stockage."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok",
                    content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Document ou fichier introuvable / hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Resource> downloadContent(
            @Parameter(description = "Identifiant numérique du document (document_id).", example = "42")
            @PathVariable("id") long id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        DocumentContentOpenApiService.DocumentFilePayload payload =
                documentContentOpenApiService.requireDownloadableContent(id, caller.accessibleInstitutionIds());

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(payload.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(payload.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(payload.inputStream()));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer un document",
            description = "Supprime la ligne document, les liaisons (projet, UE spatiale, mobilier, études, etc.) et le fichier "
                    + "sur le stockage. Même règle d'accès que le téléchargement (institution de création dans le périmètre JWT)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Suppression effectuée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Identifiant numérique du document (document_id).", example = "42")
            @PathVariable("id") long id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        documentContentOpenApiService.deleteAccessibleDocument(id, caller.accessibleInstitutionIds());
        return ResponseEntity.noContent().build();
    }
}
