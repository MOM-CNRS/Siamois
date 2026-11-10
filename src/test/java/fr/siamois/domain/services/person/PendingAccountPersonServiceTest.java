package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.PersonName;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingAccountPersonServiceTest {

    @Mock
    private PersonService personService;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private PendingPersonRepository pendingPersonRepository;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private PersonProfileAssignmentService personProfileAssignmentService;

    @InjectMocks
    private PendingAccountPersonService service;

    private final PersonName name = new PersonName("Marie", "Curie");
    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        institution.setName("Inst");
        project = new ActionUnitDTO();
        project.setId(3L);
        project.setCreatedByInstitution(institution);
    }

    @Test
    void createInProject_createsPersonAwaitingAccountAndAddsToProject() {
        when(personRepository.findAwaitingAccountByName("Marie", "Curie", 10L)).thenReturn(List.of());
        PersonDTO created = new PersonDTO();
        created.setId(42L);
        when(personService.createAccountlessPerson(name)).thenReturn(created);
        Person reference = new Person();
        when(personRepository.getReferenceById(42L)).thenReturn(reference);

        PersonDTO result = service.createInProject(name, project);

        assertSame(created, result);
        ArgumentCaptor<PendingPerson> pending = ArgumentCaptor.forClass(PendingPerson.class);
        verify(pendingPersonRepository).save(pending.capture());
        assertSame(reference, pending.getValue().getDisabledPerson());
        assertTrue(pending.getValue().isAwaitingAccount());
        verify(personProfileAssignmentService).addToProjectMembers(project, created);
    }

    @Test
    void createInProject_reusesPersonOfSameNameAwaitingInTheOrganization() {
        Person existing = new Person();
        existing.setId(7L);
        PersonDTO existingDto = new PersonDTO();
        existingDto.setId(7L);
        when(personRepository.findAwaitingAccountByName("Marie", "Curie", 10L)).thenReturn(List.of(existing));
        when(personMapper.convert(existing)).thenReturn(existingDto);

        PersonDTO result = service.createInProject(name, project);

        assertSame(existingDto, result);
        verify(personService, never()).createAccountlessPerson(any());
        verify(pendingPersonRepository, never()).save(any());
        verify(personProfileAssignmentService).addToProjectMembers(project, existingDto);
    }

    @Test
    void findLinkCandidates_searchesEveryOrganization() {
        Person existing = new Person();
        PersonDTO existingDto = new PersonDTO();
        when(personRepository.findAwaitingAccountByName("Marie", "Curie", null)).thenReturn(List.of(existing));
        when(personMapper.convert(existing)).thenReturn(existingDto);

        assertEquals(List.of(existingDto), service.findLinkCandidates(name));
    }

    @Test
    void isAwaitingAccount_trueOnlyForPendingPersonWithoutToken() {
        when(pendingPersonRepository.findByDisabledPersonId(1L)).thenReturn(Optional.of(PendingPerson.awaitingAccountOf(new Person())));
        PendingPerson invited = new PendingPerson();
        invited.setRegisterToken("token");
        when(pendingPersonRepository.findByDisabledPersonId(2L)).thenReturn(Optional.of(invited));
        when(pendingPersonRepository.findByDisabledPersonId(3L)).thenReturn(Optional.empty());

        assertTrue(service.isAwaitingAccount(1L));
        assertFalse(service.isAwaitingAccount(2L));
        assertFalse(service.isAwaitingAccount(3L));
    }
}
