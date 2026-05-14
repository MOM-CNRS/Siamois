package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestration des cas d'usage OpenAPI « projet » et listes liées : pagination, tri, périmètre institutions.
 */
@Service
@RequiredArgsConstructor
public class ProjectApiService {

    public static final int MAX_PAGE_SIZE = 200;

    private static final Set<String> ALLOWED_PROJECT_SORT_FIELDS = Set.of(
            "name", "identifier", "fullIdentifier", "creationTime"
    );

    private static final Set<String> ALLOWED_RECORDING_UNIT_SORT_FIELDS = Set.of(
            "creationTime", "id", "identifier", "fullIdentifier", "openingDate", "closingDate"
    );

    private static final Set<String> ALLOWED_ORGANIZATION_SORT_FIELDS = Set.of(
            "id", "name", "identifier", "creationDate"
    );

    private static final Set<String> ALLOWED_FIND_SORT_FIELDS = Set.of(
            "creationTime", "id", "fullIdentifier"
    );

    private final InstitutionService institutionService;
    private final ActionUnitService actionUnitService;
    private final RecordingUnitService recordingUnitService;
    private final DocumentService documentService;
    private final SpecimenService specimenService;
    private final ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    private final FindOpenApiMapper findOpenApiMapper;
    private final PersonMapper personMapper;

    public void validatePagedListRequest(int offset, int limit) {
        if (offset < 0 || limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètres de pagination invalides");
        }
        if (limit > 0 && offset % limit != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset doit être un multiple de limit");
        }
    }

