package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.IdentifierSuggestionResponse;
import fr.siamois.ui.api.openapi.v1.service.IdentifierSuggestionService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identifier-suggestions")
@Tag(name = OpenApiTags.PROJECT, description = "Suggestion d'identifiant à la création")
@RequiredArgsConstructor
public class IdentifierSuggestionControllerApi {

    private final ProjectApiService projectApiService;
    private final IdentifierSuggestionService identifierSuggestionService;

    @GetMapping
    @Operation(
            summary = "Suggérer le prochain identifiant d'une nouvelle entité",
            description = "L'identifiant que le serveur générerait pour une nouvelle entité du type donné — sans "
                    + "consommer le compteur. Le formulaire de création l'affiche comme valeur modifiable : si "
                    + "l'utilisateur la change, l'identifiant choisi est envoyé à la création (champ identifier). "
                    + "Une entité se crée dans un projet (projectId), sauf un mobilier, créé sur une UE (recordingUnitId)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok (suggestedIdentifier null quand le projet ne "
                    + "configure pas de format : l'identifiant est alors attribué à la création)"),
            @ApiResponse(responseCode = "400", description = "Paramètres manquants ou invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet, UE ou type introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<IdentifierSuggestionResponse> suggestIdentifier(
            @Parameter(description = "Collection de l'entité : recording-units, phases, containers, studies, "
                    + "documents ou finds.", example = "phases")
            @RequestParam String entityType,
            @Parameter(description = "Identifiant ou clé du projet (périmètre JWT) — toutes les entités sauf finds.",
                    example = "10")
            @RequestParam(required = false) String projectId,
            @Parameter(description = "Identifiant de l'UE (recording_unit_id) — finds uniquement.", example = "187")
            @RequestParam(required = false) String recordingUnitId,
            @Parameter(description = "Identifiant du concept définissant le type de l'entité (concept_id).",
                    example = "42")
            @RequestParam String typeId,
            @Parameter(description = "Langue préférée (la configuration des identifiants est lue dans la langue de l'utilisateur).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new IdentifierSuggestionResponse(identifierSuggestionService.suggest(
                entityType, projectId, recordingUnitId, typeId, caller.person(), caller.accessibleInstitutionIds(), lang)));
    }
}
