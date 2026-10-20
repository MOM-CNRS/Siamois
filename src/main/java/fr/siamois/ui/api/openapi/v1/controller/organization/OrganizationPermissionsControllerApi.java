package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.organization.OrganizationPermissionsResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationPermissionsControllerApi {

    private final ProjectApiService projectApiService;
    private final ProfilePermissionService profilePermissionService;

    @GetMapping("/{id}/permissions")
    @Operation(
            summary = "Droits du caller sur une organisation",
            description = "Ce que l'utilisateur peut faire dans l'organisation sans y être déjà dans un projet "
                    + "(créer un projet) : l'accueil s'en sert pour proposer ou non l'action."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<OrganizationPermissionsResponse> getPermissions(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(id, caller);
        boolean canCreateProjects = profilePermissionService
                .institutionIdsWithActionUnitCreatePermission(caller.person(), List.of(institution))
                .contains(institution.getId());
        return ResponseEntity.ok(new OrganizationPermissionsResponse(new OrganizationResourcePermissions(canCreateProjects)));
    }
}
