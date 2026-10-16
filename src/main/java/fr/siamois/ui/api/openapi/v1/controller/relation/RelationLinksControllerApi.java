package fr.siamois.ui.api.openapi.v1.controller.relation;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkUpdateRequest;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationDefinitionResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationLinkResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RelationLinkOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = OpenApiTags.RELATION)
@RequiredArgsConstructor
public class RelationLinksControllerApi {

    private final ProjectApiService projectApiService;
    private final RelationLinkOpenApiService relationLinkOpenApiService;

    @GetMapping("/relation-definitions/{code}")
    @Operation(
            summary = "Lire une relation",
            description = "Ce que la relation accepte de chaque côté (genre d'entité et type de configuration) et la correspondance "
                    + "de ses types (imposée ou suggérée) : de quoi proposer et préremplir le type d'un lien avant de le créer."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relation"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Relation inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RelationDefinitionResponse> definition(
            @Parameter(description = "Code de la relation", schema = @Schema(type = "string", example = "SIAMOIS.RELATION.ORDER"))
            @PathVariable("code") String code) {
        projectApiService.requireCaller();
        return ResponseEntity.ok(relationLinkOpenApiService.definition(code));
    }

    @PostMapping(value = "/relation-links", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un lien",
            description = "Relie deux entités par une relation configurable (stratigraphie, relation d'institution). Le lien est "
                    + "enregistré une seule fois, orienté de la source vers la cible ; l'autre côté en est un miroir. "
                    + "Vérifie ce que la relation accepte de chaque côté (genre d'entité et type), que les deux entités sont du "
                    + "même projet, la correspondance des types, les règles de la relation (pas de cycle, cardinalité, exclusion) "
                    + "et le droit de modifier l'entité du côté d'où le lien est saisi."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Lien créé (les règles non bloquantes enfreintes sont dans warnings)"),
            @ApiResponse(responseCode = "400", description = "Requête invalide : genre ou type refusé, projets différents, attribut invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Modification non autorisée, ou miroir en lecture seule"),
            @ApiResponse(responseCode = "404", description = "Relation ou entité introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Lien déjà existant ou règle bloquante enfreinte (details : les règles)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RelationLinkResponse> create(@RequestBody RelationLinkCreateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.status(HttpStatus.CREATED).body(relationLinkOpenApiService.create(body, caller));
    }

    @PatchMapping(value = "/relation-links/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier un lien",
            description = "Change les types du lien (changeTypes = true) et/ou écrit ses attributs. Ses extrémités ne changent pas : "
                    + "pour relier autre chose, supprimer le lien et en créer un autre."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lien mis à jour"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Modification non autorisée, ou miroir en lecture seule"),
            @ApiResponse(responseCode = "404", description = "Lien introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RelationLinkResponse> update(
            @Parameter(description = "Identifiant du lien", schema = @Schema(type = "integer", example = "7"))
            @PathVariable("id") Long id,
            @RequestBody RelationLinkUpdateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(relationLinkOpenApiService.update(id, body, caller));
    }

    @DeleteMapping("/relation-links/{id}")
    @Operation(
            summary = "Supprimer un lien",
            description = "Supprime le lien et ses attributs, vu des deux côtés."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Lien supprimé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Modification non autorisée, ou miroir en lecture seule"),
            @ApiResponse(responseCode = "404", description = "Lien introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifiant du lien", schema = @Schema(type = "integer", example = "7"))
            @PathVariable("id") Long id,
            @Parameter(description = "Extrémité depuis laquelle le lien est supprimé (source par défaut)",
                    schema = @Schema(allowableValues = {"source", "target"}))
            @RequestParam(value = "editedFrom", required = false) String editedFrom) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        relationLinkOpenApiService.delete(id, editedFrom, caller);
        return ResponseEntity.noContent().build();
    }
}
