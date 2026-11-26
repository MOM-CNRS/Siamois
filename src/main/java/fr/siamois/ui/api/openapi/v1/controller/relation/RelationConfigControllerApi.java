package fr.siamois.ui.api.openapi.v1.controller.relation;

import fr.siamois.ui.api.openapi.v1.request.relation.RelationDefinitionCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationDefinitionUpdateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationRulesRequest;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationDefinitionResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationRulesResponse;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RelationOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The configuration of the relations (docs/modele-relations.md, §3.2 and §3.3): the definitions, by the platform
 * administrator, and their rules per scope.
 */
@RestController
@RequestMapping("/api/v1/relation-definitions")
@Tag(name = OpenApiTags.RELATION_CONFIG, description = "Définitions des relations et leurs règles par portée")
@RequiredArgsConstructor
public class RelationConfigControllerApi {

    private static final String SCOPE = "Portée : projectId pour un projet, institutionId seul pour une institution, aucun "
            + "pour le défaut de la plateforme.";

    private final ProjectApiService projectApiService;
    private final RelationOpenApiService relationOpenApiService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une relation depuis un champ",
            description = "Le champ devient le côté source ; le côté cible est un champ relation existant ou un nouveau "
                    + "(le même pour une relation symétrique). La relation démarre avec ses règles par défaut de la "
                    + "plateforme. Réservé à l'administrateur de la plateforme.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Relation créée"),
            @ApiResponse(responseCode = "400", description = "Champ invalide ou déjà côté d'une relation, qualificatif invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Réservé à l'administrateur de la plateforme")
    })
    public ResponseEntity<RelationDefinitionResponse> create(@RequestBody RelationDefinitionCreateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.status(HttpStatus.CREATED).body(relationOpenApiService.createDefinition(body, caller));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire une relation", description = "Son identité : ses deux champs et sa nature.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relation"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Relation inconnue")
    })
    public ResponseEntity<RelationDefinitionResponse> read(@PathVariable("id") Long id) {
        projectApiService.requireCaller();
        return ResponseEntity.ok(relationOpenApiService.definition(id));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier la nature d'une relation",
            description = "Simple ou complexe, hiérarchique, champ qualificatif : refusé dès que la relation a des liens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relation modifiée"),
            @ApiResponse(responseCode = "400", description = "La relation a des liens, ou nature invalide"),
            @ApiResponse(responseCode = "403", description = "Réservé à l'administrateur de la plateforme"),
            @ApiResponse(responseCode = "404", description = "Relation inconnue")
    })
    public ResponseEntity<RelationDefinitionResponse> update(@PathVariable("id") Long id,
                                                             @RequestBody RelationDefinitionUpdateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(relationOpenApiService.updateDefinition(id, body, caller));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une relation",
            description = "Refusé tant qu'elle a des liens. Ses règles partent avec elle ; ses deux champs restent.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Relation supprimée"),
            @ApiResponse(responseCode = "400", description = "La relation a des liens"),
            @ApiResponse(responseCode = "403", description = "Réservé à l'administrateur de la plateforme"),
            @ApiResponse(responseCode = "404", description = "Relation inconnue")
    })
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        relationOpenApiService.deleteDefinition(id, caller);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/rules")
    @Operation(summary = "Lire les règles d'une portée",
            description = "Les règles propres à la portée, sinon celles qu'elle hérite (own = false). " + SCOPE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Règles"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Relation, projet ou institution introuvable ou hors périmètre")
    })
    public ResponseEntity<RelationRulesResponse> rules(@PathVariable("id") Long id,
            @RequestParam(value = "institutionId", required = false) Long institutionId,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(relationOpenApiService.rules(id, institutionId, projectId, caller));
    }

    @PutMapping(value = "/{id}/rules", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistrer les règles d'une portée",
            description = "Remplace les règles de la portée, ou les crée si elle n'en avait pas. Refusé, avec les liens en "
                    + "cause, si des liens existants des projets qui suivent ces règles les enfreignent. " + SCOPE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Règles enregistrées"),
            @ApiResponse(responseCode = "400", description = "Règles invalides (paires miroir, exclusions)"),
            @ApiResponse(responseCode = "403", description = "Modification non autorisée"),
            @ApiResponse(responseCode = "404", description = "Relation, projet ou institution introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Des liens existants enfreignent ces règles (details)")
    })
    public ResponseEntity<RelationRulesResponse> saveRules(@PathVariable("id") Long id,
            @RequestParam(value = "institutionId", required = false) Long institutionId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestBody RelationRulesRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(relationOpenApiService.saveRules(id, institutionId, projectId, body, caller));
    }

    @DeleteMapping("/{id}/rules")
    @Operation(summary = "Retirer les règles propres d'une portée",
            description = "La portée suit alors le niveau au-dessus. Refusé si ses liens enfreignent ces règles-là. " + SCOPE)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Règles retirées"),
            @ApiResponse(responseCode = "403", description = "Modification non autorisée"),
            @ApiResponse(responseCode = "409", description = "Des liens enfreignent les règles du niveau au-dessus")
    })
    public ResponseEntity<Void> deleteRules(@PathVariable("id") Long id,
            @RequestParam(value = "institutionId", required = false) Long institutionId,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        relationOpenApiService.deleteRules(id, institutionId, projectId, caller);
        return ResponseEntity.noContent().build();
    }
}
