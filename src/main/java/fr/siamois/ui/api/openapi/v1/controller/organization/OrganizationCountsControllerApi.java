package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.organization.OrganizationCountsResponse;
import fr.siamois.ui.api.openapi.v1.service.OrganizationCountsService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationCountsControllerApi {

    private final ProjectApiService projectApiService;
    private final OrganizationCountsService organizationCountsService;

    @GetMapping("/{id}/counts")
    @Operation(
            summary = "Compteurs d'une organisation",
            description = "Nombre de projets, lieux, unités d'enregistrement, mobiliers, phases et contenants."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<OrganizationCountsResponse> getCounts(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new OrganizationCountsResponse(
                organizationCountsService.countsForOrganization(caller, id)));
    }
}
