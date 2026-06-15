package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{id}/recording-units")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class ProjectRecordingUnitsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;


    @GetMapping
    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id} (id numérique, fullIdentifier, identifiant court). "
                    + "Tri : paramètre sort au format « propriété:asc » ou « propriété:desc » "
                    + "(propriétés autorisées : creationTime, id, identifier, fullIdentifier, openingDate, closingDate). "
                    + "Valeur par défaut : creationTime:desc. "
                    + "Chaque élément inclut notamment : identifiant, type, nombre de relations stratigraphiques, "
                    + "nombre de mobiliers, dates, lieu (référence place), couleur de matrice, auteur et contributeurs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tri, ex. fullIdentifier:asc ou creationTime:desc")
            @RequestParam(defaultValue = "creationTime:desc") String sort) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<RecordingUnitDTO> page = projectApiService.pageRecordingUnitsForProject(caller, id, offset, limit, sort);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(recordingUnitResourceMapper::convert)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new RecordingUnitListResponse(resources, meta));
    }

}
