package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.person.verifier.PasswordVerifier;
import fr.siamois.domain.services.person.verifier.PersonDataVerifier;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import fr.siamois.mapper.PersonMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

/**
 * Service to manage Person
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final PersonRepository personRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final List<PersonDataVerifier> verifiers;
    private final List<PasswordVerifier> passVerifiers;
    private final PersonSettingsRepository personSettingsRepository;
    private final InstitutionService institutionService;
    private final LangService langService;
    private final ConversionService conversionService;
    private final PersonMapper personMapper;
    private final PendingPersonRepository pendingPersonRepository;

    /**
     * Creates real relations and delete pending relations. Then delete the pending person
     * @param savedPerson The person but saved
     */
    private void deletePendingInvitation(Person savedPerson) {
        pendingPersonRepository.deleteById(savedPerson.getId());
    }

    /**
     * Create a new enabled Person in the database.
     *
     * @param personDTO The Person to create with a plain password. It will be hashed before saving.
     * @return The created Person with its ID set.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidPasswordException  if the password does not meet the required criteria.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     */
    public PersonDTO createPerson(PersonDTO personDTO, String password) throws InvalidUsernameException,
            InvalidEmailException,
            UserAlreadyExistException,
            InvalidPasswordException,
            InvalidNameException {
        personDTO.setId(-1L);

        checkPersonData(personDTO, true);
        checkPassword(password);

        Person person = conversionService.convert(personDTO,Person.class);
        assert person != null;
        person.setPassword(passwordEncoder.encode(password));
        person.setEnabled(true);

        person = personRepository.save(person);

        deletePendingInvitation(person);

        return conversionService.convert(person, PersonDTO.class);
    }

    /**
     * Create a new disabled Person in the database with a random password. Used when inviting someone
     * without setting a password: the person cannot log in until they complete the registration through
     * their invitation link, which replaces the random password.
     *
     * @param personDTO The Person to create.
     * @return The created Person with its ID set.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidPasswordException  never thrown, declared by the shared data verifiers.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     */
    public PersonDTO createDisabledPersonWithRandomPassword(PersonDTO personDTO) throws InvalidUsernameException,
            InvalidEmailException,
            UserAlreadyExistException,
            InvalidPasswordException,
            InvalidNameException {
        personDTO.setId(-1L);

        checkPersonData(personDTO, true);

        Person person = conversionService.convert(personDTO, Person.class);
        assert person != null;
        person.setPassword(passwordEncoder.encode(generateRandomPassword()));
        person.setEnabled(false);

        person = personRepository.save(person);

        return conversionService.convert(person, PersonDTO.class);
    }

    private static String generateRandomPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void checkPersonData(PersonDTO person, boolean isForCreation)
            throws InvalidUsernameException,
            InvalidEmailException,
            UserAlreadyExistException,
            InvalidPasswordException, InvalidNameException {
        for (PersonDataVerifier verifier : verifiers) {
            verifier.setForCreation(isForCreation);
            verifier.verify(person);
        }
    }

    private void checkPassword(String password)
            throws InvalidPasswordException {
        for (PasswordVerifier verifier : passVerifiers) {
            verifier.verify(password);
        }
    }

    /**
     * Find all the person where name or lastname match the string. Case is ignored.
     *
     * @param nameOrLastname The string to look for in name or username
     * @return The Person list
     */
    public List<PersonDTO> findAllByNameLastnameContaining(String nameOrLastname) {
        List<Person> persons = personRepository.findAllByNameOrLastname(nameOrLastname, 100);
        return persons.stream()
                .map(personMapper::convert)
                .toList();
    }

    /**
     * Utilisateurs rattachés à une institution (équipes de projet, gestionnaires), hors super-admins.
     *
     * @param search filtre optionnel sur nom, prénom, e-mail ou identifiant (insensible à la casse)
     */
    @Transactional(readOnly = true)
    public List<PersonDTO> findAllInInstitution(long institutionId, String search) {
        String filter = search == null || search.isBlank() ? null : search.trim();
        return personRepository.findAllInInstitution(institutionId, filter).stream()
                .map(personMapper::convert)
                .toList();
    }

    /**
     * Find all the person being an author of a spatial unit
     *
     * @param institution The institution
     * @return The Person list
     */
    public List<PersonDTO> findAllAuthorsOfSpatialUnitByInstitution(InstitutionDTO institution) {
        List<Person> authors = personRepository.findAllAuthorsOfSpatialUnitByInstitution(institution.getId());
        return authors.stream()
                .map(person -> conversionService.convert(person, PersonDTO.class))
                .toList();
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
     * @param personDTO The Person to update. It must have an ID set.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     * @throws InvalidPasswordException  if the password does not meet the required criteria.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     */
    public void updatePerson(@NotNull PersonDTO personDTO, String password) throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        checkPersonData(personDTO, false);
        checkPassword(password);
        Person person = conversionService.convert(personDTO,Person.class);
        Objects.requireNonNull(person, "Conversion failed");
        personRepository.save(person);
    }

    /**
     * Update a Person in the database. This methods cannot update the email of the person as it won't be saved.
     *
     * @param personDTO The Person to update. It must have an ID set.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     * @throws InvalidPasswordException  if the password does not meet the required criteria.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     */
    public void updatePerson(@NonNull PersonDTO personDTO) throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        checkPersonData(personDTO, false);
        Person existingPerson = personRepository.findById(personDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        Person person = conversionService.convert(personDTO,Person.class);
        assert person != null;
        person.setPassword(existingPerson.getPassword()); // Keep existing password
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
        for (PasswordVerifier verifier : passVerifiers) {
            if (verifier.getClass().equals(PasswordVerifier.class)) return Optional.of(verifier);
        }
        return Optional.empty();
    }

    /**
     * Update the password of a Person.
     *
     * @param id The person ID
     * @param newPassword The new plain password to set for the person.
     * @throws InvalidPasswordException if the new password does not meet the required criteria.
     */
    @Transactional
    public void updatePassword(Long id, String newPassword) throws InvalidPasswordException {
        PasswordVerifier verifier = findPasswordVerifier()
                .orElseThrow(() -> new IllegalStateException("Password verifier is not defined"));

        verifier.verify(newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);

        personRepository.updatePasswordById(id, encodedPassword);
    }

    /**
     * Create or get the settings of a Person.
     *
     * @param personDTO The Person for whom to create or get the settings.
     * @return The PersonSettings object for the given person.
     */
    public PersonSettings createOrGetSettingsOf(PersonDTO personDTO) {

        Optional<PersonSettings> personSettings = personSettingsRepository.findByPersonId(personDTO.getId());
        if (personSettings.isPresent()) return personSettings.get();

        PersonSettings toSave = new PersonSettings();

        // Fetch Person
        Person person = personRepository.findById(personDTO.getId())
                .orElseGet(() -> {
                    Person newPerson = personMapper.invertConvert(personDTO);
                    return personRepository.save(newPerson);
                });
        toSave.setPerson(person);

        toSave.setPerson(person);
        toSave.setDefaultInstitution(conversionService.convert(findDefaultInstitution(personDTO),Institution.class));
        toSave.setLangCode(findDefaultLang());

        return personSettingsRepository.save(toSave);
    }

    private String findDefaultLang() {
        return langService.getDefaultLang();
    }

    private InstitutionDTO findDefaultInstitution(PersonDTO personDTO) {
        Set<InstitutionDTO> institutions = institutionService.findInstitutionsOfPerson(personDTO);
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
    public List<PersonDTO> findClosestByUsernameOrEmail(String usernameOrMailInput) {
        if (usernameOrMailInput == null || usernameOrMailInput.isBlank()) {
            return List.of();
        }

        Set<Person> result = new HashSet<>();
        result.addAll(personRepository.findClosestByEmailLimit10(usernameOrMailInput));
        result.addAll(personRepository.findClosestByUsernameLimit10(usernameOrMailInput));

        return result.stream()
                .map(personMapper::convert)
                .toList();
    }

    public void enableAndUpdatePerson(PersonDTO person, String password) throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        Person savedPerson = personRepository.findById(person.getId()).orElseThrow(() -> new EntityNotFoundException("Person not found"));

        checkPersonData(person, false);
        checkPassword(password);

        savedPerson.setUsername(person.getUsername());
        savedPerson.setName(person.getName());
        savedPerson.setLastname(person.getLastname());
        savedPerson.setPassword(passwordEncoder.encode(password));
        savedPerson.setPassToModify(false);
        savedPerson.setEnabled(true);
        personRepository.save(savedPerson);
    }

    /**
     * Completes the registration of an invited person in a single transaction: enables and updates
     * their account, then removes the pending invitation that has just been consumed. If any step
     * fails (validation error or delete failure), the whole operation is rolled back so the account
     * is never left enabled with a lingering invitation.
     *
     * @param person   the invited person, whose account is being activated
     * @param password the plain password chosen by the invitee
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidPasswordException  if the password does not meet the required criteria.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     */
    @Transactional
    public void registerInvitedPerson(PersonDTO person, String password) throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        enableAndUpdatePerson(person, password);
        pendingPersonRepository.deleteByDisabledPersonId(person.getId());
    }

    public Optional<PersonDTO> findByUsername(String username) {
        return personRepository.findByUsernameIgnoreCase(username)
                .map(personMapper::convert);
    }
}
