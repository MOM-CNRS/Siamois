package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.OrganizationOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.OrganizationListResponse;
import fr.siamois.ui.api.openapi.v1.response.OrganizationResponse;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationControllerApi {

    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    private final RecordingUnitService recordingUnitService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final ProjectApiService projectApiService;
    private final OrganizationOpenApiMapper organizationOpenApiMapper;

    @GetMapping
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
    public ResponseEntity<OrganizationListResponse> getAll(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Champ de tri : id, name, identifier, creationDate ; direction asc ou desc (ex. name:asc, id:desc).")
            @RequestParam(required = false) List<String> sort) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();

        Page<InstitutionDTO> page = projectApiService.pageAccessibleOrganizations(caller, offset, limit, sort);

        List<OrganizationResource> resources = page.getContent().stream()
                .map(organizationOpenApiMapper::toResource)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(page.getTotalElements()))
                .body(new OrganizationListResponse(resources, meta));
    }

    @Hidden
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

}
