package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchManifestRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.response.SiblingsResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentBatchResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResponse;
import fr.siamois.ui.api.openapi.v1.service.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = OpenApiTags.DOCUMENT)
@RequiredArgsConstructor
public class DocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final DocumentFormOpenApiService documentFormOpenApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;
    private final DocumentOpenApiService documentOpenApiService;
    private final DocumentBulkOpenApiService documentBulkOpenApiService;

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload massif de documents (multi-cibles)",
            description = "Crée plusieurs documents en un appel. "
                    + "Part `manifest` (JSON DocumentBatchManifestRequest) : items indexés. "
                    + "Parts fichiers : `file_0`, `file_1`, … alignés sur l'index du manifeste. "
                    + "Chaque item peut être rattaché à plusieurs cibles "
                    + "(projects, recording-units, finds, places). "
                    + "Réponse 200 avec statut par item (created/failed) ; liens MD5 déjà présents sont listés dans skippedTargets."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lot traité (voir results[].status)"),
            @ApiResponse(responseCode = "400", description = "Manifeste invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentBatchResponse> batchUpload(
            @RequestPart("manifest") String manifestJson,
            MultipartHttpServletRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        DocumentBatchManifestRequest manifest = documentBulkOpenApiService.parseManifest(manifestJson);

        Map<String, MultipartFile> files = new HashMap<>();
        Iterator<String> names = request.getFileNames();
        while (names.hasNext()) {
            String name = names.next();
            MultipartFile file = request.getFile(name);
            if (file != null) {
                files.put(name, file);
            }
        }

        DocumentBatchResponse body = documentBulkOpenApiService.processBatch(caller, manifest, files, lang);
        return ResponseEntity.ok(body);
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.DETAIL_DEFAULT)
    @GetMapping("/{id}")
    @Operation(
            summary = "Un document via son identifiant",
            description = "Valeurs de tous les champs formulaire (système et additionnels de la catégorie), indexées par fieldId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> getById(
            @Parameter(description = "Identifiant numérique du document (document_id).", example = "42")
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new DocumentResponse(documentOpenApiService.getDocumentById(
                id, caller.person(), caller.accessibleInstitutionIds(), lang)));
    }

    @GetMapping("/{id}/siblings")
    @Operation(summary = "Document précédent et suivant",
            description = "Voisins dans le même projet, par ordre de création. Boucle en fin de liste ; "
                    + "null seulement s'il n'y a aucun autre élément.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<SiblingsResponse> getSiblings(@PathVariable("id") long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new SiblingsResponse(
                documentOpenApiService.findSiblings(id, caller.person(), caller.accessibleInstitutionIds())));
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.DETAIL_DEFAULT)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un document",
            description = "Crée un document (sans fichier) dans un projet, avec sa catégorie. Droit requis : édition des "
                    + "documents (instance, organisation ou projet). Le fichier s'envoie ensuite sur la fiche."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Projet ou catégorie introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> createDocument(
            @RequestBody DocumentCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DocumentResponse(documentOpenApiService.createDocument(
                body, caller.person(), caller.accessibleInstitutionIds(), lang)));
    }

    @GetMapping("/form")
    @Operation(
            summary = "Formulaire création / édition d'un document",
            description = "Définition des champs (titre, description, nature, échelle, format, fichier). "
                    + "Les listes de concepts (SIAD.NATURE, SIAD.SCALE, SIAD.FORMAT) sont fournies par GET /api/v1/vocabularies. "
                    + "Paramètre optionnel `documentId` : valeurs courantes pour pré-remplissage si le document est accessible "
                    + "(même règle d'institution que le téléchargement). "
                    + "Langue des libellés des valeurs courantes : en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation inconnue ou document introuvable (documentId)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentFormResponse> getDocumentForm(
            @Parameter(description = "Institution du document (doit être dans le périmètre JWT).", example = "10")
            @RequestParam("organizationId") long organizationId,
            @Parameter(description = "Optionnel : identifiant du document à éditer (pré-remplissage).", example = "42")
            @RequestParam(value = "documentId", required = false) Long documentId,
            @Parameter(description = "Langue préférée pour les libellés des valeurs courantes (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var data = documentFormOpenApiService.buildForm(
                caller.person(), organizationId, caller.accessibleInstitutionIds(), lang, documentId);
        return ResponseEntity.ok(new DocumentFormResponse(data));
    }

    @GetMapping("/{id}/file")
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
            @PathVariable("id") long id,
            @Parameter(description = "true : téléchargement (attachment) ; sinon affichage dans le navigateur (inline).")
            @RequestParam(value = "download", defaultValue = "false") boolean download,
            @Parameter(description = "Image uniquement : largeur maximale en pixels (16 à 1600) d'une copie réduite en JPEG, "
                    + "pour les vignettes. Ignoré pour un autre type de fichier ou une image que le serveur ne sait pas décoder "
                    + "(le fichier d'origine est alors servi).", example = "480")
            @RequestParam(value = "w", required = false) Integer width) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        DocumentContentOpenApiService.DocumentFilePayload payload =
                documentContentOpenApiService.requireDownloadableContent(id, caller.accessibleInstitutionIds());

        if (width != null && !download) {
            Optional<DocumentContentOpenApiService.DocumentFilePayload> thumbnail = documentContentOpenApiService.thumbnailOf(payload, width);
            if (thumbnail.isPresent()) {
                return ResponseEntity.ok()
                        .contentType(thumbnail.get().mediaType())
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename(thumbnail.get().fileName(), StandardCharsets.UTF_8).build().toString())
                        // The picture of a card: the same document at the same width is not asked for twice.
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                        .body(new InputStreamResource(thumbnail.get().inputStream()));
            }
            // Not an image the server can decode: the thumbnail attempt used up the stream, open the file again.
            payload = documentContentOpenApiService.requireDownloadableContent(id, caller.accessibleInstitutionIds());
        }

        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(payload.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(payload.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(payload.inputStream()));
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Envoyer ou remplacer le fichier d'un document",
            description = "Multipart (`file`). Remplace le fichier précédent, renseigne la taille (Mo) et, s'il est vide, "
                    + "le format. Droit d'édition des documents du projet requis ; la taille maximale est celle du serveur."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Fichier absent, type ou taille refusés"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Pas le droit d'éditer les documents du projet"),
            @ApiResponse(responseCode = "404", description = "Document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> putFile(@PathVariable("id") long id,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        documentContentOpenApiService.replaceFile(id, file, caller);
        return ResponseEntity.ok(new DocumentResponse(documentOpenApiService.getDocumentById(
                id, caller.person(), caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage))));
    }

    @DeleteMapping("/{id}/file")
    @Operation(
            summary = "Retirer le fichier d'un document",
            description = "Supprime le fichier stocké ; le document et son URL externe demeurent."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Pas le droit d'éditer les documents du projet"),
            @ApiResponse(responseCode = "404", description = "Document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> deleteFile(@PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        documentContentOpenApiService.removeFile(id, caller);
        return ResponseEntity.ok(new DocumentResponse(documentOpenApiService.getDocumentById(
                id, caller.person(), caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage))));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier les métadonnées d'un document",
            description = "Client mobile : titre, description et concepts (nature, échelle, format) à plat. "
                    + "Client web : `answers` (fusion partielle des champs du formulaire, comme les autres entités) "
                    + "et `validationStatus`. Le fichier n'est pas remplacé par cet endpoint."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Document ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> patchDocument(@PathVariable("id") long id, @RequestBody DocumentPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        if (body.isFormPatch()) {
            String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
            return ResponseEntity.ok(new DocumentResponse(documentOpenApiService.patchDocument(
                    id, body, caller.person(), caller.accessibleInstitutionIds(), lang)));
        }
        var resource = documentWriteOpenApiService.updateDocument(
                caller,
                id,
                body.getTitle(),
                body.getDescription(),
                body.getNatureConceptId(),
                body.getScaleConceptId(),
                body.getFormatConceptId());
        return ResponseEntity.ok(new DocumentResponse(resource));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer un document",
            description = "Supprime la ligne document et le fichier sur le stockage. Refus (409) si le document est encore lié "
                    + "à une entité (UE, mobilier, lieu, phase, contenant, étude) : retirer d'abord ces liens. L'institution de création doit être dans le périmètre JWT et l'appelant doit pouvoir éditer "
                    + "les documents du projet du document."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Suppression effectuée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Pas le droit d'éditer les documents du projet"),
            @ApiResponse(responseCode = "404", description = "Document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Document encore lié à une entité"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Identifiant numérique du document (document_id).", example = "42")
            @PathVariable("id") long id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        documentContentOpenApiService.deleteAccessibleDocument(id, caller);
        return ResponseEntity.noContent().build();
    }
}
