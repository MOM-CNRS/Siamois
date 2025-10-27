package fr.siamois.domain.services;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.team.ActionManagerRelation;
import fr.siamois.domain.models.team.TeamMemberRelation;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.GlobalFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.InstitutionSettingsRepository;
import fr.siamois.infrastructure.database.repositories.team.ActionManagerRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private InstitutionSettingsRepository institutionSettingsRepository;
    @Mock
    private ActionManagerRepository actionManagerRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private VocabularyService vocabularyService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;

    @InjectMocks
    private InstitutionService institutionService;

    private Institution institution1, institution2;
    private Person manager;
    private ActionUnit actionUnit;

    @BeforeEach
    void setUp() {
        manager = new Person();
        manager.setId(1L);
        manager.setUsername("Username");

        institution1 = new Institution();
        institution1.setId(-1L);
        institution1.setName("First name");
        institution1.setIdentifier("123456");
        institution1.getManagers().add(manager);

        institution2 = new Institution();
        institution2.setId(-2L);
        institution2.setName("Second name");
        institution2.setIdentifier("0987654");

        actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setCreatedBy(manager);
        actionUnit.setCreatedByInstitution(institution1);
    }

    @Test
    void findAll() {
        when(institutionRepository.findAll()).thenReturn(List.of(institution1, institution2));

        Set<Institution> result = institutionService.findAll();

        assertThat(result).containsExactlyInAnyOrder(institution1, institution2);
    }

    @Test
    void createInstitution_throwsInstitutionAlreadyExist() {
        when(institutionRepository.findInstitutionByIdentifier("123456")).thenReturn(Optional.of(institution1));
        assertThrows(InstitutionAlreadyExistException.class, () -> institutionService.createInstitution(institution1, "invalid_url"));
    }

    @Test
    void createInstitution_throwsFailedInstitutionSaveException() throws InvalidEndpointException {

        // Mock
        when(institutionRepository.save(institution1)).thenThrow(new RuntimeException("Error while saving institution"));
        Vocabulary fakeVocabulary = new Vocabulary();
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenReturn(fakeVocabulary);

        assertThrows(FailedInstitutionSaveException.class, () -> institutionService.createInstitution(institution1, "valid_url"));
    }

    @Test
    void createInstitution() throws InstitutionAlreadyExistException, FailedInstitutionSaveException, InvalidEndpointException, NotSiamoisThesaurusException, ErrorProcessingExpansionException {

        Vocabulary fakeVocabulary = new Vocabulary();
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenReturn(fakeVocabulary);
        when(fieldConfigurationService
                .setupFieldConfigurationForInstitution(any(Institution.class), any(Vocabulary.class))).thenReturn(Optional.of(mock(GlobalFieldConfig.class)));
        when(institutionRepository.save(any(Institution.class))).thenReturn(mock(Institution.class));

        institutionService.createInstitution(institution1, "valid_url");

        verify(institutionRepository, times(1)).save(institution1);
    }

    @Test
    void createInstitution_throwsInvalidEndpointException() throws InvalidEndpointException {

        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenThrow(new InvalidEndpointException("Invalid url"));

        assertThrows(InvalidEndpointException.class, () -> institutionService.createInstitution(institution1, "invalid_url"));
    }

    @Test
    void addUserToInstitution() throws FailedInstitutionSaveException {
        Concept realRole = new Concept();
        realRole.setId(1L);

        institutionService.addUserToInstitution(manager, institution1, realRole);

        verify(personRepository, times(1)).addPersonToInstitution(manager.getId(), institution1.getId(), realRole.getId());
    }

    @Test
    void addUserToInstitution_throwsFailedInstitutionSaveException() {
        Concept realRole = new Concept();
        realRole.setId(1L);

        doThrow(new RuntimeException("Error while adding person to institution"))
                .when(personRepository)
                .addPersonToInstitution(manager.getId(), institution1.getId(), realRole.getId());

        assertThrows(FailedInstitutionSaveException.class, () ->
                institutionService.addUserToInstitution(manager, institution1, realRole));
    }

    @Test
    void createOrGetSettingsOf_shouldReturnSettings_whenSet() {
        Institution institution = new Institution();
        institution.setId(1L);

        InstitutionSettings settings = new InstitutionSettings();
        settings.setInstitution(institution);
        settings.setArkNaan("66666");

        when(institutionSettingsRepository.findById(institution.getId())).thenReturn(Optional.of(settings));

        InstitutionSettings result = institutionService.createOrGetSettingsOf(institution);

        assertThat(result).isEqualTo(settings);
    }

    @Test
    void createOrGetSettingsOf_shouldReturnEmptySettings_whenNotSet() {
        Institution institution = new Institution();
        institution.setId(1L);

        when(institutionSettingsRepository.findById(institution.getId())).thenReturn(Optional.empty());
        when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));
        when(institutionSettingsRepository.save(any(InstitutionSettings.class)))
                .then(invocation -> invocation.getArgument(0, InstitutionSettings.class));

        InstitutionSettings result = institutionService.createOrGetSettingsOf(institution);

        assertThat(result.getInstitution()).isEqualTo(institution);
    }

    @Test
    void isManagerOf_whenIsOwnerOfInstitution_shouldReturnTrue() {
        Person person = new Person();
        person.setUsername("username");
        person.setEmail("test@example.com");
        person.setPassword("password123");
        person.setId(12L);

        Institution institution = new Institution();
        institution.setId(2L);
        institution.setName("institution");
        institution.getManagers().add(person);

        when(institutionRepository.personIsInstitutionManagerOf(any(Long.class), any(Long.class))).thenReturn(true);

        boolean result = institutionService.isManagerOf(institution, person);

        assertThat(result).isTrue();
    }

    @Test
    void update() {
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Updated Institution");

        when(institutionRepository.save(institution)).thenReturn(institution);

        Institution result = institutionService.update(institution);

        assertThat(result).isEqualTo(institution);
        verify(institutionRepository, times(1)).save(institution);
    }

    @Test
    void findInstitutionsOfPerson_shouldReturnAllInstitutions() {
        Person person = new Person();
        person.setId(1L);

        when(institutionRepository.findAllAsMember(person.getId())).thenReturn(Set.of(institution1));
        when(institutionRepository.findAllAsActionManager(person.getId())).thenReturn(Set.of(institution2));
        when(institutionRepository.findAllAsInstitutionManager(person.getId())).thenReturn(Set.of(institution1, institution2));

        Set<Institution> result = institutionService.findInstitutionsOfPerson(person);

        assertThat(result).containsExactlyInAnyOrder(institution1, institution2);
    }

    @Test
    void findRelationsOf_shouldReturnAllRelations() {
        actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setCreatedBy(manager);

        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Updated Institution");
        institution.setIdentifier("123456");

        actionUnit.setCreatedByInstitution(institution);

        TeamMemberRelation relation = new TeamMemberRelation(actionUnit, manager);

        when(teamMemberRepository.findAllByActionUnit(actionUnit)).thenReturn(new HashSet<>(Set.of(relation)));

        Set<TeamMemberRelation> result = institutionService.findRelationsOf(actionUnit);

        assertThat(result).contains(relation);
    }

    @Test
    void findMembersOf_shouldReturnAllMembers() {
        actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setCreatedBy(manager);

        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Updated Institution");

        actionUnit.setCreatedByInstitution(institution);

        TeamMemberRelation relation = new TeamMemberRelation(actionUnit, manager);

        when(teamMemberRepository.findAllByActionUnit(actionUnit)).thenReturn(new HashSet<>(Set.of(relation)));

        Set<Person> result = institutionService.findMembersOf(actionUnit);

        assertThat(result).containsExactlyInAnyOrder(manager);
    }

    @Test
    void addToManagers_shouldAddManagerAndReturnTrue() {
        // given
        Institution inst = new Institution();
        inst.setId(1L);

        Person p = new Person();
        p.setId(2L);

        when(institutionRepository.findById(1L)).thenReturn(Optional.of(inst));
        when(personRepository.getReferenceById(2L)).thenReturn(p);

        // when
        boolean added = institutionService.addToManagers(inst, p);

        // then
        assertTrue(added);                       // return value is true
        assertTrue(inst.getManagers().contains(p)); // person is in managers set
    }

    @Test
    void addToManagers_shouldNotAddManagerIfAlreadyExists() {
        // given
        Institution inst = new Institution();
        inst.setId(1L);

        Person p = new Person();
        p.setId(2L);

        inst.getManagers().add(p);

        when(institutionRepository.findById(1L)).thenReturn(Optional.of(inst));
        when(personRepository.getReferenceById(2L)).thenReturn(p);

        // when
        boolean added = institutionService.addToManagers(inst, p);

        // then
        assertTrue(!added);                       // return value is true
        assertTrue(inst.getManagers().contains(p)); // person is in managers set
    }

    @Test
    void countMembersInInstitution_shouldReturnCorrectCount() {
        Institution institution = new Institution();
        institution.setId(1L);

        when(personRepository.countPersonsInInstitution(institution.getId())).thenReturn(2L);

        long result = institutionService.countMembersInInstitution(institution);

        assertThat(result).isEqualTo(2);
    }

    @Test
    void personIsInInstitution_shouldReturnTrueIfActionManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution))
                .thenReturn(Optional.of(new ActionManagerRelation(institution, person)));

        boolean result = institutionService.personIsInInstitution(person, institution);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInInstitution_shouldReturnTrueIfTeamMember() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution)).thenReturn(Optional.empty());
        when(teamMemberRepository.personIsInInstitution(person.getId(), institution.getId())).thenReturn(true);

        boolean result = institutionService.personIsInInstitution(person, institution);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInInstitution_shouldReturnFalseIfNotInInstitution() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution)).thenReturn(Optional.empty());
        when(teamMemberRepository.personIsInInstitution(person.getId(), institution.getId())).thenReturn(false);

        boolean result = institutionService.personIsInInstitution(person, institution);

        assertThat(result).isFalse();
    }

    @Test
    void findAllActionManagersOf_shouldReturnAllActionManagers() {
        Institution institution = new Institution();
        institution.setId(1L);

        ActionManagerRelation relation = new ActionManagerRelation(institution, manager);

        when(actionManagerRepository.findAllByInstitution(institution)).thenReturn(Set.of(relation));

        Set<ActionManagerRelation> result = institutionService.findAllActionManagersOf(institution);

        assertThat(result).containsExactlyInAnyOrder(relation);
    }

    @Test
    void personIsInstitutionManager_shouldReturnTrueIfManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(true);

        boolean result = institutionService.personIsInstitutionManager(person, institution);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInstitutionManager_shouldReturnFalseIfNotManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(false);

        boolean result = institutionService.personIsInstitutionManager(person, institution);

        assertThat(result).isFalse();
    }

    @Test
    void personIsActionManager_shouldReturnTrueIfActionManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution))
                .thenReturn(Optional.of(new ActionManagerRelation(institution, person)));

        boolean result = institutionService.personIsActionManager(person, institution);

        assertThat(result).isTrue();
    }

    @Test
    void personIsActionManager_shouldReturnFalseIfNotActionManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution)).thenReturn(Optional.empty());

        boolean result = institutionService.personIsActionManager(person, institution);

        assertThat(result).isFalse();
    }

    @Test
    void personIsInstitutionManagerOrActionManager_shouldReturnTrueIfManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(true);

        boolean result = institutionService.personIsInstitutionManagerOrActionManager(person, institution);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInstitutionManagerOrActionManager_shouldReturnTrueIfActionManager() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(false);
        when(actionManagerRepository.findByPersonAndInstitution(person, institution))
                .thenReturn(Optional.of(new ActionManagerRelation(institution, person)));

        boolean result = institutionService.personIsInstitutionManagerOrActionManager(person, institution);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInstitutionManagerOrActionManager_shouldReturnFalseIfNeither() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(false);
        when(actionManagerRepository.findByPersonAndInstitution(person, institution)).thenReturn(Optional.empty());

        boolean result = institutionService.personIsInstitutionManagerOrActionManager(person, institution);

        assertThat(result).isFalse();
    }

    @Test
    void addPersonToActionManager_shouldAddManagerAndReturnTrue() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution)).thenReturn(Optional.empty());

        boolean result = institutionService.addPersonToActionManager(institution, person);

        assertThat(result).isTrue();
        verify(actionManagerRepository, times(1)).save(any(ActionManagerRelation.class));
    }

    @Test
    void addPersonToActionManager_shouldNotAddManagerIfAlreadyExists() {
        Institution institution = new Institution();
        institution.setId(1L);

        Person person = new Person();
        person.setId(1L);

        when(actionManagerRepository.findByPersonAndInstitution(person, institution))
                .thenReturn(Optional.of(new ActionManagerRelation(institution, person)));

        boolean result = institutionService.addPersonToActionManager(institution, person);

        assertThat(result).isFalse();
        verify(actionManagerRepository, never()).save(any(ActionManagerRelation.class));
    }

    @Test
    void addPersonToActionUnit_shouldAddMemberAndReturnTrue() {
        actionUnit = new ActionUnit();
        actionUnit.setId(1L);

        Person person = new Person();
        person.setId(1L);

        Concept role = new Concept();
        role.setId(1L);

        when(teamMemberRepository.findByActionUnitAndPerson(actionUnit, person)).thenReturn(Optional.empty());

        boolean result = institutionService.addPersonToActionUnit(actionUnit, person, role);

        assertThat(result).isTrue();
        verify(teamMemberRepository, times(1)).save(any(TeamMemberRelation.class));
    }

    @Test
    void addPersonToActionUnit_shouldNotAddMemberIfAlreadyExists() {
        actionUnit = new ActionUnit();
        actionUnit.setId(1L);

        Person person = new Person();
        person.setId(1L);

        Concept role = new Concept();
        role.setId(1L);

        when(teamMemberRepository.findByActionUnitAndPerson(actionUnit, person))
                .thenReturn(Optional.of(new TeamMemberRelation(actionUnit, person)));

        boolean result = institutionService.addPersonToActionUnit(actionUnit, person, role);

        assertThat(result).isFalse();
        verify(teamMemberRepository, never()).save(any(TeamMemberRelation.class));
    }

    @Test
    void findManagersOf_shouldReturnAllManagers() {
        // given
        Institution inst = new Institution();
        inst.setId(42L);

        Person p1 = new Person();
        p1.setId(1L);
        p1.setUsername("user1");
        Person p2 = new Person();
        p2.setId(2L);
        p2.setUsername("user2");
        Set<Person> repoResult = new HashSet<>(Arrays.asList(p1, p2));

        when(personRepository.findManagersOfInstitution(42L)).thenReturn(repoResult);

        // when
        Set<Person> result = institutionService.findManagersOf(inst);

        // then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(p1));
        assertTrue(result.contains(p2));

        verify(personRepository, times(1)).findManagersOfInstitution(42L);

    }

    @Test
    void findById_success() {
        Institution institution = new Institution();
        institution.setId(1L);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        Institution res = institutionService.findById(1L);
        assertThat(institution).isNotNull();
        assertThat(res.getId()).isEqualTo(1L);
    }

    @Test
    void findById_null() {
        when(institutionRepository.findById(1L)).thenReturn(Optional.empty());
        Institution institution = institutionService.findById(1L);
        assertThat(institution).isNull();
    }

}