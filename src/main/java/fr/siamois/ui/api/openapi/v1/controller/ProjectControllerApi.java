package fr.siamois.ui.api.openapi.v1.controller;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import fr.siamois.utils.AuthenticatedUserUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project", description = "Endpoints des projets")
public class ProjectControllerApi {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "identifier", "fullIdentifier", "creationTime"
    );

    private final ActionUnitService actionUnitService;
    private final InstitutionService institutionService;
    private final PersonMapper personMapper;
    private final ProjectResponseMapper projectResponseMapper;

    public ProjectControllerApi(ActionUnitService actionUnitService,
                              InstitutionService institutionService,
                              PersonMapper personMapper,
                              ProjectResponseMapper projectResponseMapper) {
        this.actionUnitService = actionUnitService;
        this.institutionService = institutionService;
        this.personMapper = personMapper;
        this.projectResponseMapper = projectResponseMapper;
    }

    @Operation(summary = "La liste des projets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation non autorisée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping({"", "/"})
    public ResponseEntity<ProjectListResponse> getAll(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name:asc") String sort) {

        if (offset < 0 || limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètres de pagination invalides");
        }
        if (limit > 0 && offset % limit != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset doit être un multiple de limit");
        }

        Person person = AuthenticatedUserUtils.getAuthenticatedUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        PersonDTO personDto = personMapper.convert(person);
        Set<Long> institutionIds = institutionService.findInstitutionsOfPerson(personDto).stream()
                .map(InstitutionDTO::getId)
                .collect(Collectors.toSet());

        if (organizationId != null && !institutionIds.contains(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible");
        }

        Sort springSort = parseProjectSort(sort);
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit, springSort);

        Page<AccessibleProjectForApi> rows = actionUnitService.findAccessibleProjects(
                institutionIds,
                organizationId,
                search,
                pageable);

        List<ProjectResource> resources = rows.getContent().stream()
                .map(projectResponseMapper::toResource)
                .toList();

        ListMeta meta = new ListMeta(rows.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(rows.getTotalElements()))
                .body(new ProjectListResponse(resources, meta));
    }

    private static Sort parseProjectSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "name");
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(property)) {
            property = "name";
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    @Operation(summary = "Un projet via son identifiant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getById(
            @PathVariable Long id
    ) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }


    @Operation(summary = "La liste des mobiliers d'un projet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/finds")
    @Tag(name="Mobilier")
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "Le geopackage d'un projet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok",
                    content = @Content(
                            mediaType = "application/geopackage+sqlite3",
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping(value = "/{id}/geopackage", produces = "application/geopackage+sqlite3")
    public ResponseEntity<Resource> getGeoPackage(
            @PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }



    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un lieu")
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

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

}
