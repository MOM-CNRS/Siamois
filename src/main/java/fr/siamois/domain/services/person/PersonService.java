package fr.siamois.domain.services.person;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionScopeType;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.person.verifier.PasswordVerifier;
import fr.siamois.domain.services.person.verifier.PersonDataVerifier;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    private final PersonRepository personRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final List<PersonDataVerifier> verifiers;
    private final List<PasswordVerifier> passVerifiers;
    private final PersonSettingsRepository personSettingsRepository;
    private final InstitutionService institutionService;
    private final LangService langService;
    private final PendingPersonRepository pendingPersonRepository;
    private final PendingPersonService pendingPersonService;
    private final ConversionService conversionService;
    private final fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository pendingInstitutionInviteRepository;
    private final InstitutionMapper institutionMapper;
    private final ActionUnitMapper actionUnitMapper;
    private final PersonMapper personMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;
    private final ProfileMapper profileMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    private void createAndDeletePendingRelations(PendingPerson pendingPerson, Person person) {
        Set<PendingInstitutionInvite> institutionInvites = pendingInstitutionInviteRepository.findAllByPendingPerson(pendingPerson);
        PersonDTO personDTO = personMapper.convert(person);
        for (PendingInstitutionInvite invite : institutionInvites) {
            InstitutionDTO institution = institutionMapper.convert(invite.getInstitution());

            List<ProfileDTO> institutionProfiles = new ArrayList<>();
            Map<ActionUnit, List<ProfileDTO>> projectProfiles = new HashMap<>();
            for (Profile profile : invite.getProfiles()) {
                if (profile.getScope() == PermissionScopeType.PROJECT && profile.getActionUnit() != null) {
                    projectProfiles.computeIfAbsent(profile.getActionUnit(), unit -> new ArrayList<>())
                            .add(profileMapper.convert(profile));
                } else {
                    institutionProfiles.add(profileMapper.convert(profile));
                }
            }

            personProfileAssignmentService.addToInstitution(institution, personDTO, institutionProfiles);
            projectProfiles.forEach((actionUnit, profiles) ->
                    personProfileAssignmentService.addToProjectMembers(actionUnitMapper.convert(actionUnit), personDTO, profiles));

            pendingPersonService.delete(invite);
        }
    }

    private void managePendingInvites(Person savedPerson) {
        PendingPerson p = pendingPersonService.createOrGetPendingPerson(savedPerson.getEmail());
        createAndDeletePendingRelations(p, savedPerson);
        pendingPersonRepository.delete(p);
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

        managePendingInvites(person);

        return conversionService.convert(person, PersonDTO.class);
    }

    /**
     * Create a new disabled Person invited to register: the account stays disabled until the person
     * creates their own password through the registration link sent by email. The temporary
     * password is optional; when blank, a random one is generated so the account cannot be used
     * before the registration is completed, the account is also set as not enabled
     *
     * @param personDTO The Person to create.
     * @param password  The optional temporary plain password; may be null or blank.
     * @return The created Person with its ID set.
     * @throws InvalidUsernameException  if the username is invalid or already exists.
     * @throws InvalidEmailException     if the email is invalid or already exists.
     * @throws UserAlreadyExistException if a user with the same username or email already exists.
     * @throws InvalidPasswordException  if the given password does not meet the required criteria.
     * @throws InvalidNameException      if the name is invalid or does not meet the required criteria.
     */
    public PersonDTO createInvitedPerson(PersonDTO personDTO, String password) throws InvalidUsernameException,
            InvalidEmailException,
            UserAlreadyExistException,
            InvalidPasswordException,
            InvalidNameException {
        personDTO.setId(-1L);

        checkPersonData(personDTO, true);

        String effectivePassword = password;
        if (password == null || password.isBlank()) {
            effectivePassword = generateRandomPassword();
        } else {
            checkPassword(password);
        }

        Person person = conversionService.convert(personDTO, Person.class);
        assert person != null;
        person.setPassword(passwordEncoder.encode(effectivePassword));
        person.setPassToModify(true);
        person.setEnabled(!StringUtils.isBlank(password));

        person = personRepository.save(person);

        return conversionService.convert(person, PersonDTO.class);
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Activate an invited person's account once they created their password through the
     * registration link: sets the new password, enables the account and applies then removes
     * any pending invitations for their email.
     *
     * @param personId    The ID of the person to activate.
     * @param newPassword The plain password created by the person.
     * @throws InvalidPasswordException if the password does not meet the required criteria.
     */
    @Transactional
    public void activatePerson(Long personId, String newPassword) throws InvalidPasswordException {
        checkPassword(newPassword);

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        person.setPassword(passwordEncoder.encode(newPassword));
        person.setEnabled(true);
        person.setPassToModify(false);
        person = personRepository.save(person);

        managePendingInvites(person);
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
}
