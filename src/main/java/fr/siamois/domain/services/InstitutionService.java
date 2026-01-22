package fr.siamois.domain.services;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.team.ActionManagerRelation;
import fr.siamois.domain.models.team.TeamMemberRelation;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.InstitutionSettingsRepository;
import fr.siamois.infrastructure.database.repositories.team.ActionManagerRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
    private final TeamMemberRepository teamMemberRepository;
    private final ActionManagerRepository actionManagerRepository;
    private final VocabularyService vocabularyService;
    private final FieldConfigurationService fieldConfigurationService;

    /**
     * Finds an institution by its identifier.
     *
     * @param id the identifier of the institution to find
     * @return the institution if found, or null if not found
     */
    @Transactional(readOnly = true)
    public Institution findById(Long id) {
        Optional<Institution> institution = institutionRepository.findById(id);
        return institution.orElse(null);
    }

    /**
     * Find all institutions in the system.
     *
     * @return a set of all institutions
     */
    public Set<Institution> findAll() {
        Set<Institution> result = new HashSet<>();
        for (Institution institution : institutionRepository.findAll())
            result.add(institution);
        return result;
    }

    /**
     * Finds all institutions that a person is associated with.
     *
     * @param person the person whose institutions to find
     * @return a set of institutions associated with the person
     */
    public Set<Institution> findInstitutionsOfPerson(Person person) {
        Set<Institution> institutions = new HashSet<>();
        institutions.addAll(institutionRepository.findAllAsMember(person.getId()));
        institutions.addAll(institutionRepository.findAllAsActionManager(person.getId()));
        institutions.addAll(institutionRepository.findAllAsInstitutionManager(person.getId()));
        return institutions;
    }

    /**
     * Creates a new institution.
     *
     * @param institution the institution to create
     * @return the created institution
     * @throws InstitutionAlreadyExistException if an institution with the same identifier already exists
     * @throws FailedInstitutionSaveException   if there is an error while saving the institution
     */
    public Institution createInstitution(Institution institution, String thesaurusUrl) throws InstitutionAlreadyExistException, FailedInstitutionSaveException, InvalidEndpointException, NotSiamoisThesaurusException {
        Optional<Institution> existing = institutionRepository.findInstitutionByIdentifier(institution.getIdentifier());
        if (existing.isPresent())
            throw new InstitutionAlreadyExistException("Institution with code " + institution.getIdentifier() + " already exists");

        // Récuperation ou création du vocabulaire
        Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(thesaurusUrl);

        try {
            // Création de l'institution et préparation des concepts du thésaurus sélectionnés
            Institution i = institutionRepository.save(institution);
            fieldConfigurationService.setupFieldConfigurationForInstitution(i, vocabulary);
            return i;
        } catch (NotSiamoisThesaurusException e) {
            log.error("The thesaurus is not a siamois thesaurus : {}", thesaurusUrl, e);
            throw e;
        } catch (Exception e) {
            log.error("Error while saving institution", e);
            throw new FailedInstitutionSaveException("Failed to save institution");
        }
    }


    /**
     * Finds all relations of a given action unit.
     *
     * @param actionUnit the action unit whose relations to find
     * @return a set of team member relations associated with the action unit, including the author
     */
    public Set<TeamMemberRelation> findRelationsOf(ActionUnit actionUnit) {
        Set<TeamMemberRelation> result = teamMemberRepository.findAllByActionUnit(actionUnit);
        Person creator = actionUnit.getCreatedBy();
        Hibernate.unproxy(creator);
        result.add(new TeamMemberRelation(actionUnit, creator));

        for (TeamMemberRelation relation : result) {
            relation.setPerson(Hibernate.unproxy(relation.getPerson(), Person.class));
        }

        return result;
    }

    /**
     * Finds all members of a given action unit.
     *
     * @param actionUnit the action unit whose members to find
     * @return a set of persons who are members of the action unit
     */
    @Transactional
    public Set<Person> findMembersOf(ActionUnit actionUnit) {
        return findRelationsOf(actionUnit)
                .stream()
                .map(TeamMemberRelation::getPerson)
                .collect(Collectors.toSet());
    }

    /**
     * Adds a user to an institution with a specific role.
     *
     * @param person      the person to add to the institution
     * @param institution the institution to which the person will be added
     * @param roleConcept the role concept that defines the person's role in the institution
     * @throws FailedInstitutionSaveException if there is an error while saving the institution
     */
    public void addUserToInstitution(Person person, Institution institution, Concept roleConcept) throws FailedInstitutionSaveException {
        try {
            personRepository.addPersonToInstitution(person.getId(), institution.getId(), roleConcept.getId());
        } catch (Exception e) {
            log.error("Failed to add user to institution", e);
            throw new FailedInstitutionSaveException("Failed to add user to institution");
        }
    }

    /**
     * Creates or retrieves the settings for a given institution.
     *
     * @param institution the institution for which to create or retrieve settings
     * @return the institution settings
     */
    public InstitutionSettings createOrGetSettingsOf(Institution institution) {
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
    public boolean addToManagers(Institution institution, Person person) {
        Institution inst = institutionRepository.findById(institution.getId()).orElseThrow();
        Person p = personRepository.getReferenceById(person.getId());
        return inst.getManagers().add(p); // no explicit save()
    }


    /**
     * Checks if a person is a manager of a given institution.
     *
     * @param institution the institution to check
     * @param person      the person to check
     * @return true if the person is a manager of the institution, false otherwise
     */
    public boolean isManagerOf(Institution institution, Person person) {
        return institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId());
    }

    /**
     * Updates an institution.
     *
     * @param institution the institution to update
     * @return the updated institution
     */
    public Institution update(Institution institution) {
        return institutionRepository.save(institution);
    }

    /**
     * Counts the number of members in a given institution.
     *
     * @param institution the institution for which to count members
     * @return the number of members in the institution
     */
    public long countMembersInInstitution(Institution institution) {
        return personRepository.countPersonsInInstitution(institution.getId());
    }

    /**
     * Checks if a person is associated with a given institution, either as an action manager or as a team member.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is associated with the institution, false otherwise
     */
    public boolean personIsInInstitution(Person person, Institution institution) {
        Optional<ActionManagerRelation> optManager = actionManagerRepository.findByPersonAndInstitution(person, institution);
        if (optManager.isPresent()) {
            return true;
        }

        return teamMemberRepository.personIsInInstitution(person.getId(), institution.getId());
    }

    /**
     * Finds all action managers of a given institution.
     *
     * @param institution the institution for which to find action managers
     * @return a set of action manager relations associated with the institution
     */
    public Set<ActionManagerRelation> findAllActionManagersOf(Institution institution) {
        return actionManagerRepository.findAllByInstitution(institution);
    }

    /**
     * Checks if a person is an institution manager.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is an institution manager, false otherwise
     */
    public boolean personIsInstitutionManager(Person person, Institution institution) {
        return institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId());
    }

    /**
     * Checks if a person is an action manager for a given institution.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is an action manager, false otherwise
     */
    public boolean personIsActionManager(Person person, Institution institution) {
        return actionManagerRepository.findByPersonAndInstitution(person, institution).isPresent();
    }

    /**
     * Checks if a person is either an institution manager or an action manager for a given institution.
     *
     * @param person      the person to check
     * @param institution the institution to check against
     * @return true if the person is either an institution manager or an action manager, false otherwise
     */
    public boolean personIsInstitutionManagerOrActionManager(Person person, Institution institution) {
        return personIsInstitutionManager(person, institution) || personIsActionManager(person, institution);
    }

    /**
     * Adds a person to the action manager of a given institution.
     *
     * @param institution the institution to which the person will be added as an action manager
     * @param person      the person to add as an action manager
     * @return true if the person was added successfully, false if they were already an action manager
     */
    public boolean addPersonToActionManager(Institution institution, Person person) {
        Optional<ActionManagerRelation> optRelation = actionManagerRepository.findByPersonAndInstitution(person, institution);
        if (optRelation.isPresent())
            return false;

        ActionManagerRelation relation = new ActionManagerRelation(institution, person);
        actionManagerRepository.save(relation);

        return true;
    }

    /**
     * Adds a person to an action unit with a specific role.
     *
     * @param actionUnit the action unit to which the person will be added
     * @param person     the person to add to the action unit
     * @param role       the role concept that defines the person's role in the action unit
     * @return true if the person was added successfully, false if they were already a member of the action unit
     */
    public boolean addPersonToActionUnit(ActionUnit actionUnit, Person person, Concept role) {
        Optional<TeamMemberRelation> optRelation = teamMemberRepository.findByActionUnitAndPerson(actionUnit, person);
        if (optRelation.isPresent()) {
            log.warn("Person {} is already a member of action unit {}", person.getId(), actionUnit.getId());
            return false;
        }
        TeamMemberRelation relation = new TeamMemberRelation(actionUnit, person);
        relation.setRole(role);

        teamMemberRepository.save(relation);

        return true;
    }

    /**
     * Finds all managers of a given institution.
     *
     * @param institution the institution for which to find managers
     * @return a set of persons who are managers of the institution
     */
    @Transactional(readOnly = true)
    public Set<Person> findManagersOf(Institution institution) {
        return personRepository.findManagersOfInstitution(institution.getId());
    }
}
