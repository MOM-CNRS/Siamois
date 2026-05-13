package fr.siamois.ui.api.openapi.v1.controller;


import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.OrganizationOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.*;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organisation", description = "API des organisation")
public class OrganizationControllerApi {

    private final RecordingUnitService recordingUnitService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final ProjectApiService projectApiService;
    private final OrganizationOpenApiMapper organizationOpenApiMapper;

    public OrganizationControllerApi(RecordingUnitService recordingUnitService,
                                     RecordingUnitResponseMapper recordingUnitResourceMapper,
                                     ProjectApiService projectApiService,
                                     OrganizationOpenApiMapper organizationOpenApiMapper) {
        this.recordingUnitService = recordingUnitService;
        this.recordingUnitResourceMapper = recordingUnitResourceMapper;
        this.projectApiService = projectApiService;
        this.organizationOpenApiMapper = organizationOpenApiMapper;
    }

    @Operation(
            summary = "Liste des organisations accessibles",
            description = "Institutions auxquelles l'utilisateur authentifié (JWT) est rattaché : membre, gestionnaire d'action, "
                    + "gestionnaire d'institution, ou super-admin (toutes les institutions). "
                    + "Paramètres autorisés : pagination (`offset`, `limit`) et tri (`sort`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<OrganizationListResponse> getAll(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Champ de tri : id, name, identifier, creationDate ; direction asc ou desc (ex. name:asc, id:desc).")
            @RequestParam(defaultValue = "name:asc") String sort) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller, offset, limit, sort);

        List<OrganizationResource> resources = page.getContent().stream()
                .map(organizationOpenApiMapper::toResource)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new OrganizationListResponse(resources, meta));
    }

    @Operation(summary = "Une organisation via son identifiant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getById(
            @PathVariable Long id
    ) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "La liste des projets d'une institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/projects")
    @Tag(name = "Project")
    public ResponseEntity<ProjectListResponse> getProjects(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "La liste des lieux d'une institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/places")
    @Tag(name = "Place")
    public ResponseEntity<PlaceListResponse> getPlaces(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "La liste des mobiliers d'une institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/finds")
    public ResponseEntity<PlaceListResponse> getFinds(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }


    @Operation(summary = "Récupérer une unité d'enregistrement d'une organisation par son identifiant metier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "RecordingUnit trouvée"),
            @ApiResponse(responseCode = "404", description = "RecordingUnit non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units/{recordingUnitFullIdentifier}")
    @Tag(name = "Unité d'enregistrement")
    public ResponseEntity<RecordingUnitResponse> getById(
            @PathVariable Long id,
            @Parameter(
                    description = "Optional list of counts to include. " +
                            "Only 'specimen' is allowed.",
                    schema = @Schema(
                            type = "array",
                            allowableValues = {"specimen"},
                            defaultValue = "specimen"
                    ),
                    in = ParameterIn.QUERY
            )
            @RequestParam(required = false)
            List<String> counts,
            @PathVariable String recordingUnitFullIdentifier) {

        RecordingUnitDTO recordingUnit =
                recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(
                        recordingUnitFullIdentifier,
                        id,
                        counts
                );

        return ResponseEntity.ok(
                new RecordingUnitResponse(
                        recordingUnitResourceMapper.convert(recordingUnit))
        );
    }

    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'une institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "404", description = "Institution non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units")
    @Tag(name = "Unité d'enregistrement")
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {

        Page<RecordingUnitDTO> page =
                recordingUnitService.findByInstitutionId(id, limit, offset);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(recordingUnitResourceMapper::convert)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        RecordingUnitListResponse response =
                new RecordingUnitListResponse(resources, meta);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(response);
    }

}
