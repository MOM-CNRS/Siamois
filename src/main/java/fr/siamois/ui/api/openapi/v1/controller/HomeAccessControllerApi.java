package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessResource;
import fr.siamois.ui.api.openapi.v1.service.HomeAccessOpenApiService;
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

/** The data accesses of an organisation's home page. See {@link HomeAccessOpenApiService} for the rules. */
@RestController
@RequestMapping("/api/v1/home-accesses")
@Tag(name = OpenApiTags.VIEW)
@RequiredArgsConstructor
public class HomeAccessControllerApi {

    private final ProjectApiService projectApiService;
    private final HomeAccessOpenApiService homeAccessService;

    @GetMapping
    @Operation(summary = "Accès aux données de l'accueil",
            description = "Les cartes configurées pour l'organisation, dans l'ordre d'affichage, et si l'utilisateur peut les modifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public HomeAccessListResponse list(
            @RequestParam Long organizationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return homeAccessService.list(requireUserInfo(organizationId, acceptLanguage));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ajouter un accès aux données",
            description = "Crée la vue (table et type) et la carte qui l'ouvre. Droit de modifier les vues par défaut requis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Table, titre ou état invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Droit manquant ou organisation hors périmètre")
    })
    public ResponseEntity<HomeAccessResource> create(
            @RequestBody HomeAccessRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        HomeAccessResource created = homeAccessService.create(requireUserInfo(body.organizationId(), acceptLanguage), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Retirer un accès aux données",
            description = "Retire la carte ; sa vue reste. Droit de modifier les vues par défaut requis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Retiré"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Droit manquant ou organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Accès introuvable")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam Long organizationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        homeAccessService.delete(requireUserInfo(organizationId, acceptLanguage), id);
        return ResponseEntity.noContent().build();
    }

    private UserInfo requireUserInfo(Long organizationId, String acceptLanguage) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(organizationId, caller);
        return new UserInfo(institution, caller.person(), ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }
}
