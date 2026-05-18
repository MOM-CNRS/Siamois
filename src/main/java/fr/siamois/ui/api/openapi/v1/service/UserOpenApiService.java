package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Liste des utilisateurs (personnes) rattachés à une organisation accessible.
 */
@Service
public class UserOpenApiService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "lastname", "email", "username"
    );

    private final PersonService personService;
    private final InstitutionService institutionService;

    public UserOpenApiService(PersonService personService, InstitutionService institutionService) {
        this.personService = personService;
        this.institutionService = institutionService;
    }

    @Transactional(readOnly = true)
    public Page<PersonDTO> pageUsersInOrganization(
            ProjectApiCaller caller,
            long organizationId,
            String search,
            int offset,
            int limit,
            String sortParam) {
        assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        Sort sort = parseUserSort(sortParam);
        List<PersonDTO> sorted = sortPersons(personService.findAllInInstitution(organizationId, search), sort);

        long total = sorted.size();
        int from = Math.min(offset, (int) total);
        int to = Math.min(offset + limit, (int) total);
        List<PersonDTO> slice = sorted.subList(from, to);
        Pageable pageable = PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort);
        return new PageImpl<>(slice, pageable, total);
    }

    private static void assertOrganizationInCallerScope(Long organizationId, Set<Long> accessibleInstitutionIds) {
        if (organizationId != null && accessibleInstitutionIds != null
                && !accessibleInstitutionIds.contains(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible");
        }
    }

    private static Sort parseUserSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "lastname");
        }
        String[] parts = sortParam.split(":", 2);
        String property = ALLOWED_SORT_FIELDS.contains(parts[0].trim()) ? parts[0].trim() : "lastname";
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(dir, property);
    }

    private static List<PersonDTO> sortPersons(List<PersonDTO> persons, Sort sort) {
        if (persons.isEmpty()) {
            return List.of();
        }
        Sort.Order order = sort.stream().findFirst().orElse(new Sort.Order(Sort.Direction.ASC, "lastname"));
        Comparator<PersonDTO> cmp = switch (order.getProperty()) {
            case "id" -> Comparator.comparing(PersonDTO::getId, Comparator.nullsLast(Long::compareTo));
            case "name" -> Comparator.comparing(PersonDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "email" -> Comparator.comparing(PersonDTO::getEmail, Comparator.nullsLast(String::compareToIgnoreCase));
            case "username" -> Comparator.comparing(PersonDTO::getUsername, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(PersonDTO::getLastname, Comparator.nullsLast(String::compareToIgnoreCase));
        };
        if (order.isDescending()) {
            cmp = cmp.reversed();
        }
        Comparator<PersonDTO> stable = cmp.thenComparing(PersonDTO::getId, Comparator.nullsLast(Long::compareTo));
        return persons.stream().sorted(stable).toList();
    }
}
