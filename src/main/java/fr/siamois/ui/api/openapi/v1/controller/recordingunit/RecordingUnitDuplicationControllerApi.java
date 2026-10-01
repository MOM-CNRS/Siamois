package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitDuplicateStructureRequest;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitDuplicationResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitStructureResource;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitDuplicationOpenApiService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Dupliquer la structure" of a recording unit — see {@link RecordingUnitDuplicationOpenApiService}. */
@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitDuplicationControllerApi {

    private static final String KEY_DOC = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.";

    private final ProjectApiService projectApiService;
    private final RecordingUnitDuplicationOpenApiService duplicationService;

    @GetMapping("/{id}/structure")
    @Operation(
            summary = "Structure d'une UE",
            description = "L'UE et tous ses descendants (500 au plus), à plat : de quoi choisir ce qu'une duplication "
                    + "de structure inclut. Même périmètre d'accès que GET /api/v1/recording-units/{id}."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre")
    })
    public ResponseEntity<Response<RecordingUnitStructureResource>> getStructure(
            @Parameter(description = KEY_DOC, schema = @Schema(type = "string", example = "2"))
            @PathVariable("id") String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new Response<>(duplicationService.structure(id, caller.accessibleInstitutionIds())));
    }

    @PostMapping(value = "/{id}/duplicate-structure", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Dupliquer une UE et une partie de ses descendants",
            description = "N exemplaires (copies, 1 à 50) de l'UE et des descendants listés dans descendantIds. "
                    + "Chaque copie de l'UE reste sous les mêmes parents que l'original ; chaque descendant copié "
                    + "n'est rattaché qu'à la copie de son parent. Tout ou rien (une seule transaction). "
                    + "Même droit que la modification de l'UE source ; au plus 500 UE créées."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créées"),
            @ApiResponse(responseCode = "400", description = "Nombre d'exemplaires ou de créations hors limites"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Identifiant généré déjà utilisé")
    })
    public ResponseEntity<Response<RecordingUnitDuplicationResource>> duplicateStructure(
            @Parameter(description = KEY_DOC, schema = @Schema(type = "string", example = "2"))
            @PathVariable("id") String id,
            @RequestBody(required = false) RecordingUnitDuplicateStructureRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        RecordingUnitDuplicateStructureRequest request = body != null ? body : new RecordingUnitDuplicateStructureRequest();
        RecordingUnitDuplicationResource result = duplicationService.duplicateStructure(
                id, request.getCopies(), request.getDescendantIds(), caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new Response<>(result));
    }
}
