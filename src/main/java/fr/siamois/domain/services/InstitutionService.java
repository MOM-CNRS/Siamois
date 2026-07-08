package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.InstitutionSettingsRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing institutions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final InstitutionSettingsRepository institutionSettingsRepository;
    private final PersonRepository personRepository;
    private final VocabularyService vocabularyService;
    private final FieldConfigurationService fieldConfigurationService;
    private final PersonMapper personMapper;
    private final InstitutionMapper institutionMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileService profileService;
    private final ProfileMapper profileMapper;

    /**
     * Finds an institution by its identifier.
     *
     * @param id the identifier of the institution to find
     * @return the institution if found, or null if not found
     */
    @Transactional(readOnly = true)
    public InstitutionDTO findById(Long id) {
        Optional<Institution> institutionOpt = institutionRepository.findById(id);
        if (institutionOpt.isEmpty()) {
            return null;
        }

        Institution institution = institutionOpt.get();
        return institutionMapper.convert(institution);
    }

    /**
     * Find all institutions in the system.
     *
     * @return a set of all institutions as DTOs
     */
    public Set<InstitutionDTO> findAll() {
        Iterable<Institution> institutions = institutionRepository.findAll();
        Set<InstitutionDTO> institutionDTOs = new HashSet<>();

        institutions.forEach(institution ->
                institutionDTOs.add(institutionMapper.convert(institution))
        );

        return institutionDTOs;
    }

    /**
     * Finds all institutions that a person is allowed to display, based on the
     * profile permission system: an INSTANCE-scoped profile with an organization
     * access permission grants every institution, an ORGANISATION-scoped profile
     * with {@link PermissionConstants#ORGANIZATION_ACCESS} grants its institution,
     * and a PROJECT-scoped profile grants the institution owning its action unit.
     *
     * @param person the person whose visible institutions to find
     * @return a set of institutions the person can display, as DTOs
     */
    public Set<InstitutionDTO> findInstitutionsOfPerson(PersonDTO person) {
        Set<Institution> institutions = institutionRepository.findAllVisibleToPerson(
                person.getId());

        return institutions.stream()
                .map(institutionMapper::convert)
                .collect(Collectors.toSet());
    }


    /**
     * Creates a new institution.
     *
     * @param institution the institution to create
     * @return the created institution
     * @throws InstitutionAlreadyExistException if an institution with the same identifier already exists
     * @throws FailedInstitutionSaveException   if there is an error while saving the institution
     */
    public InstitutionDTO createInstitution(InstitutionDTO institution, String thesaurusUrl) throws InstitutionAlreadyExistException, FailedInstitutionSaveException, InvalidEndpointException, NotSiamoisThesaurusException {
        Optional<Institution> existing = institutionRepository.findInstitutionByIdentifier(institution.getIdentifier());
        institution.setCreationDate(OffsetDateTime.now(ZoneOffset.UTC));
        if (existing.isPresent())
            throw new InstitutionAlreadyExistException("Institution with code " + institution.getIdentifier() + " already exists");

        // Récuperation ou création du vocabulaire
        Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(thesaurusUrl);

        try {
            // Création de l'institution et préparation des concepts du thésaurus sélectionnés
            // Verifier que le thesaurus soit compatible

            Institution i = institutionRepository.save(Objects.requireNonNull(institutionMapper.invertConvert(institution)));
            fieldConfigurationService.setupFieldConfigurationForInstitution(Objects.requireNonNull(institutionMapper.convert(i)), vocabulary);
            InstitutionDTO institutionDTO = institutionMapper.convert(i);
            createInstitutionProfiles(institutionDTO);
            return  institutionDTO;
        } catch (NotSiamoisThesaurusException e) {
            log.error("The thesaurus is not a siamois thesaurus : {}", thesaurusUrl, e);
            throw e;
        } catch (Exception e) {
            log.error("Error while saving institution", e);
            throw new FailedInstitutionSaveException("Failed to save institution");
        }
    }

    private void createInstitutionProfiles(InstitutionDTO institutionDTO) {
        profileService.createOrGetOrganizationManagerProfile(institutionDTO);
        profileService.createOrGetOrganizationProjectManagerProfile(institutionDTO);
        profileService.createOrGetOrganizationMemberProfile(institutionDTO);
    }


    /**
     * Finds all members of a given action unit, i.e. all persons holding a
     * PROJECT-scoped profile on the action unit, including the author.
     *
     * @param actionUnit the action unit whose members to find
     * @return a set of persons who are members of the action unit
     */
    @Transactional(readOnly = true)
    public Set<PersonDTO> findMembersOf(ActionUnitDTO actionUnit) {
        Set<PersonDTO> members = personProfileAssignmentRepository
                .findAllPersonsByProfileActionUnitId(actionUnit.getId())
                .stream()
                .map(personMapper::convert)
                .collect(Collectors.toCollection(HashSet::new));

        if (actionUnit.getCreatedBy() != null) {
            members.add(actionUnit.getCreatedBy());
        }

        return members;
    }

    /**
     * Creates or retrieves the settings for a given institution.
     *
     * @param institution the institution for which to create or retrieve settings
     * @return the institution settings
     */
    public InstitutionSettings createOrGetSettingsOf(InstitutionDTO institution) {
        Optional<InstitutionSettings> opt = institutionSettingsRepository.findById(institution.getId());
        if (opt.isPresent()) return opt.get();
        InstitutionSettings empty = new InstitutionSettings();
        empty.setInstitution(institutionRepository.findById(institution.getId()).orElse(null));
        return saveSettings(empty);
    }

    /**
     * Saves the settings for a given institution.
     *
     * @param settings the institution settings to save
     * @return the saved institution settings
     */
    public InstitutionSettings saveSettings(InstitutionSettings settings) {
        return institutionSettingsRepository.save(settings);
    }

    /**
     * Adds a person to the managers of an institution.
     *
     * @param institution the institution to which the person will be added as a manager
     * @param person      the person to add as a manager
     * @return true if the person was added successfully, false if they were already a manager
     */
    @Transactional
    public boolean addToManagers(InstitutionDTO institution, PersonDTO person) {
        Profile member = profileService.createOrGetOrganizationMemberProfile(institution);
        Profile institutionManager = profileService.createOrGetOrganizationManagerProfile(institution);
        List<ProfileDTO> profiles =  new ArrayList<>();
        profiles.add(profileMapper.convert(member));
        profiles.add(profileMapper.convert(institutionManager));
        return personProfileAssignmentService.addToInstitution(institution, person, profiles) != null;
    }


    /**
     * Checks if a person is a manager of a given institution.
     *
     * @param institution the institution to check
     * @param person      the person to check
     * @return true if the person is a manager of the institution, false otherwise
     */
    public boolean isManagerOf(InstitutionDTO institution, PersonDTO person) {
        return institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId());
    }

    /**
     * Updates an institution.
     *
     * @param institution the institution to update
     * @return the updated institution
     */
    public InstitutionDTO update(InstitutionDTO institution) {
        return institutionMapper.convert(institutionRepository.save(
                institutionMapper.invertConvert(institution)
        ));
    }

    /**
     * Counts the number of members in a given institution.
     *
     * @param institution the institution for which to count members
     * @return the number of members in the institution
     */
    public long countMembersInInstitution(InstitutionDTO institution) {
        return personRepository.countPersonsInInstitution(institution.getId());
    }

    /**
     * Checks if a person is associated with a given institution, i.e. holds any
     * profile (ORGANISATION or PROJECT scoped) attached to the institution.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is associated with the institution, false otherwise
     */
    public boolean personIsInInstitution(PersonDTO person, InstitutionDTO institution) {
        return personProfileAssignmentRepository.personHasAnyProfileInInstitution(
                person.getId(), institution.getId());
    }

    /**
     * Finds all action managers of a given institution, i.e. all persons holding
     * the {@link ProfileConstants#ORGANIZATION_PROJECT_MANAGER} profile of the institution.
     *
     * @param institution the institution for which to find action managers
     * @return a set of persons managing the actions of the institution
     */
    @Transactional(readOnly = true)
    public Set<PersonDTO> findAllActionManagersOf(InstitutionDTO institution) {
        return personProfileAssignmentRepository
                .findAllPersonsByProfileCodeAndInstitutionId(
                        ProfileConstants.ORGANIZATION_PROJECT_MANAGER, institution.getId())
                .stream()
                .map(personMapper::convert)
                .collect(Collectors.toSet());
    }

    /**
     * Checks if a person is an institution manager.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is an institution manager, false otherwise
     */
    public boolean personIsInstitutionManager(PersonDTO person, InstitutionDTO institution) {
        return institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId());
    }

    /**
     * Checks if a person is an action manager for a given institution.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is an action manager, false otherwise
     */
    public boolean personIsActionManager(PersonDTO person, InstitutionDTO institution) {
        return personProfileAssignmentRepository.personHasProfileWithCodeInInstitution(
                person.getId(), institution.getId(), ProfileConstants.ORGANIZATION_PROJECT_MANAGER);
    }

    /**
     * Checks if a person is either an institution manager or an action manager for a given institution.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is either an institution manager or an action manager, false otherwise
     */
    public boolean personIsInstitutionManagerOrActionManager(PersonDTO person, InstitutionDTO institution) {
        return personIsInstitutionManager(person, institution) || personIsActionManager(person, institution);
    }

    /**
     * Adds a person to the action manager of a given institution.
     *
     * @param institution the institution to which the person will be added as an action manager
     * @param person      the person to add as an action manager
     * @return true if the person was added successfully, false if they were already an action manager
     */
    public boolean addPersonToActionManager(InstitutionDTO institution, PersonDTO person) {
        return personProfileAssignmentService.addToActionManagers(institution, person);
    }


    public boolean addPersonAsMemberOfActionUnit(ActionUnitDTO actionUnitDTO, PersonDTO personDTO) {
        return personProfileAssignmentService.addToProjectMembers(actionUnitDTO, personDTO);
    }

    @Transactional(readOnly = true)
    public Set<PersonDTO> findManagersOf(InstitutionDTO institution) {
        return personRepository.findManagersOfInstitution(institution.getId())
                .stream()
                .map(personMapper::convert)
                .collect(Collectors.toSet());
    }
}
