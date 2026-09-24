package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitFindsControllerApi {

    private final ProjectApiService projectApiService;
    private final ResourceBookmarkService resourceBookmarkService;

    @GetMapping("/{id}/mobiliers")
    @Operation(
            summary = "Liste des mobiliers d'une unité d'enregistrement",
            description = "Spécimens liés à l'UE (table specimen, fk_recording_unit_id). "
                    + "Pagination : offset, limit (max 200, offset multiple de limit). "
                    + "Tri : creationTime, id ou fullIdentifier (ex. creationTime:desc). "
                    + "Recherche : search, sur fullIdentifier. Chaque élément porte _permissions, resourceUri et bookmarked. "
                    + "Même clé d'UE que GET /api/v1/recording-units/{id}. "
                    + "La langue pour les libellés de type suit Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination invalide ou UE sans institution"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<FindListResponse> getFinds(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tri, ex. fullIdentifier:asc ou creationTime:desc")
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(description = "Recherche libre, sur fullIdentifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Langue pour le classement des libellés de type (requête SQL).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ProjectApiService.RecordingUnitFindsPage result = projectApiService.pageFindsOfRecordingUnit(
                caller, id, offset, limit, sort, search, acceptLanguage);
        Page<FindResource> page = result.page();
        RecordingUnitDTO ru = result.recordingUnit();

        // Same enrichment as GET /projects/{id}/mobiliers: one _permissions for the page (the RU's
        // project), the navigation URI, one bookmark query.
        boolean canEdit = ru.getActionUnit() != null
                && projectApiService.canEditFindsForProject(caller, String.valueOf(ru.getActionUnit().getId()), lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(canEdit);
        page.getContent().forEach(resource -> {
            resource.setPermissions(permissions);
            if (resource.getId() != null) {
                resource.setResourceUri("/specimen/" + resource.getId());
            }
        });
        resourceBookmarkService.markBookmarked(caller.person(), ru.getCreatedByInstitution(), page.getContent(), lang);
        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new FindListResponse(page.getContent(), meta));
    }


}
