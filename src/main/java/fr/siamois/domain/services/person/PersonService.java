package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingActionUnitAttribution;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.verifier.PasswordVerifier;
import fr.siamois.domain.services.person.verifier.PersonDataVerifier;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service to manage Person
 */
@Slf4j
@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final List<PersonDataVerifier> verifiers;
    private final PersonSettingsRepository personSettingsRepository;
    private final InstitutionService institutionService;
    private final LangService langService;
    private final PendingPersonRepository pendingPersonRepository;
    private final PendingPersonService pendingPersonService;
    private final fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository pendingInstitutionInviteRepository;

    public PersonService(PersonRepository personRepository,
                         BCryptPasswordEncoder passwordEncoder,
                         List<PersonDataVerifier> verifiers,
                         PersonSettingsRepository personSettingsRepository,
                         InstitutionService institutionService,
                         LangService langService,
                         PendingPersonRepository pendingPersonRepository,
                         PendingPersonService pendingPersonService,
                         PendingInstitutionInviteRepository pendingInstitutionInviteRepository) {
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.verifiers = verifiers;
        this.personSettingsRepository = personSettingsRepository;
        this.institutionService = institutionService;
        this.langService = langService;
        this.pendingPersonRepository = pendingPersonRepository;
        this.pendingPersonService = pendingPersonService;
        this.pendingInstitutionInviteRepository = pendingInstitutionInviteRepository;
    }

    private void createAndDeletePendingRelations(PendingPerson pendingPerson, Person person) {
        Set<PendingInstitutionInvite> institutionInvites = pendingInstitutionInviteRepository.findAllByPendingPerson(pendingPerson);
        for (PendingInstitutionInvite invite : institutionInvites) {
            Institution institution = invite.getInstitution();
            if (invite.isManager()) {
                institutionService.addToManagers(institution, person);
            }
            if (invite.isActionManager()) {
                institutionService.addPersonToActionManager(institution, person);
            }

            Set<PendingActionUnitAttribution> attributions = pendingPersonService.findActionAttributionsByPendingInvite(invite);
            for (PendingActionUnitAttribution attribution : attributions) {
                institutionService.addPersonToActionUnit(attribution.getActionUnit(), person, attribution.getRole());
                pendingPersonService.delete(attribution);
            }

            pendingPersonService.delete(invite);
        }
    }

    private void managePendingInvites(Person savedPerson) {
        PendingPerson p = pendingPersonService.createOrGetPendingPerson(savedPerson.getEmail());
        createAndDeletePendingRelations(p, savedPerson);
        pendingPersonRepository.delete(p);
    }

    /**
     * Create a new Person in the database.
     *
     * @param person The Person to create with a plain password. It will be hashed before saving.
     * @return The created Person with its ID set.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidPasswordException  if the password does not meet the required criteria.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     */
    public Person createPerson(Person person) throws InvalidUsernameException, InvalidEmailException, UserAlreadyExistException, InvalidPasswordException, InvalidNameException {
        person.setId(-1L);

        checkPersonData(person, true);

        person.setPassword(passwordEncoder.encode(person.getPassword()));

        person = personRepository.save(person);

        managePendingInvites(person);

        return person;
    }

    private void checkPersonData(Person person, boolean isForCreation) throws InvalidUsernameException, InvalidEmailException, UserAlreadyExistException, InvalidPasswordException, InvalidNameException {
        for (PersonDataVerifier verifier : verifiers) {
            verifier.setForCreation(isForCreation);
            verifier.verify(person);
        }
    }

    /**
     * Find all the person where name or lastname match the string. Case is ignored.
     *
     * @param nameOrLastname The string to look for in name or username
     * @return The Person list
     */
    public List<Person> findAllByNameLastnameContaining(String nameOrLastname) {
        return personRepository.findAllByNameOrLastname(nameOrLastname);
    }


    /**
     * Find all the person being an author of a spatial unit
     *
     * @param institution The institution
     * @return The Person list
     */
    public List<PersonDTO> findAllAuthorsOfSpatialUnitByInstitution(InstitutionDTO institution) {
        return personRepository.findAllAuthorsOfSpatialUnitByInstitution(institution.getId());
    }

    /**
     * Find all the person being an author of a action unit
     *
     * @param institution The institution
     * @return The Person list
     */
    public List<Person> findAllAuthorsOfActionUnitByInstitution(InstitutionDTO institution) {
        return personRepository.findAllAuthorsOfActionUnitByInstitution(institution.getId());
    }

    /**
     * Find a person by its ID
     *
     * @param id The ID of the person
     * @return The person having the given ID
     */
    public Person findById(long id) {
        return personRepository.findById(id).orElse(null);
    }

    /**
     * Update a Person in the database. This methods cannot update the email of the person as it won't be saved.
     *
     * @param person The Person to update. It must have an ID set.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     * @throws InvalidPasswordException  if the password does not meet the required criteria.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     */
    public void updatePerson(Person person) throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        checkPersonData(person, false);

        personRepository.save(person);
    }

    /**
     * Check if the given plain password matches the hashed password of the person.
     *
     * @param person        The Person whose password is to be checked.
     * @param plainPassword The plain password to check against the hashed password.
     * @return true if the plain password matches the hashed password, false otherwise.
     */
    public boolean passwordMatch(Person person, String plainPassword) {
        return passwordEncoder.matches(plainPassword, person.getPassword());
    }

    Optional<PasswordVerifier> findPasswordVerifier() {
        for (PersonDataVerifier verifier : verifiers) {
            if (verifier.getClass().equals(PasswordVerifier.class)) return Optional.of((PasswordVerifier) verifier);
        }
        return Optional.empty();
    }

    /**
     * Update the password of a Person.
     *
     * @param person      The Person whose password is to be updated. The person must have an ID set.
     * @param newPassword The new plain password to set for the person.
     * @throws InvalidPasswordException if the new password does not meet the required criteria.
     */
    public void updatePassword(Person person, String newPassword) throws InvalidPasswordException {
        PasswordVerifier verifier = findPasswordVerifier().orElseThrow(() -> new IllegalStateException("Password verifier is not defined"));

        person.setPassword(newPassword);
        person.setPassToModify(false);

        verifier.verify(person);

        person.setPassword(passwordEncoder.encode(newPassword));
        personRepository.save(person);
    }

    /**
     * Create or get the settings of a Person.
     *
     * @param person The Person for whom to create or get the settings.
     * @return The PersonSettings object for the given person.
     */
    public PersonSettings createOrGetSettingsOf(PersonDTO person) {
        Optional<PersonSettings> personSettings = personSettingsRepository.findByPerson(person);
        if (personSettings.isPresent()) return personSettings.get();

        PersonSettings toSave = new PersonSettings();
        toSave.setPerson(person);
        toSave.setDefaultInstitution(findDefaultInstitution(person));
        toSave.setLangCode(findDefaultLang());

        return personSettingsRepository.save(toSave);
    }

    private String findDefaultLang() {
        return langService.getDefaultLang();
    }

    private Institution findDefaultInstitution(Person person) {
        Set<Institution> institutions = institutionService.findInstitutionsOfPerson(person);
        return institutions.isEmpty() ? null : institutions.stream().findFirst().orElseThrow(IllegalStateException::new);
    }

    /**
     * Update the settings of a Person.
     *
     * @param personSettings The PersonSettings to update. It must have a Person set.
     * @return The updated PersonSettings object.
     */
    public PersonSettings updatePersonSettings(PersonSettings personSettings) {
        log.trace("Updating person settings {}", personSettings.getPerson().getUsername());
        return personSettingsRepository.save(personSettings);
    }

    /**
     * Find a Person by its email.
     *
     * @param email The email of the person to find.
     * @return An Optional containing the Person if found, or empty if not found.
     */
    public Optional<Person> findByEmail(String email) {
        return personRepository.findByEmailIgnoreCase(email);
    }

    /**
     * Find a Person by its username or email. Case-insensitive. Uses the pg_trgm extension for fuzzy matching.
     *
     * @param usernameOrMailInput The username or email of the person to find.
     * @return An Optional containing the Person if found, or empty if not found.
     */
    public List<Person> findClosestByUsernameOrEmail(String usernameOrMailInput) {
        if (usernameOrMailInput == null || usernameOrMailInput.isBlank()) {
            return List.of();
        }

        Set<Person> result = new HashSet<>();
        result.addAll(personRepository.findClosestByEmailLimit10(usernameOrMailInput));
        result.addAll(personRepository.findClosestByUsernameLimit10(usernameOrMailInput));

        return result.stream().toList();
    }
}
