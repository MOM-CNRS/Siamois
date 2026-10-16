package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.DeprecatedEndpointHeaders;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.ProjectConceptsResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
import io.swagger.v3.oas.annotations.Hidden;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationProjectsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final VocabularyOpenApiService vocabularyOpenApiService;

    @Hidden
    @GetMapping("/{id}/projects")
    public ResponseEntity<ProjectListResponse> getProjects(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @GetMapping("/{id}/project-types")
    @Operation(summary = "Types de projet de l'organisation et leur configuration (formulaire, champs)",
            description = "Remplace GET /api/v1/projects/form. Retourne le layout, le catalogue de champs partagé "
                    + "et le formulaire du projet (form, fieldConfigs, tableColumns). data reste vide tant que Project n'a pas "
                    + "de types configurables (comme GET /api/v1/projects/{id}/recording-unit-types pour les UE).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectTypeListResponse> getProjectTypes(
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(id, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(recordingUnitOpenApiService.buildProjectTypes(id, caller.person(), lang));
    }


    @GetMapping("/{id}/concepts")
    @Operation(
            summary = "Concepts de l'organisation pour un champ (fieldId) ou un fieldCode",
            description = "Les concepts proposés par un champ de vocabulaire. **Utiliser `fieldId`** (celui du champ dans la "
                    + "configuration des types du projet) avec `projectId` et `valueConceptId` : le serveur applique la "
                    + "restriction branche/collection du champ, ses concepts désactivés ou sa liste figée, puis le "
                    + "thésaurus du projet, puis le field code. **`fieldCode` seul est déprécié** (réponse marquée "
                    + "`Deprecation: true`) : il ignore tout cela et renvoie la liste complète du code. "
                    + "Pour les filtres de la liste des projets, qui n'ont pas de projet de contexte. "
                    + "Sans `q` : tous les concepts paginés. Avec `q` : mode suggestion (autocomplete, non paginé, "
                    + "résultats limités)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "fieldCode manquant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable ou fieldCode sans vocabulaire configuré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectConceptsResponse> getConcepts(
            @PathVariable("id") long id,
            @Parameter(description = "Code du champ (ex: SIARU.TYPE, SIAS.CATEGORY). Obligatoire sauf si fieldId est donné.")
            @RequestParam(required = false) String fieldCode,
            @Parameter(description = "Identifiant d'un champ de vocabulaire (alternative à fieldCode) : ses propres "
                    + "restrictions branche/collection, puis le thésaurus du projet, puis son fieldCode.")
            @RequestParam(required = false) Long fieldId,
            @Parameter(description = "Avec fieldId : projet de l'entité éditée (thésaurus du projet).")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "Avec fieldId : concept de type de l'entité éditée.")
            @RequestParam(required = false) Long valueConceptId,
            @Parameter(description = "Liste dépendante (règle options RELATED_CONCEPTS) : seuls les concepts liés "
                    + "à ce concept — la réponse du champ dont celui-ci dépend.")
            @RequestParam(required = false) Long relatedToConceptId,
            @Parameter(description = "Texte de recherche — active le mode suggestion si présent.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Nombre de résultats (ignoré en mode suggestion).")
            @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "Décalage pour la pagination (ignoré en mode suggestion).")
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        if (fieldId == null && (fieldCode == null || fieldCode.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le paramètre fieldCode (ou fieldId) est obligatoire");
        }

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(id, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        List<ConceptAutocompleteDTO> all;
        if (relatedToConceptId != null) {
            all = vocabularyOpenApiService.getRelatedConcepts(id, fieldCode, fieldId, relatedToConceptId, projectId, q, lang, caller.person());
        } else if (fieldId != null) {
            all = vocabularyOpenApiService.getConceptsForField(id, fieldId, projectId, valueConceptId, q, lang, caller.person());
        } else {
            all = vocabularyOpenApiService.getConceptsForOrganization(id, fieldCode, q, lang, caller.person());
        }

        boolean isSuggestMode = q != null;
        List<ResolvedConceptResource> page = (isSuggestMode ? all : paginate(all, offset, limit))
                .stream().map(ResolvedConceptResource::from).toList();

        boolean byFieldCodeOnly = relatedToConceptId == null && fieldId == null;
        ResponseEntity.BodyBuilder response = byFieldCodeOnly
                ? DeprecatedEndpointHeaders.apply(ResponseEntity.ok(), "/api/v1/organizations/{id}/concepts?fieldId=")
                : ResponseEntity.ok();
        return response
                .header("X-Total-Count", String.valueOf(all.size()))
                .body(new ProjectConceptsResponse(page));
    }

    private static <T> List<T> paginate(List<T> list, int offset, int limit) {
        if (offset >= list.size()) {
            return List.of();
        }
        return list.subList(offset, Math.min(offset + limit, list.size()));
    }
}
