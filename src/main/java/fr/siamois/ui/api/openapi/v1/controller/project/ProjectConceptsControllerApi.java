package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.ProjectConceptsResponse;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.ProjectFieldCodesResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
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
@RequestMapping("/api/v1/projects/{id}")
@Tag(name = OpenApiTags.PROJECT)
@Tag(name = OpenApiTags.MOBILE_APP)
@RequiredArgsConstructor
public class ProjectConceptsControllerApi {

    private final ProjectApiService projectApiService;
    private final VocabularyOpenApiService vocabularyOpenApiService;

    @GetMapping("/concepts")
    @Operation(
            summary = "Concepts d'un projet pour un fieldCode",
            description = "Sans `q` : retourne tous les concepts paginés. "
                    + "Avec `q` : mode suggestion (autocomplete, non paginé, résultats limités). "
                    + "FieldCodes disponibles : GET /api/v1/projects/{id}/concepts/fields."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "fieldCode manquant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou fieldCode sans vocabulaire configuré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectConceptsResponse> getConcepts(
            @PathVariable long id,
            @Parameter(description = "Code du champ (ex: SIARU.TYPE, SIAS.CATEGORY).", required = true)
            @RequestParam String fieldCode,
            @Parameter(description = "Texte de recherche — active le mode suggestion si présent.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Nombre de résultats (ignoré en mode suggestion).")
            @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "Décalage pour la pagination (ignoré en mode suggestion).")
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        if (fieldCode == null || fieldCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le paramètre fieldCode est obligatoire");
        }

        ProjectApiCaller caller = projectApiService.requireCaller();
        long organizationId = resolveOrganizationId(caller, id);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        List<ConceptAutocompleteDTO> all = vocabularyOpenApiService.getConceptsForOrganization(
                organizationId, fieldCode, q, lang, caller.person());

        boolean isSuggestMode = q != null;
        List<ResolvedConceptResource> page = (isSuggestMode ? all : paginate(all, offset, limit))
                .stream().map(ResolvedConceptResource::from).toList();

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(all.size()))
                .body(new ProjectConceptsResponse(page));
    }

    @GetMapping("/field-codes")
    @Operation(
            summary = "FieldCodes disponibles pour un projet",
            description = "Retourne les fieldCodes ayant un vocabulaire configuré pour l'organisation liée au projet. "
                    + "Utiliser ces codes comme valeur du paramètre `fieldCode` sur GET /api/v1/projects/{id}/concepts."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectFieldCodesResponse> getFieldCodes(
            @PathVariable long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        long organizationId = resolveOrganizationId(caller, id);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        List<String> fieldCodes = vocabularyOpenApiService.getAvailableFieldCodesForOrganization(
                organizationId, lang, caller.person());

        return ResponseEntity.ok(new ProjectFieldCodesResponse(fieldCodes));
    }

    private long resolveOrganizationId(ProjectApiCaller caller, long projectId) {
        AccessibleProjectForApi project = projectApiService.requireAccessibleProject(caller, String.valueOf(projectId));
        InstitutionDTO institution = project.actionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        return institution.getId();
    }

    private static <T> List<T> paginate(List<T> list, int offset, int limit) {
        if (offset >= list.size()) {
            return List.of();
        }
        return list.subList(offset, Math.min(offset + limit, list.size()));
    }
}