    public ProjectApiCaller requireCaller() {
        Person person = AuthenticatedUserUtils.getAuthenticatedUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));
        PersonDTO personDto = personMapper.convert(person);
        List<InstitutionDTO> institutions = new ArrayList<>(institutionService.findInstitutionsOfPerson(personDto));
        Set<Long> institutionIds = institutions.stream()
                .map(InstitutionDTO::getId)
                .collect(Collectors.toSet());
        return new ProjectApiCaller(personDto, institutionIds, institutions);
    }

    public void assertOrganizationInCallerScope(Long organizationId, Set<Long> accessibleInstitutionIds) {
        if (organizationId != null && !accessibleInstitutionIds.contains(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible");
        }
    }

    public Page<AccessibleProjectForApi> pageAccessibleProjects(
            ProjectApiCaller caller,
            Long organizationId,
            String search,
            int offset,
            int limit,
            String sortParam) {
        assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        Sort sort = parseProjectSort(sortParam);
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit, sort);
        return actionUnitService.findAccessibleProjects(
                caller.accessibleInstitutionIds(),
                organizationId,
                search,
                pageable);
    }

    public AccessibleProjectForApi requireAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        return actionUnitService.findAccessibleProjectByKey(projectIdOrKey, caller.accessibleInstitutionIds());
    }

    /**
     * Institutions que l'utilisateur peut consulter.
     * Tri et pagination appliqués sur la liste déjà portée par {@link ProjectApiCaller#institutions()}
     * (chargée une seule fois lors de l'appel à {@link #requireCaller()}).
     */
    public Page<InstitutionDTO> pageAccessibleOrganizations(ProjectApiCaller caller, int offset, int limit, String sortParam) {
        Sort sort = parseOrganizationSort(sortParam);
        List<InstitutionDTO> sorted = sortInstitutions(caller.institutions(), sort);
        long total = sorted.size();
        int from = Math.min(offset, (int) total);
        int to = Math.min(offset + limit, (int) total);
        List<InstitutionDTO> slice = sorted.subList(from, to);
        Pageable pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        return new PageImpl<>(slice, pageable, total);
    }

    public Page<RecordingUnitDTO> pageRecordingUnitsForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            int offset,
            int limit,
            String sortParam) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        Sort sort = parseRecordingUnitSort(sortParam);
        return recordingUnitService.findByActionUnitId(row.actionUnit().getId(), limit, offset, sort);
    }

    /**
     * Documents rattachés au projet (unité d'action) via la table {@code action_unit_document}.
     */
    @Transactional(readOnly = true)
    public List<ProjectDocumentResource> listDocumentsForAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        AccessibleProjectForApi row = requireAccessibleProject(caller, projectIdOrKey);
        return documentService.findForActionUnit(row.actionUnit()).stream()
                .sorted(Comparator.comparing(Document::getId, Comparator.nullsLast(Long::compareTo)))
                .map(projectDocumentOpenApiMapper::toResource)
                .toList();
    }

    /**
     * Documents rattachés à une UE via la table {@code recording_unit_document}.
     */
    @Transactional(readOnly = true)
    public List<ProjectDocumentResource> listDocumentsForAccessibleRecordingUnit(ProjectApiCaller caller, String recordingUnitKey) {
        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, caller.accessibleInstitutionIds(), null);
        return documentService.findForRecordingUnit(ru).stream()
                .sorted(Comparator.comparing(Document::getId, Comparator.nullsLast(Long::compareTo)))
                .map(projectDocumentOpenApiMapper::toResource)
                .toList();
    }

    /**
     * Mobiliers (spécimens) rattachés à une UE accessible, avec pagination (même périmètre que le détail UE).
     */
    @Transactional(readOnly = true)
    public Page<FindResource> pageFindsForAccessibleRecordingUnit(
            ProjectApiCaller caller,
            String recordingUnitKey,
            int offset,
            int limit,
            String sortParam,
            String acceptLanguage) {
        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, caller.accessibleInstitutionIds(), null);
        InstitutionDTO institution = ru.getCreatedByInstitution();
        if (institution == null || institution.getId() == null || ru.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité d'enregistrement sans institution");
        }
        String lang = primaryAcceptLanguage(acceptLanguage);
        Sort sort = parseFindSort(sortParam);
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit, sort);
        Page<SpecimenDTO> page = specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institution.getId(),
                ru.getId(),
                null,
                null,
                null,
                lang,
                pageable);
        return page.map(findOpenApiMapper::toResource);
    }

    /**
     * Langue principale depuis l'en-tête {@code Accept-Language} (première entrée, sans qualité).
     */
    public static String primaryAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.FRENCH.getLanguage();
        }
        String first = acceptLanguage.split(",")[0].trim();
        int semi = first.indexOf(';');
        if (semi > 0) {
            first = first.substring(0, semi).trim();
        }
        int dash = first.indexOf('-');
        return (dash > 0 ? first.substring(0, dash) : first).toLowerCase(Locale.ROOT);
    }

    private static Sort parseProjectSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "name");
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_PROJECT_SORT_FIELDS.contains(property)) {
            property = "name";
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private static Sort parseOrganizationSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "name");
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_ORGANIZATION_SORT_FIELDS.contains(property)) {
            property = "name";
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private static List<InstitutionDTO> sortInstitutions(List<InstitutionDTO> institutions, Sort sort) {
        if (institutions.isEmpty()) {
            return List.of();
        }
        Sort.Order order = sort.stream().findFirst().orElse(new Sort.Order(Sort.Direction.ASC, "name"));
        Comparator<InstitutionDTO> cmp = switch (order.getProperty()) {
            case "id" -> Comparator.comparing(InstitutionDTO::getId, Comparator.nullsLast(Long::compareTo));
            case "identifier" -> Comparator.comparing(
                    InstitutionDTO::getIdentifier, Comparator.nullsLast(String::compareToIgnoreCase));
            case "creationDate" -> Comparator.comparing(
                    InstitutionDTO::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(InstitutionDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase));
        };
        if (order.isDescending()) {
            cmp = cmp.reversed();
        }
        return institutions.stream().sorted(cmp).toList();
    }

    private static Sort parseFindSort(String sortParam) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "creationTime")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        if (sortParam == null || sortParam.isBlank()) {
            return defaultSort;
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_FIND_SORT_FIELDS.contains(property)) {
            return defaultSort;
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort primary = Sort.by(direction, property);
        if ("id".equals(property)) {
            return primary;
        }
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static Sort parseRecordingUnitSort(String sortParam) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "creationTime")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        if (sortParam == null || sortParam.isBlank()) {
            return defaultSort;
        }
        String[] parts = sortParam.split(":", 2);
        String property = parts[0].trim();
        if (!ALLOWED_RECORDING_UNIT_SORT_FIELDS.contains(property)) {
            return defaultSort;
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort primary = Sort.by(direction, property);
        if ("id".equals(property)) {
            return primary;
        }
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
