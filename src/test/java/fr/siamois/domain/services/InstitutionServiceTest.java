package fr.siamois.domain.services;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.vocabulary.FeedbackFieldConfig;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.InstitutionSettingsRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private VocabularyService vocabularyService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private InstitutionMapper institutionMapper;
    @Mock
    private ProfileService profileService;
    @Mock
    private PersonProfileAssignmentService personProfileAssignmentService;
    @Mock
    private PersonProfileAssignmentRepository personProfileAssignmentRepository;
    @Mock
    private ProfileMapper profileMapper;

    @InjectMocks
    private InstitutionService institutionService;

    private Institution institution1, institution2;
    private Person manager;
    private ActionUnit actionUnit;

    private InstitutionDTO institution1DTO, institution2DTO;
    private PersonDTO managerDTO;


    @BeforeEach
    void setUp() {
        manager = new Person();
        manager.setId(1L);
        manager.setUsername("Username");


        managerDTO = new PersonDTO();
        managerDTO.setId(1L);
        managerDTO.setUsername("Username");

        institution1 = new Institution();
        institution1.setId(-1L);
        institution1.setName("First name");
        institution1.setIdentifier("123456");

        institution2 = new Institution();
        institution2.setId(-2L);
        institution2.setName("Second name");
        institution2.setIdentifier("0987654");

        institution1DTO = new InstitutionDTO();
        institution1DTO.setId(-1L);
        institution1DTO.setName("First name");
        institution1DTO.setIdentifier("123456");

        institution2DTO = new InstitutionDTO();
        institution2DTO.setId(-2L);
        institution2DTO.setName("Second name");
        institution2DTO.setIdentifier("0987654");

        actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setCreatedBy(manager);
        actionUnit.setCreatedByInstitution(institution1);
    }

    @Test
    void findAll_shouldReturnAllInstitutionsAsDTOs() {


        when(institutionRepository.findAll()).thenReturn(List.of(institution1, institution2));
        when(institutionMapper.convert(institution1)).thenReturn(institution1DTO);
        when(institutionMapper.convert(institution2)).thenReturn(institution2DTO);

        // Act
        Set<InstitutionDTO> result = institutionService.findAll();

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .containsExactlyInAnyOrder(institution1DTO, institution2DTO);

        verify(institutionRepository, times(1)).findAll();
        verify(institutionMapper, times(1)).convert(institution1);
        verify(institutionMapper, times(1)).convert(institution2);
    }


    @Test
    void createInstitution_throwsInstitutionAlreadyExist() {
        when(institutionRepository.findInstitutionByIdentifier("123456")).thenReturn(Optional.of(institution1));
        assertThrows(InstitutionAlreadyExistException.class, () -> institutionService.createInstitution(institution1DTO, "invalid_url"));
    }

    @Test
    void createInstitution_throwsFailedInstitutionSaveException() throws InvalidEndpointException {

        // Mock
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(new Institution());
        when(institutionRepository.save(any(Institution.class))).thenThrow(new RuntimeException("Error while saving institution"));
        Vocabulary fakeVocabulary = new Vocabulary();
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenReturn(fakeVocabulary);

        assertThrows(FailedInstitutionSaveException.class, () -> institutionService.createInstitution(institution1DTO, "valid_url"));
    }

    @Test
    void createInstitution() throws InstitutionAlreadyExistException, FailedInstitutionSaveException, InvalidEndpointException, NotSiamoisThesaurusException, ErrorProcessingExpansionException {

        Vocabulary fakeVocabulary = new Vocabulary();
        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution1);
        when(vocabularyService.findOrCreateVocabularyOfUri(anyString()))
                .thenReturn(fakeVocabulary);
        when(fieldConfigurationService
                .setupFieldConfigurationForInstitution(any(InstitutionDTO.class), any(Vocabulary.class))).thenReturn(
                        Optional.of(mock(FeedbackFieldConfig.class)));
        when(institutionMapper.convert(any(Institution.class))).thenReturn(new InstitutionDTO());
        when(institutionRepository.save(any(Institution.class))).thenReturn(mock(Institution.class));

        institutionService.createInstitution(institution1DTO, "valid_url");

        verify(institutionRepository, times(1)).save(institution1);
    }

    @Test
    void createInstitution_throwsInvalidEndpointException() throws InvalidEndpointException {

        when(vocabularyService.findOrCreateVocabularyOfUri(anyString())).thenThrow(new InvalidEndpointException("Invalid url"));

        assertThrows(InvalidEndpointException.class, () -> institutionService.createInstitution(institution1DTO, "invalid_url"));
    }

    @Test
    void createOrGetSettingsOf_shouldReturnSettings_whenSet() {
        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(1L);
        Institution institution = new Institution();
        institution.setId(1L);

        InstitutionSettings settings = new InstitutionSettings();
        settings.setInstitution(institution);
        settings.setArkNaan("66666");

        when(institutionSettingsRepository.findById(institution.getId())).thenReturn(Optional.of(settings));

        InstitutionSettings result = institutionService.createOrGetSettingsOf(institutionDto);

        assertThat(result).isEqualTo(settings);
    }

    @Test
    void createOrGetSettingsOf_shouldReturnEmptySettings_whenNotSet() {
        Institution institution = new Institution();
        institution.setId(1L);
        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(1L);

        when(institutionSettingsRepository.findById(institution.getId())).thenReturn(Optional.empty());
        when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));
        when(institutionSettingsRepository.save(any(InstitutionSettings.class)))
                .then(invocation -> invocation.getArgument(0, InstitutionSettings.class));

        InstitutionSettings result = institutionService.createOrGetSettingsOf(institutionDto);

        assertThat(result.getInstitution()).isEqualTo(institution);
    }

    @Test
    void isManagerOf_whenIsOwnerOfInstitution_shouldReturnTrue() {
        PersonDTO person = new PersonDTO();
        person.setUsername("username");
        person.setEmail("test@example.com");
        person.setId(12L);


        when(institutionRepository.personIsInstitutionManagerOf(any(Long.class), any(Long.class))).thenReturn(true);

        boolean result = institutionService.isManagerOf(institution1DTO, person);

        assertThat(result).isTrue();
    }

    @Test
    void update() {
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Updated Institution");

        when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(institution);
        when(institutionRepository.save(institution)).thenReturn(institution);

        institutionService.update(institution1DTO);

        verify(institutionRepository, times(1)).save(institution);
    }

    @Test
    void findInstitutionsOfPerson_shouldReturnAllInstitutions() {
        // Arrange
        PersonDTO person = new PersonDTO();
        person.setId(1L);

        when(institutionRepository.findAllVisibleToPerson(
                person.getId()))
                .thenReturn(Set.of(institution1, institution2));

        when(institutionMapper.convert(institution1)).thenReturn(institution1DTO);
        when(institutionMapper.convert(institution2)).thenReturn(institution2DTO);

        // Act
        Set<InstitutionDTO> result = institutionService.findInstitutionsOfPerson(person);

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .containsExactlyInAnyOrder(institution1DTO, institution2DTO);

        verify(institutionRepository, times(1)).findAllVisibleToPerson(
                person.getId());
        verify(institutionMapper, times(1)).convert(institution1);
        verify(institutionMapper, times(1)).convert(institution2);
    }

    @Test
    void findMembersOf_shouldReturnAssignedMembersAndCreator() {
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setId(1L);

        PersonDTO creatorDTO = new PersonDTO();
        creatorDTO.setId(2L);
        creatorDTO.setUsername("creator");
        dto.setCreatedBy(creatorDTO);

        when(personProfileAssignmentRepository.findAllPersonsByProfileActionUnitId(1L))
                .thenReturn(Set.of(manager));
        when(personMapper.convert(manager)).thenReturn(managerDTO);

        Set<PersonDTO> result = institutionService.findMembersOf(dto);

        assertThat(result).containsExactlyInAnyOrder(managerDTO, creatorDTO);
    }

    @Test
    void addToManagers_shouldAddManagerAndReturnTrue() {
        InstitutionDTO instDto = new InstitutionDTO();
        instDto.setId(1L);

        PersonDTO p = new PersonDTO();
        p.setId(2L);

        Profile memberProfile = new Profile();
        memberProfile.setCode(ProfileConstants.ORGANIZATION_MEMBER);
        Profile managerProfile = new Profile();
        managerProfile.setCode(ProfileConstants.ORGANIZATION_MANAGER);

        ProfileDTO memberDTO = new ProfileDTO();
        memberDTO.setCode(ProfileConstants.ORGANIZATION_MEMBER);
        ProfileDTO managerProfileDTO = new ProfileDTO();
        managerProfileDTO.setCode(ProfileConstants.ORGANIZATION_MANAGER);

        when(profileService.createOrGetOrganizationMemberProfile(instDto)).thenReturn(memberProfile);
        when(profileService.createOrGetOrganizationManagerProfile(instDto)).thenReturn(managerProfile);
        when(profileMapper.convert(memberProfile)).thenReturn(memberDTO);
        when(profileMapper.convert(managerProfile)).thenReturn(managerProfileDTO);
        when(personProfileAssignmentService.addToInstitution(eq(instDto), eq(p), anyList()))
                .thenReturn(new InstitutionMemberDTO());

        boolean added = institutionService.addToManagers(instDto, p);

        assertTrue(added);

        ArgumentCaptor<List<ProfileDTO>> profilesCaptor = ArgumentCaptor.forClass(List.class);
        verify(personProfileAssignmentService).addToInstitution(eq(instDto), eq(p), profilesCaptor.capture());
        assertThat(profilesCaptor.getValue()).containsExactlyInAnyOrder(memberDTO, managerProfileDTO);
    }

    @Test
    void countMembersInInstitution_shouldReturnCorrectCount() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);

        when(personRepository.countPersonsInInstitution(institution.getId())).thenReturn(2L);

        long result = institutionService.countMembersInInstitution(institution);

        assertThat(result).isEqualTo(2);
    }

    @Test
    void personIsInInstitution_shouldReturnTrueIfPersonHasProfileInInstitution() {
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        when(personProfileAssignmentRepository.personHasAnyProfileInInstitution(personDto.getId(), institution1DTO.getId()))
                .thenReturn(true);

        boolean result = institutionService.personIsInInstitution(personDto, institution1DTO);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInInstitution_shouldReturnFalseIfNotInInstitution() {
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        when(personProfileAssignmentRepository.personHasAnyProfileInInstitution(personDto.getId(), institution1DTO.getId()))
                .thenReturn(false);

        boolean result = institutionService.personIsInInstitution(personDto, institution1DTO);

        assertThat(result).isFalse();
    }

    @Test
    void findAllActionManagersOf_shouldReturnAllActionManagers() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);

        when(personProfileAssignmentRepository.findAllPersonsByProfileCodeAndInstitutionId(
                ProfileConstants.ORGANIZATION_PROJECT_MANAGER, institution.getId()))
                .thenReturn(Set.of(manager));
        when(personMapper.convert(manager)).thenReturn(managerDTO);

        Set<PersonDTO> result = institutionService.findAllActionManagersOf(institution);

        assertThat(result).containsExactlyInAnyOrder(managerDTO);
    }

    @Test
    void personIsInstitutionManager_shouldReturnTrueIfManager() {
        Institution institution = new Institution();
        institution.setId(-1L);

        Person person = new Person();
        person.setId(1L);
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(true);

        boolean result = institutionService.personIsInstitutionManager(personDto, institution1DTO);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInstitutionManager_shouldReturnFalseIfNotManager() {
        Institution institution = new Institution();
        institution.setId(-1L);

        Person person = new Person();
        person.setId(1L);
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);
        when(institutionRepository.personIsInstitutionManagerOf(institution.getId(), person.getId())).thenReturn(false);

        boolean result = institutionService.personIsInstitutionManager(personDto, institution1DTO);

        assertThat(result).isFalse();
    }

    @Test
    void personIsActionManager_shouldReturnTrueIfActionManager() {
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);
        institution1DTO.setId(1L);

        when(personProfileAssignmentRepository.personHasProfileWithCodeInInstitution(
                personDto.getId(), institution1DTO.getId(), ProfileConstants.ORGANIZATION_PROJECT_MANAGER))
                .thenReturn(true);

        boolean result = institutionService.personIsActionManager(personDto, institution1DTO);

        assertThat(result).isTrue();
    }

    @Test
    void personIsActionManager_shouldReturnFalseIfNotActionManager() {
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);
        institution1DTO.setId(1L);

        when(personProfileAssignmentRepository.personHasProfileWithCodeInInstitution(
                personDto.getId(), institution1DTO.getId(), ProfileConstants.ORGANIZATION_PROJECT_MANAGER))
                .thenReturn(false);

        boolean result = institutionService.personIsActionManager(personDto, institution1DTO);

        assertThat(result).isFalse();
    }

    @Test
    void personIsInstitutionManagerOrActionManager_shouldReturnTrueIfManager() {
        institution1DTO.setId(1L);
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(1L, 1L)).thenReturn(true);

        boolean result = institutionService.personIsInstitutionManagerOrActionManager(personDto, institution1DTO);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInstitutionManagerOrActionManager_shouldReturnTrueIfActionManager() {
        institution1DTO.setId(-1L);
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution1DTO.getId(), personDto.getId())).thenReturn(false);
        when(personProfileAssignmentRepository.personHasProfileWithCodeInInstitution(
                personDto.getId(), institution1DTO.getId(), ProfileConstants.ORGANIZATION_PROJECT_MANAGER))
                .thenReturn(true);

        boolean result = institutionService.personIsInstitutionManagerOrActionManager(personDto, institution1DTO);

        assertThat(result).isTrue();
    }

    @Test
    void personIsInstitutionManagerOrActionManager_shouldReturnFalseIfNeither() {
        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        when(institutionRepository.personIsInstitutionManagerOf(institution1DTO.getId(), personDto.getId())).thenReturn(false);
        when(personProfileAssignmentRepository.personHasProfileWithCodeInInstitution(
                personDto.getId(), institution1DTO.getId(), ProfileConstants.ORGANIZATION_PROJECT_MANAGER))
                .thenReturn(false);

        boolean result = institutionService.personIsInstitutionManagerOrActionManager(personDto, institution1DTO);

        assertThat(result).isFalse();
    }


    @Test
    void addPersonAsMemberOfActionUnit_shouldAddMemberAndReturnTrue() {
        ActionUnitDTO actionUnitDto = new ActionUnitDTO();
        actionUnitDto.setId(1L);

        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);

        when(personProfileAssignmentService.addToProjectMembers(actionUnitDto, personDTO)).thenReturn(true);

        boolean result = institutionService.addPersonAsMemberOfActionUnit(actionUnitDto, personDTO);

        assertThat(result).isTrue();
    }

    @Test
    void addPersonAsMemberOfActionUnit_shouldNotAddMemberIfAlreadyExists() {
        ActionUnitDTO actionUnitDto = new ActionUnitDTO();
        actionUnitDto.setId(1L);

        PersonDTO personDTO = new PersonDTO();
        personDTO.setId(1L);

        when(personProfileAssignmentService.addToProjectMembers(actionUnitDto, personDTO)).thenReturn(false);

        boolean result = institutionService.addPersonAsMemberOfActionUnit(actionUnitDto, personDTO);

        assertThat(result).isFalse();
    }

    @Test
    void findManagersOf_shouldReturnAllManagers() {
        // given
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(42L);

        Person p1 = new Person();
        p1.setId(1L);
        p1.setUsername("user1");
        Person p2 = new Person();
        p2.setId(2L);
        p2.setUsername("user2");
        Set<Person> repoResult = new HashSet<>(Arrays.asList(p1, p2));

        when(personRepository.findManagersOfInstitution(42L)).thenReturn(repoResult);
        when(personMapper.convert(any(Person.class))).thenReturn(new PersonDTO());

        // when
        Set<PersonDTO> result = institutionService.findManagersOf(inst);


        // then
        assertNotNull(result);
        verify(personMapper, times(2))
                .convert(any(Person.class));

        verify(personRepository, times(1)).findManagersOfInstitution(42L);

    }

    @Test
    void findById_success() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));

        InstitutionDTO expectedDTO = new InstitutionDTO();
        expectedDTO.setId(1L);
        when(institutionMapper.convert(institution)).thenReturn(expectedDTO);

        // Act
        InstitutionDTO result = institutionService.findById(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(institutionRepository, times(1)).findById(1L);
        verify(institutionMapper, times(1)).convert(institution);
    }

    @Test
    void findById_null() {
        when(institutionRepository.findById(1L)).thenReturn(Optional.empty());
        InstitutionDTO institution = institutionService.findById(1L);
        assertThat(institution).isNull();
    }

    @Test
    void findByIdentifier_returnsMappedInstitution_whenFound() {
        Institution institution = new Institution();
        institution.setIdentifier("siamois");
        InstitutionDTO dto = new InstitutionDTO();
        dto.setIdentifier("siamois");
        when(institutionRepository.findByIdentifierIgnoreCase("siamois")).thenReturn(Optional.of(institution));
        when(institutionMapper.convert(institution)).thenReturn(dto);

        Optional<InstitutionDTO> result = institutionService.findByIdentifier("siamois");

        assertThat(result).contains(dto);
    }

    @Test
    void findByIdentifier_returnsEmpty_whenNotFound() {
        when(institutionRepository.findByIdentifierIgnoreCase("unknown")).thenReturn(Optional.empty());

        Optional<InstitutionDTO> result = institutionService.findByIdentifier("unknown");

        assertThat(result).isEmpty();
        verify(institutionMapper, never()).convert(any(Institution.class));
    }
}
