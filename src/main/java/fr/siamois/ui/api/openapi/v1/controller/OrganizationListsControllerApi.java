package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.container.ContainerListResponse;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.phase.PhaseListResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.ui.api.openapi.v1.service.ContainerListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService.EditPermissions;
import fr.siamois.ui.api.openapi.v1.service.PhaseListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitListProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Organization-wide lists, at the collection root like {@code GET /api/v1/projects?organizationId=…}
 * — the React counterparts of JSF's RecordingUnit/Specimen/Phase/Container/SpatialUnit list panels.
 * Kept in one controller (not on each entity's own controller) so the existing controllers' and
 * tests' constructors don't change. Search + sort + pagination only.
 */
@RestController
@RequiredArgsConstructor
public class OrganizationListsControllerApi {

    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";
    private static final String ORG_PARAM_DOC = "Organisation (obligatoire)";

    private final ProjectApiService projectApiService;
    private final OrganizationListService organizationListService;
    private final PlaceOpenApiService placeOpenApiService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final RecordingUnitListProjectionService recordingUnitListProjectionService;
    private final FindOpenApiMapper findOpenApiMapper;
    private final PhaseOpenApiMapper phaseOpenApiMapper;
    private final PhaseListProjectionService phaseListProjectionService;
    private final ContainerOpenApiMapper containerOpenApiMapper;
    private final ContainerListProjectionService containerListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;

    @GetMapping("/api/v1/recording-units")
    @Tag(name = "Unité d'enregistrement")
    @Operation(summary = "Unités d'enregistrement d'une organisation",
            description = "Tri : fullIdentifier, creationTime, … (400 sur propriété inconnue). Recherche : fullIdentifier. "
                    + "Filtres par colonne f.<clé>, comme GET /api/v1/projects/{id}/recording-units. "
                    + "Un membre sans accès à toute l'organisation ne voit que ses projets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "organizationId absent, pagination ou tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    })
    public ResponseEntity<RecordingUnitListResponse> listRecordingUnits(
            @Parameter(description = ORG_PARAM_DOC) @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        // f.<key>[.from|.to] — same contract as GET /api/v1/projects/{id}/recording-units.
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(queryParams);
        Page<RecordingUnitDTO> page = organizationListService.pageRecordingUnits(caller, institution, offset, limit, sort, search, filter);
        Map<Long, Boolean> canEdit = canEdit(caller, institution, page, RecordingUnitDTO::getActionUnit, lang, EditPermissions.RECORDING_UNITS);
        RecordingUnitListProjectionService.RecordingUnitListProjection projection =
                recordingUnitListProjectionService.build(page.getContent(), null, lang);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(dto -> {
                    RecordingUnitResource resource = recordingUnitResourceMapper.convert(dto);
                    resource.setAnswers(projection.answersFor(dto.getId()));
                    resource.setPermissions(permissionsFor(canEdit, dto.getActionUnit()));
                    resource.setProject(OrganizationListService.projectRef(dto.getActionUnit()));
                    return resource;
                })
                .toList();
        resourceBookmarkService.markBookmarked(caller.person(), institution, resources, lang);
        return ok(new RecordingUnitListResponse(resources, meta(page, limit, offset)), page);
    }

    @GetMapping("/api/v1/finds")
    @Tag(name = "Mobilier")
    @Operation(summary = "Mobiliers d'une organisation",
            description = "Tri : fullIdentifier, collectionDate, id (400 sur propriété inconnue). Recherche : fullIdentifier. "
                    + "Un membre sans accès à toute l'organisation ne voit que ses projets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "organizationId absent, pagination ou tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    })
    public ResponseEntity<FindListResponse> listFinds(
            @Parameter(description = ORG_PARAM_DOC) @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "fullIdentifier:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        Page<SpecimenDTO> page = organizationListService.pageFinds(caller, institution, offset, limit, sort, search);
        Map<Long, Boolean> canEdit = canEdit(caller, institution, page, SpecimenDTO::getActionUnit, lang, EditPermissions.FINDS);

        List<FindResource> resources = page.getContent().stream()
                .map(dto -> {
                    FindResource resource = findOpenApiMapper.toResource(dto);
                    resource.setPermissions(permissionsFor(canEdit, dto.getActionUnit()));
                    resource.setProject(OrganizationListService.projectRef(dto.getActionUnit()));
                    if (dto.getId() != null) {
                        resource.setResourceUri("/specimen/" + dto.getId());
                    }
                    return resource;
                })
                .toList();
        resourceBookmarkService.markBookmarked(caller.person(), institution, resources, lang);
        return ok(new FindListResponse(resources, meta(page, limit, offset)), page);
    }

    @GetMapping("/api/v1/phases")
    @Tag(name = "Phase")
    @Operation(summary = "Phases d'une organisation",
            description = "Tri : identifier, orderNumber, title, id (400 sur propriété inconnue). Recherche : identifier. "
                    + "Un membre sans accès à toute l'organisation ne voit que ses projets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "organizationId absent, pagination ou tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    })
    public ResponseEntity<PhaseListResponse> listPhases(
            @Parameter(description = ORG_PARAM_DOC) @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "orderNumber:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        Page<PhaseDTO> page = organizationListService.pagePhases(caller, institution, offset, limit, sort, search);
        Map<Long, Boolean> canEdit = canEdit(caller, institution, page, PhaseDTO::getActionUnit, lang, EditPermissions.PHASES);
        PhaseListProjectionService.PhaseListProjection projection = phaseListProjectionService.build(page.getContent(), null, lang);

        List<PhaseResource> resources = page.getContent().stream()
                .map(dto -> {
                    PhaseResource resource = phaseOpenApiMapper.toResource(dto, lang, projection.resolvedLabels());
                    resource.setPermissions(permissionsFor(canEdit, dto.getActionUnit()));
                    resource.setProject(OrganizationListService.projectRef(dto.getActionUnit()));
                    return resource;
                })
                .toList();
        resourceBookmarkService.markBookmarked(caller.person(), institution, resources, lang);
        return ok(new PhaseListResponse(resources, meta(page, limit, offset)), page);
    }

    @GetMapping("/api/v1/containers")
    @Tag(name = "Contenant")
    @Operation(summary = "Contenants d'une organisation",
            description = "Tri : identifier, id (400 sur propriété inconnue). Recherche : identifier. "
                    + "Un membre sans accès à toute l'organisation ne voit que ses projets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "organizationId absent, pagination ou tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    })
    public ResponseEntity<ContainerListResponse> listContainers(
            @Parameter(description = ORG_PARAM_DOC) @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "identifier:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        Page<ContainerDTO> page = organizationListService.pageContainers(caller, institution, offset, limit, sort, search);
        Map<Long, Boolean> canEdit = canEdit(caller, institution, page, ContainerDTO::getActionUnit, lang, EditPermissions.CONTAINERS);
        ContainerListProjectionService.ContainerListProjection projection =
                containerListProjectionService.build(page.getContent(), null, lang);

        List<ContainerResource> resources = page.getContent().stream()
                .map(dto -> {
                    ContainerResource resource = containerOpenApiMapper.toResource(dto, lang, projection.resolvedLabels());
                    resource.setPermissions(permissionsFor(canEdit, dto.getActionUnit()));
                    resource.setProject(OrganizationListService.projectRef(dto.getActionUnit()));
                    return resource;
                })
                .toList();
        resourceBookmarkService.markBookmarked(caller.person(), institution, resources, lang);
        return ok(new ContainerListResponse(resources, meta(page, limit, offset)), page);
    }

    @GetMapping("/api/v1/places")
    @Tag(name = OpenApiTags.SPATIAL_UNIT)
    @Operation(summary = "Lieux d'une organisation",
            description = "Tri : name, id, code, creationTime ; direction asc ou desc. Recherche : name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "organizationId absent ou pagination invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    })
    public ResponseEntity<PlaceListResponse> listPlaces(
            @Parameter(description = ORG_PARAM_DOC) @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PlaceListResponse body = placeOpenApiService.listByOrganization(caller, organizationId, offset, limit, sort, search, lang);
        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(body.getMeta().total()))
                .body(body);
    }

    private <T> Map<Long, Boolean> canEdit(ProjectApiCaller caller, InstitutionDTO institution, Page<T> page,
                                           Function<T, ActionUnitSummaryDTO> project, String lang,
                                           EditPermissions permissions) {
        return organizationListService.canEditByProject(caller, institution,
                page.getContent().stream().map(project).toList(), lang, permissions);
    }

    private static ProjectResourcePermissions permissionsFor(Map<Long, Boolean> canEdit, ActionUnitSummaryDTO project) {
        boolean editable = project != null && Boolean.TRUE.equals(canEdit.get(project.getId()));
        return ProjectResourcePermissions.of(editable);
    }

    private static ListMeta meta(Page<?> page, int limit, int offset) {
        return new ListMeta(page.getTotalElements(), limit, (long) offset);
    }

    private static <B> ResponseEntity<B> ok(B body, Page<?> page) {
        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(page.getTotalElements()))
                .body(body);
    }
}
