package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.person.verifier.EmailVerifier;
import fr.siamois.domain.services.person.verifier.PasswordVerifier;
import fr.siamois.domain.services.person.verifier.PersonDataVerifier;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import fr.siamois.infrastructure.database.repositories.specs.PersonSpec;
import fr.siamois.mapper.PersonMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private void deletePendingInvitation(Person savedPerson) {
        pendingPersonRepository.deleteByDisabledPersonId(savedPerson.getId());
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
        if (institution == null) {
            return List.of();
        }
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
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long id, String newPassword) throws InvalidPasswordException {
        PasswordVerifier verifier = findPasswordVerifier()
                .orElseThrow(() -> new IllegalStateException("Password verifier is not defined"));

        verifier.verify(newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);

        personRepository.updatePasswordById(id, encodedPassword);
    }

    /**
     * Check that an address can become the e-mail of a person: valid format and not already used.
     *
     * @param newEmail the candidate address
     * @throws InvalidEmailException if the address is invalid (or already used: {@code EmailAlreadyExistException})
     */
    public void checkNewEmail(String newEmail) throws InvalidEmailException {
        PersonDTO probe = new PersonDTO();
        probe.setEmail(newEmail);
        for (PersonDataVerifier verifier : verifiers) {
            if (verifier instanceof EmailVerifier) {
                verifier.setForCreation(true);
                try {
                    verifier.verify(probe);
                } catch (InvalidEmailException e) {
                    throw e;
                } catch (Exception e) {
                    throw new InvalidEmailException(e.getMessage());
                }
            }
        }
    }

    /**
     * Replace the e-mail of a person, after checking the new one with {@link #checkNewEmail(String)}.
     *
     * @param id       the person id
     * @param newEmail the new address
     * @throws InvalidEmailException if the address is invalid or already used
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(Long id, String newEmail) throws InvalidEmailException {
        checkNewEmail(newEmail);
        personRepository.updateEmailById(id, newEmail);
    }

    /**
     * Flags the e-mail address of a person as verified, e.g. once they used a link sent to it.
     *
     * @param id the person id
     */
    @Transactional
    public void markEmailVerified(Long id) {
        personRepository.markMailVerifiedById(id);
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
     * Find a Person by its email, as a DTO.
     *
     * @param email The email of the person to find.
     * @return An Optional containing the PersonDTO if found, or empty if not found.
     */
    public Optional<PersonDTO> findDtoByEmail(String email) {
        return findByEmail(email).map(personMapper::convert);
    }

    /**
     * Find a Person by its username, email, or first/last name. Case-insensitive. Uses the pg_trgm
     * extension for fuzzy matching.
     *
     * @param usernameOrMailInput The username, email, or name of the person to find.
     * @return An Optional containing the Person if found, or empty if not found.
     */
    public List<PersonDTO> findClosestByUsernameOrEmail(String usernameOrMailInput) {
        if (usernameOrMailInput == null || usernameOrMailInput.isBlank()) {
            return List.of();
        }

        Set<Person> result = new HashSet<>();
        result.addAll(personRepository.findClosestByEmailLimit10(usernameOrMailInput));
        result.addAll(personRepository.findClosestByUsernameLimit10(usernameOrMailInput));
        result.addAll(personRepository.findClosestByNameLimit10(usernameOrMailInput));

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
    @Transactional(rollbackFor = Exception.class)
    public void registerInvitedPerson(PersonDTO person, String password) throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        enableAndUpdatePerson(person, password);
        pendingPersonRepository.deleteByDisabledPersonId(person.getId());
    }

    public Optional<PersonDTO> findByUsername(String username) {
        return personRepository.findByUsernameIgnoreCase(username)
                .map(personMapper::convert);
    }

    private static final int USERNAME_RANDOM_SUFFIX_MAX_ATTEMPTS = 20;
    private static final int MAX_SEARCH_RESULTS = 100;

    /**
     * Builds a username from the person's first/last name (falling back to their e-mail's local part,
     * then to "user" when both are blank), picking one that is actually free: when the plain
     * "firstname.lastname" form is already taken, appends a random numeric suffix and retries.
     *
     * @param firstName         the person's first name, may be blank
     * @param lastName          the person's last name, may be blank
     * @param emailFallback     their e-mail, used to derive a username only when both names are blank
     * @param reservedUsernames usernames (case-insensitive) to also treat as taken in addition to the
     *                          database - lets a caller avoid collisions within a batch of not-yet-persisted
     *                          drafts (e.g. several rows of the same bulk CSV import) before any of them
     *                          actually reaches the database
     * @return a username guaranteed not to collide with the database or {@code reservedUsernames}
     */
    public String generateUniqueUsername(String firstName, String lastName, String emailFallback, Set<String> reservedUsernames) {
        String rawBase = usernameBase(firstName, lastName, emailFallback);
        String base = rawBase.length() > Person.USERNAME_MAX_LENGTH
                ? rawBase.substring(0, Person.USERNAME_MAX_LENGTH) : rawBase;
        if (isUsernameFree(base, reservedUsernames)) {
            return base;
        }

        int maxBaseLength = Person.USERNAME_MAX_LENGTH - 5; // leaves room for up to a 4-digit suffix
        String truncatedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;

        for (int attempt = 0; attempt < USERNAME_RANDOM_SUFFIX_MAX_ATTEMPTS; attempt++) {
            String candidate = truncatedBase + (100 + SECURE_RANDOM.nextInt(9900));
            if (isUsernameFree(candidate, reservedUsernames)) {
                return candidate;
            }
        }

        // Astronomically unlikely to be reached, but guarantees termination with a unique username.
        String candidate;
        do {
            candidate = truncatedBase + SECURE_RANDOM.nextInt(1_000_000);
        } while (!isUsernameFree(candidate, reservedUsernames));
        return candidate;
    }

    private boolean isUsernameFree(String username, Set<String> reservedUsernames) {
        if (reservedUsernames != null && reservedUsernames.stream().anyMatch(username::equalsIgnoreCase)) {
            return false;
        }
        return findByUsername(username).isEmpty();
    }

    private static String usernameBase(String firstName, String lastName, String emailFallback) {
        String base = Stream.of(firstName, lastName)
                .map(PersonService::sanitizeUsernamePart)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("."));
        if (!base.isBlank()) {
            return base;
        }
        String emailLocal = emailFallback != null && emailFallback.contains("@")
                ? emailFallback.substring(0, emailFallback.indexOf('@'))
                : emailFallback;
        String sanitized = sanitizeUsernamePart(emailLocal);
        return sanitized.isBlank() ? "user" : sanitized;
    }

    private static String sanitizeUsernamePart(String s) {
        if (s == null) {
            return "";
        }
        String withoutDiacritics = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String lettersDigitsDots = withoutDiacritics.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]", "")
                .replaceAll("\\.{2,}", ".");
        int start = 0;
        while (start < lettersDigitsDots.length() && lettersDigitsDots.charAt(start) == '.') {
            start++;
        }
        int end = lettersDigitsDots.length();
        while (end > start && lettersDigitsDots.charAt(end - 1) == '.') {
            end--;
        }
        return lettersDigitsDots.substring(start, end);
    }

    /**
     * Find the persons of an institution whose firstname, lastname or email contains the given query.
     * <p>
     * Comparison is case- and accent-insensitive, and the result is capped to the first
     * {@value #MAX_SEARCH_RESULTS} matches as this backs an autocomplete.
     *
     * @param query       The searched text; when null or blank, every person of the institution matches
     * @param institution The institution the search is scoped to
     * @return The matching persons, or an empty list when no institution is given
     */
    public List<PersonDTO> findContainingByNameOrEmailInInstitution(String query, InstitutionDTO institution) {
        if (institution == null) {
            return List.of();
        }

        Specification<Person> matchingQuery = Specification
                .where(PersonSpec.firstNameOrLastNameContainsIgnoreCase(query))
                .or(PersonSpec.emailContainsIgnoreCase(query));

        Specification<Person> spec = Specification
                .where(PersonSpec.isInInstitution(institution))
                .and(matchingQuery);

        return personRepository
                .findAll(spec, PageRequest.of(0, MAX_SEARCH_RESULTS))
                .map(personMapper::convert)
                .toList();
    }
}
