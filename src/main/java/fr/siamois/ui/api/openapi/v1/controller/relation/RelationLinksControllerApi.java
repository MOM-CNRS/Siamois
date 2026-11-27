package fr.siamois.ui.api.openapi.v1.controller.relation;

import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.relation.RelationLinkUpdateRequest;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationFieldResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationLinkResponse;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RelationOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The links of the configurable relations, entered from a record (docs/modele-relations.md). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = OpenApiTags.RELATION, description = "Liens entre fiches par les relations configurables")
@RequiredArgsConstructor
public class RelationLinksControllerApi {

    private final ProjectApiService projectApiService;
    private final RelationOpenApiService relationOpenApiService;

    @GetMapping("/relation-fields/{fieldId}")
    @Operation(summary = "Décrire un champ relation dans un projet",
            description = "Le côté du champ, son champ miroir, les fiches qu'il peut relier, s'il est à choix unique, et "
                    + "les qualificatifs tels qu'il les montre avec le concept que le lien enregistre, selon les règles "
                    + "que le projet suit.")
    @ApiResponse(responseCode = "200", description = "Champ décrit")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "404", description = "Champ sans relation, ou projet introuvable ou hors périmètre")
    public ResponseEntity<RelationFieldResponse> field(
            @Parameter(description = "Identifiant du champ", example = "-329") @PathVariable("fieldId") Long fieldId,
            @Parameter(description = "Projet de la fiche", example = "7", required = true)
            @RequestParam("projectId") Long projectId) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(relationOpenApiService.field(fieldId, projectId, caller));
    }

    @PostMapping(value = "/relation-links", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer un lien",
            description = "Relie la fiche en cours de saisie à une autre, par le champ d'un côté d'une relation. Le lien est "
                    + "enregistré une fois, de la source vers la cible. Vérifie : fiches existantes et accessibles, droit "
                    + "de modifier la fiche (les deux depuis le champ miroir), même projet, types acceptés par les règles "
                    + "du projet, qualificatif proposé, doublon, choix unique, exclusion, boucle.")
    @ApiResponse(responseCode = "201", description = "Lien créé ; une boucle par un lien incertain est dans warnings")
    @ApiResponse(responseCode = "400", description = "Type refusé, projets différents, lien sur soi, qualificatif invalide")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Modification non autorisée")
    @ApiResponse(responseCode = "404", description = "Relation ou fiche introuvable ou hors périmètre")
    @ApiResponse(responseCode = "409", description = "Lien déjà existant, ou règle enfreinte (details : la règle et les liens en cause)")
    public ResponseEntity<RelationLinkResponse> create(@RequestBody RelationLinkCreateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.status(HttpStatus.CREATED).body(relationOpenApiService.createLink(body, caller));
    }

    @PatchMapping(value = "/relation-links/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier un lien",
            description = "Change le qualificatif et l'incertitude. Rendre certain un lien qui ferme une boucle est refusé. "
                    + "Pour relier une autre fiche, supprimer le lien et en créer un autre.")
    @ApiResponse(responseCode = "200", description = "Lien modifié")
    @ApiResponse(responseCode = "400", description = "Qualificatif invalide")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Modification non autorisée")
    @ApiResponse(responseCode = "404", description = "Lien introuvable ou hors périmètre")
    @ApiResponse(responseCode = "409", description = "Règle enfreinte")
    public ResponseEntity<RelationLinkResponse> update(
            @Parameter(description = "Identifiant du lien", example = "7") @PathVariable("id") Long id,
            @RequestBody RelationLinkUpdateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(relationOpenApiService.updateLink(id, body, caller));
    }

    @DeleteMapping("/relation-links/{id}")
    @Operation(summary = "Supprimer un lien", description = "Supprime le lien, vu des deux fiches.")
    @ApiResponse(responseCode = "204", description = "Lien supprimé")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Modification non autorisée")
    @ApiResponse(responseCode = "404", description = "Lien introuvable ou hors périmètre")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifiant du lien", example = "7") @PathVariable("id") Long id,
            @Parameter(description = "Champ relation par lequel le lien est supprimé : donne le côté", example = "-329")
            @RequestParam(value = "field", required = false) Long field,
            @Parameter(description = "Côté du champ par lequel le lien est supprimé, si field est absent (source par défaut)")
            @RequestParam(value = "side", required = false) String side) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        relationOpenApiService.deleteLink(id, field, side, caller);
        return ResponseEntity.noContent().build();
    }
}
