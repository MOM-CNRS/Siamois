package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.view.DefaultViewRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewStateRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewWriteRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.DefaultViewResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewStateResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ViewOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saved list views of an organisation, the default view per record type, and each user's own state on
 * a view. See {@link ViewOpenApiService} for the rules.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = OpenApiTags.VIEW)
@RequiredArgsConstructor
public class ViewControllerApi {

    private final ProjectApiService projectApiService;
    private final ViewOpenApiService viewService;

    @GetMapping("/views")
    @Operation(summary = "Vues de l'organisation",
            description = "Les vues créées par les utilisateurs, visibles de tous les membres de l'organisation, "
                    + "les plus récemment modifiées d'abord. Les vues par défaut n'y figurent pas.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public ViewListResponse list(
            @RequestParam Long organizationId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.list(requireUserInfo(organizationId, acceptLanguage), resourceType, q, limit, offset);
    }

    @GetMapping("/views/{id}")
    @Operation(summary = "Une vue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "404", description = "Vue introuvable dans cette organisation")
    })
    public ViewResource get(
            @PathVariable Long id,
            @RequestParam Long organizationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.get(requireUserInfo(organizationId, acceptLanguage), id);
    }

    @PostMapping(value = "/views", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une vue", description = "L'utilisateur courant en devient le propriétaire.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créée"),
            @ApiResponse(responseCode = "400", description = "Champ obligatoire manquant")
    })
    public ResponseEntity<ViewResource> create(
            @RequestBody ViewWriteRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        UserInfo user = requireUserInfo(body.organizationId(), acceptLanguage);
        return ResponseEntity.status(HttpStatus.CREATED).body(viewService.create(user, body));
    }

    @PutMapping(value = "/views/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier une vue", description = "Réservé au propriétaire.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modifiée"),
            @ApiResponse(responseCode = "403", description = "Pas le propriétaire"),
            @ApiResponse(responseCode = "404", description = "Vue introuvable")
    })
    public ViewResource update(
            @PathVariable Long id,
            @RequestBody ViewWriteRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.update(requireUserInfo(body.organizationId(), acceptLanguage), id, body);
    }

    @DeleteMapping("/views/{id}")
    @Operation(summary = "Supprimer une vue",
            description = "Réservé au propriétaire. Supprime aussi l'état que chaque utilisateur avait sur cette vue.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimée"),
            @ApiResponse(responseCode = "403", description = "Pas le propriétaire"),
            @ApiResponse(responseCode = "404", description = "Vue introuvable")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam Long organizationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        viewService.delete(requireUserInfo(organizationId, acceptLanguage), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/views/default")
    @Operation(summary = "Vue par défaut d'un type",
            description = "La vue que chacun voit avant toute modification personnelle, et si l'utilisateur courant "
                    + "peut l'enregistrer. `view` est absent tant qu'aucune n'a été enregistrée.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "resourceType manquant")
    })
    public DefaultViewResponse getDefault(
            @RequestParam Long organizationId,
            @RequestParam String resourceType,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long typeId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.getDefault(requireUserInfo(organizationId, acceptLanguage), resourceType, projectId, typeId);
    }

    @PutMapping(value = "/views/default", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistrer la vue par défaut d'un type",
            description = "Demande le droit de modifier les vues par défaut (sur le projet, l'organisation ou l'instance).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enregistrée"),
            @ApiResponse(responseCode = "403", description = "Droit manquant")
    })
    public ViewResource saveDefault(
            @RequestBody DefaultViewRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.saveDefault(requireUserInfo(body.organizationId(), acceptLanguage), body);
    }

    @GetMapping("/view-states")
    @Operation(summary = "État de l'utilisateur courant sur une vue",
            description = "`state` est absent quand l'utilisateur n'a rien modifié sur cette vue.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "viewKey manquant")
    })
    public ViewStateResponse getState(
            @RequestParam Long organizationId,
            @RequestParam String viewKey,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.getState(requireUserInfo(organizationId, acceptLanguage), viewKey);
    }

    @PutMapping(value = "/view-states", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistrer l'état de l'utilisateur courant sur une vue",
            description = "Remplace l'état précédent ; la dernière écriture l'emporte.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enregistré"),
            @ApiResponse(responseCode = "400", description = "viewKey ou state manquant")
    })
    public ViewStateResponse putState(
            @RequestBody ViewStateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return viewService.putState(requireUserInfo(body.organizationId(), acceptLanguage), body);
    }

    @DeleteMapping("/view-states")
    @Operation(summary = "Réinitialiser l'état de l'utilisateur courant sur une vue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Réinitialisé (ou déjà absent)"),
            @ApiResponse(responseCode = "400", description = "viewKey manquant")
    })
    public ResponseEntity<Void> deleteState(
            @RequestParam Long organizationId,
            @RequestParam String viewKey,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        viewService.deleteState(requireUserInfo(organizationId, acceptLanguage), viewKey);
        return ResponseEntity.noContent().build();
    }

    private UserInfo requireUserInfo(Long organizationId, String acceptLanguage) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(organizationId, caller);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return new UserInfo(institution, caller.person(), lang);
    }
}
