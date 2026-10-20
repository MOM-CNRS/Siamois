package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.view.FicheConfigRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.FicheConfigResponse;
import fr.siamois.ui.api.openapi.v1.service.FicheConfigOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
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
 * The tabs of a record type's fiche, configured per project. See {@link FicheConfigOpenApiService}.
 */
@RestController
@RequestMapping("/api/v1/fiche-configs")
@Tag(name = OpenApiTags.VIEW)
@RequiredArgsConstructor
public class FicheConfigControllerApi {

    private final ProjectApiService projectApiService;
    private final FicheConfigOpenApiService ficheConfigService;

    @GetMapping
    @Operation(summary = "Onglets configurés d'une fiche",
            description = "La configuration du sous-type demandé, à défaut celle de tous les sous-types, et si "
                    + "l'utilisateur courant peut l'enregistrer. `tabs` est absent tant que personne n'a configuré la fiche.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "resourceType manquant")
    })
    public FicheConfigResponse get(
            @RequestParam Long organizationId,
            @RequestParam String resourceType,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long typeId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ficheConfigService.get(requireUserInfo(organizationId, acceptLanguage), resourceType, projectId, typeId);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistrer les onglets d'une fiche",
            description = "Demande le droit de modifier les vues par défaut (sur le projet, l'organisation ou l'instance).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enregistrée"),
            @ApiResponse(responseCode = "400", description = "Configuration invalide"),
            @ApiResponse(responseCode = "403", description = "Droit manquant")
    })
    public FicheConfigResponse save(
            @RequestBody FicheConfigRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ficheConfigService.save(requireUserInfo(body.organizationId(), acceptLanguage), body);
    }

    @DeleteMapping
    @Operation(summary = "Revenir aux onglets du code",
            description = "Supprime la configuration exacte demandée (projet, type et sous-type). Même droit que l'enregistrement.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimée (ou déjà absente)"),
            @ApiResponse(responseCode = "403", description = "Droit manquant")
    })
    public ResponseEntity<Void> delete(
            @RequestParam Long organizationId,
            @RequestParam String resourceType,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long typeId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ficheConfigService.delete(requireUserInfo(organizationId, acceptLanguage), resourceType, projectId, typeId);
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
