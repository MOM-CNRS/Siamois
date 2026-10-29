package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.study.StudyService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.IdentifierSuggestionResponse;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.Kind;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * The identifier a creation form suggests, for every entity whose identifier is generated: one endpoint,
 * the entity named by its collection. Each domain service computes it for the new entity built by
 * {@link ProjectTypedCreationService} — a find, created on a recording unit rather than in a project,
 * by {@link FindOpenApiService}.
 */
@RestController
@RequestMapping("/api/v1/identifier-suggestions")
@Tag(name = OpenApiTags.PROJECT, description = "Suggestion d'identifiant à la création")
@RequiredArgsConstructor
public class IdentifierSuggestionControllerApi {

    private final ProjectApiService projectApiService;
    private final ProjectTypedCreationService projectTypedCreationService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final PhaseService phaseService;
    private final ContainerService containerService;
    private final StudyService studyService;
    private final DocumentService documentService;
    private final FindOpenApiService findOpenApiService;

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
            @Parameter(description = "UE (recording_unit_id) sous laquelle la nouvelle UE sera créée — recording-units "
                    + "uniquement : les jetons du parent (NUM_PARENT, ID_PARENT) sont alors évalués. Rien n'est lié.",
                    example = "187")
            @RequestParam(required = false) Long parentRecordingUnitId,
            @Parameter(description = "Identifiant du concept définissant le type de l'entité (concept_id).",
                    example = "42")
            @RequestParam String typeId,
            @Parameter(description = "Langue préférée (la configuration des identifiants est lue dans la langue de l'utilisateur).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PersonDTO person = caller.person();
        Set<Long> scope = caller.accessibleInstitutionIds();
        String suggested = switch (entityType) {
            case "recording-units" -> projectTypedCreationService.suggestIdentifier(Kind.RECORDING_UNIT,
                    projectId, typeId, person, scope, lang,
                    unit -> recordingUnitOpenApiService.suggestFullIdentifier(unit, parentRecordingUnitId, scope));
            case "phases" -> projectTypedCreationService.suggestIdentifier(Kind.PHASE,
                    projectId, typeId, person, scope, lang, phaseService::suggestIdentifier);
            case "containers" -> projectTypedCreationService.suggestIdentifier(Kind.CONTAINER,
                    projectId, typeId, person, scope, lang, containerService::suggestIdentifier);
            case "studies" -> projectTypedCreationService.suggestIdentifier(Kind.STUDY,
                    projectId, typeId, person, scope, lang, studyService::suggestIdentifier);
            case "documents" -> projectTypedCreationService.suggestIdentifier(Kind.DOCUMENT,
                    projectId, typeId, person, scope, lang, documentService::suggestIdentifier);
            case "finds" -> findOpenApiService.suggestIdentifier(recordingUnitId, typeId, person, scope, lang);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityType inconnu : " + entityType
                    + " (attendu : recording-units, phases, containers, studies, documents ou finds)");
        };
        return ResponseEntity.ok(new IdentifierSuggestionResponse(suggested));
    }
}
