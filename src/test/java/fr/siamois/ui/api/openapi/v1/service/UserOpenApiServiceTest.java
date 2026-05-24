package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L, 20L);

    @Mock
    private PersonService personService;
    @Mock
    private InstitutionService institutionService;

    private UserOpenApiService service;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        service = new UserOpenApiService(personService, institutionService);
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, SCOPE, List.of());
    }

    @Test
    void pageUsersInOrganization_organizationOutOfScope_throws403() {
        assertThatThrownBy(() -> service.pageUsersInOrganization(caller, 99L, null, 0, 20, "lastname:asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void pageUsersInOrganization_unknownOrganization_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.pageUsersInOrganization(caller, 10L, null, 0, 20, "lastname:asc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void pageUsersInOrganization_sortsPaginatesAndFilters() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        PersonDTO a = user(1L, "Alice", "Martin", "alice");
        PersonDTO b = user(2L, "Bob", "Dupont", "bob");
        PersonDTO c = user(3L, "Claire", "Martin", "claire");
        when(personService.findAllInInstitution(10L, "mart")).thenReturn(List.of(a, c));

        Page<PersonDTO> page = service.pageUsersInOrganization(caller, 10L, "mart", 0, 1, "name:asc");

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).containsExactly(a);
        verify(personService).findAllInInstitution(10L, "mart");
    }

    @Test
    void pageUsersInOrganization_sortByLastnameDesc() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        PersonDTO a = user(1L, "A", "Zebra", "a");
        PersonDTO b = user(2L, "B", "Alpha", "b");
        when(personService.findAllInInstitution(10L, null)).thenReturn(List.of(a, b));

        Page<PersonDTO> page = service.pageUsersInOrganization(caller, 10L, null, 0, 20, "lastname:desc");

        assertThat(page.getContent()).containsExactly(a, b);
    }

    @Test
    void pageUsersInOrganization_invalidSortField_fallsBackToLastname() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        PersonDTO a = user(1L, "Z", "Zebra", "z");
        PersonDTO b = user(2L, "A", "Alpha", "a");
        when(personService.findAllInInstitution(10L, null)).thenReturn(List.of(a, b));

        Page<PersonDTO> page = service.pageUsersInOrganization(caller, 10L, null, 0, 20, "unknown:asc");

        assertThat(page.getContent()).containsExactly(b, a);
    }

    @Test
    void pageUsersInOrganization_offsetBeyondTotal_returnsEmptySlice() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);
        when(personService.findAllInInstitution(10L, null)).thenReturn(List.of(user(1L, "A", "B", "a")));

        Page<PersonDTO> page = service.pageUsersInOrganization(caller, 10L, null, 50, 20, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).isEmpty();
    }

    private static PersonDTO user(long id, String name, String lastname, String username) {
        PersonDTO p = new PersonDTO();
        p.setId(id);
        p.setName(name);
        p.setLastname(lastname);
        p.setUsername(username);
        return p;
    }
}
