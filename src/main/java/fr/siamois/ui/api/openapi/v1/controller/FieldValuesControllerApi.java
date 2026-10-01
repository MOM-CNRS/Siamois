package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.response.form.FieldValuesResponse;
import fr.siamois.ui.api.openapi.v1.service.FieldValuesService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RelationFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The values of one multi-valued answer, whatever resource it is on — the target of every
 * incomplete answer's {@code _links.values}.
 */
@RestController
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class FieldValuesControllerApi {

    private final ProjectApiService projectApiService;
    private final FieldValuesService fieldValuesService;

    @GetMapping("/api/v1/{collection:recording-units|finds|phases|containers|projects}/{id}/fields/{fieldId}/values")
    @Operation(summary = "Toutes les valeurs d'un champ multivalué",
            description = "Liste paginée des valeurs d'un champ multivalué d'une ressource — la cible de "
                    + "_links.values quand une liste ou un détail n'en porte qu'un aperçu (complete=false). "
                    + "Mêmes valeurs, même forme (ResourceRef) et même ordre (par libellé) que l'aperçu. "
                    + "Même périmètre d'accès que GET /api/v1/{collection}/{id}.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Champ non multivalué, pagination ou tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Ressource ou champ introuvable, ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<FieldValuesResponse> getValues(
            @Parameter(description = "Collection de la ressource",
                    schema = @Schema(allowableValues = {"recording-units", "finds", "phases", "containers", "projects"}))
            @PathVariable("collection") String collection,
            @Parameter(description = "Clé de la ressource, comme dans GET /api/v1/{collection}/{id}",
                    schema = @Schema(type = "string", example = "42"))
            @PathVariable("id") String id,
            @Parameter(description = "Identifiant du champ (custom_field_id)", schema = @Schema(type = "string", example = "-319"))
            @PathVariable("fieldId") String fieldId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "Ne garde que les valeurs dont le libellé contient ce texte (insensible à la casse)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Ordre des valeurs", schema = @Schema(allowableValues = {"label:asc", "label:desc"}))
            @RequestParam(defaultValue = FieldValuesService.SORT_ASC) String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        RelationFieldService.ValuesPage page = fieldValuesService.values(
                caller, collection, id, fieldId, offset, limit, search, sort, lang);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.total()))
                .body(new FieldValuesResponse(page.values(), new ListMeta(page.total(), limit, (long) offset)));
    }
}
