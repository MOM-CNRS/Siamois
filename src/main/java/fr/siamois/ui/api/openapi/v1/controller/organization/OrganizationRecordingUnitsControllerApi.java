package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationRecordingUnitsControllerApi {

    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    /**
     * MVP sort surface for the React list (identifier search + basic column sort) — the JSF list also
     * sorts by type/action-unit/spatial-unit/counts via synthetic specs (RecordingUnitSortFilterService),
     * not threaded through here yet; extend this set (and add the matching filter param) when needed.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "creationTime", "id", RecordingUnitSpec.FULL_IDENTIFIER,
            RecordingUnitSpec.OPENING_DATE_FILTER, RecordingUnitSpec.CLOSING_DATE_FILTER);

    private final RecordingUnitService recordingUnitService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;


    @GetMapping("/{id}/recording-units")
    @Operation(summary = "Liste paginée des unités d'enregistrement d'une institution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Institution non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitListResponse> getRecordingUnits(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Recherche plein texte sur l'identifiant complet")
            @RequestParam(required = false) String q,
            @Parameter(description = "Tri, ex: fullIdentifier:asc. Champs autorisés: creationTime, id, fullIdentifier, openingDate, closingDate")
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(id, caller);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        int pageNumber = limit > 0 ? offset / limit : 0;
        Pageable pageable = PageRequest.of(pageNumber, limit, parseSort(sort));

        FilterDTO filters = new FilterDTO();
        if (q != null && !q.isBlank()) {
            filters.add(RecordingUnitSpec.FULL_IDENTIFIER, q.trim(), FilterDTO.FilterType.CONTAINS);
        }

        Page<RecordingUnitDTO> page = recordingUnitService.searchRecordingUnit(institution, filters, pageable, false);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(dto -> {
                    RecordingUnitResource resource = recordingUnitResourceMapper.convert(dto);
                    resource.setAnswers(recordingUnitOpenApiService.buildListColumnAnswers(dto, lang));
                    return resource;
                })
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(page.getTotalElements()))
                .body(new RecordingUnitListResponse(resources, meta));
    }

    /** Mirrors ProjectApiService#parseSortWithStableId — stable secondary sort by id unless id is primary. */
    private static Sort parseSort(String sortParam) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "creationTime").and(Sort.by(Sort.Direction.DESC, "id"));
        if (sortParam == null || sortParam.isBlank()) return defaultSort;
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(property)) return defaultSort;
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort primary = Sort.by(dir, property);
        return "id".equals(property) ? primary : primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
