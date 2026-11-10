package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.PersonName;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.*;
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
     * Gives an account to a person awaiting it: the person takes the account's identity and keeps every field
     * and membership that names them. Without password, the caller invites them by e-mail to choose one (their
     * pending person then receives a registration token).
     *
     * @param personId the id of the person awaiting their account
     * @param identity the account's identity: first and last name, username and e-mail
     * @param password the account's plain password, or {@code null} to invite the person
     * @return the person, now carrying the account's identity
     * @throws IllegalStateException     if the person does not await an account (any more)
     * @throws InvalidUsernameException  if the username is invalid
     * @throws InvalidEmailException     if the email is invalid or already exists
     * @throws UserAlreadyExistException if a user with the same username or email already exists
     * @throws InvalidPasswordException  if the password does not meet the required criteria
     * @throws InvalidNameException      if the name is invalid
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonDTO giveAccount(Long personId, PersonDTO identity, String password) throws InvalidUsernameException,
            InvalidEmailException,
            UserAlreadyExistException,
            InvalidPasswordException,
            InvalidNameException {
        if (!isAwaitingAccount(personId)) {
            throw new IllegalStateException("Person " + personId + " does not await an account");
        }
        return personService.giveAccountTo(personId, identity, password);
    }

    /**
     * Gives an e-mail address to a person awaiting their account, so that they can be invited to create it.
     *
     * @param personId the id of the person awaiting their account
     * @param email    the address the invitation is sent to
     * @return the person, now carrying the address
     * @throws IllegalStateException if the person does not await an account (any more)
     * @throws InvalidEmailException if the address is invalid or already used
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonDTO assignInvitationEmail(Long personId, String email) throws InvalidEmailException {
        if (!isAwaitingAccount(personId)) {
            throw new IllegalStateException("Person " + personId + " does not await an account");
        }
        String address = email == null ? "" : email.trim();
        personService.updateEmail(personId, address);
        PersonDTO person = personMapper.convert(personRepository.getReferenceById(personId));
        person.setEmail(address);
        return person;
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
