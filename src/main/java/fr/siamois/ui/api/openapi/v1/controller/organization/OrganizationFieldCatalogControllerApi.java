package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.organization.OrganizationFieldCatalogResponse;
import fr.siamois.ui.api.openapi.v1.service.OrganizationFieldCatalogService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Column catalogs of the organization-wide lists — the organization-level counterpart of
 * {@code GET /api/v1/projects/{id}/{recording-unit|find|phase|container}-types}, aggregated over the
 * organization's projects (see {@link OrganizationFieldCatalogService}).
 */
@RestController
@RequestMapping("/api/v1/organizations/{id}")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationFieldCatalogControllerApi {

    private static final String DESCRIPTION = "Catalogue de colonnes de la liste organisation : champs système du "
            + "formulaire de détail, plus l'union des champs additionnels actifs dans au moins un projet de "
            + "l'organisation. Même forme que le catalogue projet, avec data toujours vide.";

    private final ProjectApiService projectApiService;
    private final OrganizationFieldCatalogService organizationFieldCatalogService;

    @GetMapping("/recording-unit-types")
    @Operation(summary = "Catalogue de colonnes des unités d'enregistrement de l'organisation", description = DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public ResponseEntity<OrganizationFieldCatalogResponse> recordingUnitTypes(
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return catalog(id, ConfigurableTable.UE, acceptLanguage);
    }

    @GetMapping("/find-types")
    @Operation(summary = "Catalogue de colonnes des mobiliers de l'organisation", description = DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public ResponseEntity<OrganizationFieldCatalogResponse> findTypes(
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return catalog(id, ConfigurableTable.MOBILIER, acceptLanguage);
    }

    @GetMapping("/phase-types")
    @Operation(summary = "Catalogue de colonnes des phases de l'organisation", description = DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public ResponseEntity<OrganizationFieldCatalogResponse> phaseTypes(
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return catalog(id, ConfigurableTable.PHASE, acceptLanguage);
    }

    @GetMapping("/container-types")
    @Operation(summary = "Catalogue de colonnes des contenants de l'organisation", description = DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public ResponseEntity<OrganizationFieldCatalogResponse> containerTypes(
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return catalog(id, ConfigurableTable.CONTENANT, acceptLanguage);
    }

    private ResponseEntity<OrganizationFieldCatalogResponse> catalog(long id, ConfigurableTable table, String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(id, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(organizationFieldCatalogService.build(id, table, lang));
    }
}
