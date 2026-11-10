package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.PersonName;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Persons awaiting the creation of their account: someone named in a form field that expects a person (the
 * scientific manager of a project, the manager of a study's treatment…) who has no account yet. They are
 * created on the fly from their first and last name, as a disabled person with a {@link PendingPerson}
 * that carries no registration token, and join the project they were named in as a member.
 * <p>
 * When an account is later created for someone of the same name, the manager can link it to the person
 * awaiting it ({@link #findLinkCandidates(PersonName)}): the account then takes over that very person, so
 * every field and membership that named them stays attached.
 */
@Service
@RequiredArgsConstructor
public class PendingAccountPersonService {

    private final PersonService personService;
    private final PersonRepository personRepository;
    private final PendingPersonRepository pendingPersonRepository;
    private final PersonMapper personMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    /**
     * Gives the person of this name awaiting an account in the project's organization, creating them if there
     * is none, and makes them a member of the project.
     *
     * @param name    the person's first and last name
     * @param project the project the person is named in; its organization scopes the reuse of an existing person
     * @return the person, member of the project
     */
    @Transactional
    public PersonDTO createInProject(PersonName name, ActionUnitDTO project) {
        PersonDTO person = findAwaitingAccountInInstitution(name, project.getCreatedByInstitution().getId())
                .orElseGet(() -> createAwaitingAccount(name));
        personProfileAssignmentService.addToProjectMembers(project, person);
        return person;
    }

    /**
     * The persons awaiting an account who carry the given name, ignoring case and accents: the ones an account
     * created for someone of this name may be linked to.
     *
     * @param name the name of the person an account is being created for
     * @return the persons awaiting an account of this name, oldest first
     */
    @Transactional(readOnly = true)
    public List<PersonDTO> findLinkCandidates(PersonName name) {
        return personRepository.findAwaitingAccountByName(name.firstName(), name.lastName(), null).stream()
                .map(personMapper::convert)
                .toList();
    }

    /**
     * Tells whether the person still awaits the creation of their account.
     *
     * @param personId the person's id
     * @return {@code true} when the person was created from their name only and has not been given an account
     */
    @Transactional(readOnly = true)
    public boolean isAwaitingAccount(Long personId) {
        return pendingPersonRepository.findByDisabledPersonId(personId)
                .map(PendingPerson::isAwaitingAccount)
                .orElse(false);
    }

    private Optional<PersonDTO> findAwaitingAccountInInstitution(PersonName name, Long institutionId) {
        return personRepository.findAwaitingAccountByName(name.firstName(), name.lastName(), institutionId).stream()
                .findFirst()
                .map(personMapper::convert);
    }

    private PersonDTO createAwaitingAccount(PersonName name) {
        PersonDTO person = personService.createAccountlessPerson(name);
        Person saved = personRepository.getReferenceById(person.getId());
        pendingPersonRepository.save(PendingPerson.awaitingAccountOf(saved));
        return person;
    }
}
