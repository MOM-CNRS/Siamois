package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.PersonOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import fr.siamois.ui.api.openapi.v1.response.person.PersonListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.UserOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = OpenApiTags.USER)
@RequiredArgsConstructor
public class UsersControllerApi {

    private final ProjectApiService projectApiService;
    private final UserOpenApiService userOpenApiService;
    private final PersonOpenApiMapper personOpenApiMapper;

    @GetMapping
    @Operation(
            summary = "Liste des utilisateurs d'une organisation",
            description = "Retourne les personnes rattachées à l'organisation : gestionnaires d'institution, "
                    + "gestionnaires d'action et membres d'équipe de projet (hors super-admins). "
                    + "L'organisation doit être dans le périmètre JWT. "
                    + "Paramètre optionnel `search` : filtre sur prénom, nom, e-mail ou identifiant."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PersonListResponse> listUsers(
            @Parameter(description = "Institution (doit être dans le périmètre JWT).", example = "10", required = true)
            @RequestParam("organizationId") long organizationId,
            @Parameter(description = "Filtre texte sur prénom, nom, e-mail ou identifiant.")
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Tri : id, name, lastname, email, username (ex. lastname:asc, name:desc). Défaut : lastname:asc.")
            @RequestParam(defaultValue = "lastname:asc") String sort) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<PersonDTO> page = userOpenApiService.pageUsersInOrganization(
                caller, organizationId, search, offset, limit, sort);

        List<PersonResource> resources = page.getContent().stream()
                .map(personOpenApiMapper::toResource)
                .toList();
        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new PersonListResponse(resources, meta));
    }
}
