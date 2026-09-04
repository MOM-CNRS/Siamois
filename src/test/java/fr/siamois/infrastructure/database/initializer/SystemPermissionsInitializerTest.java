package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PermissionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.InstitutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class SystemPermissionsInitializerTest {

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private PersonProfileAssignmentRepository personProfileAssignmentRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private InstitutionMapper institutionMapper;
    @Mock
    private PersonProfileAssignmentService personProfileAssignmentService;

    @InjectMocks
    private SystemPermissionsInitializer initializer;

    private Person adminPerson;
    private Profile superAdminProfile;
    private Institution defaultInstitution;
    private InstitutionDTO defaultInstitutionDTO;

    private static final String ADMIN_USERNAME = "admin_test";

    @BeforeEach
    void setUp() {
        // Injection de la propriété @Value
        ReflectionTestUtils.setField(initializer, "adminUsername", ADMIN_USERNAME);

        adminPerson = new Person();
        adminPerson.setId(1L);
        adminPerson.setUsername(ADMIN_USERNAME);

        superAdminProfile = new Profile();
        superAdminProfile.setId(10L);
        superAdminProfile.setCode("SUPERADMIN");

        defaultInstitution = new Institution();
        defaultInstitution.setId(100L);
        defaultInstitution.setIdentifier("siamois");

        defaultInstitutionDTO = new InstitutionDTO();
        defaultInstitutionDTO.setId(100L);
        defaultInstitutionDTO.setName("Institution par défaut");
    }

    @Test
    void initialize_SuccessfulExecution_WhenAssignmentsAreMissing() {
        // Mock Permissions (Simule que les permissions n'existent pas encore pour déclencher la sauvegarde)
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.empty());

        // Mock ProfileService & Repositories
        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.of(adminPerson));

        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(defaultInstitution));
        when(institutionRepository.findAll()).thenReturn(List.of(defaultInstitution));
        when(institutionMapper.convert(defaultInstitution)).thenReturn(defaultInstitutionDTO);

        // Mock Assignments: Simule que l'admin n'a pas encore ces profils
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(adminPerson.getId())))
                .thenReturn(Optional.empty());

        // Execution
        assertDoesNotThrow(() -> initializer.initialize());

        // Verifications
        // Vérifie qu'au moins une permission a été sauvegardée (basé sur la réflexion de PermissionConstants)
        verify(permissionRepository, atLeastOnce()).save(any(Permission.class));

        // Seul le profil SUPERADMIN est assigné par l'initializer : le rattachement du superadmin aux
        // gestionnaires de chaque organisation passe par PersonProfileAssignmentService
        ArgumentCaptor<PersonProfileAssignment> assignmentCaptor = ArgumentCaptor.forClass(PersonProfileAssignment.class);
        verify(personProfileAssignmentRepository, times(1)).save(assignmentCaptor.capture());

        PersonProfileAssignment savedAssignment = assignmentCaptor.getValue();
        assertEquals(superAdminProfile.getId(), savedAssignment.getProfile().getId());
        assertEquals(adminPerson.getId(), savedAssignment.getPerson().getId());

        // Les profils d'organisation existent et le superadmin en devient gestionnaire
        verify(profileService).createOrGetOrganizationManagerProfile(defaultInstitutionDTO);
        verify(profileService).createOrGetOrganizationProjectManagerProfile(defaultInstitutionDTO);
        verify(profileService).createOrGetOrganizationMemberProfile(defaultInstitutionDTO);
        verify(personProfileAssignmentService).assignSuperAdminsAsOrganizationManagers(defaultInstitutionDTO);
    }

    @Test
    void initialize_AssignsSuperAdminsAsManagersOfEveryExistingInstitution() {
        Institution otherInstitution = new Institution();
        otherInstitution.setId(200L);
        otherInstitution.setIdentifier("other");
        InstitutionDTO otherInstitutionDTO = new InstitutionDTO();
        otherInstitutionDTO.setId(200L);
        otherInstitutionDTO.setName("Autre institution");

        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(new Permission()));
        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.of(adminPerson));
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(adminPerson.getId())))
                .thenReturn(Optional.of(new PersonProfileAssignment()));

        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(defaultInstitution));
        when(institutionRepository.findAll()).thenReturn(List.of(defaultInstitution, otherInstitution));
        when(institutionMapper.convert(defaultInstitution)).thenReturn(defaultInstitutionDTO);
        when(institutionMapper.convert(otherInstitution)).thenReturn(otherInstitutionDTO);

        assertDoesNotThrow(() -> initializer.initialize());

        verify(personProfileAssignmentService).assignSuperAdminsAsOrganizationManagers(defaultInstitutionDTO);
        verify(personProfileAssignmentService).assignSuperAdminsAsOrganizationManagers(otherInstitutionDTO);
    }

    @Test
    void initialize_SuccessfulExecution_SkipsAssignmentWhenAlreadyAssigned() {
        // Mock Permissions (Simule que les permissions existent déjà)
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(new Permission()));

        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.of(adminPerson));

        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(defaultInstitution));
        when(institutionRepository.findAll()).thenReturn(List.of(defaultInstitution));
        when(institutionMapper.convert(defaultInstitution)).thenReturn(defaultInstitutionDTO);

        // Mock Assignments: Simule que l'admin a déjà ces profils
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(adminPerson.getId())))
                .thenReturn(Optional.of(new PersonProfileAssignment()));

        // Execution
        assertDoesNotThrow(() -> initializer.initialize());

        // Verifications
        verify(permissionRepository, never()).save(any(Permission.class));
        verify(personProfileAssignmentRepository, never()).save(any(PersonProfileAssignment.class));
        verify(personProfileAssignmentService).assignSuperAdminsAsOrganizationManagers(defaultInstitutionDTO);
    }

    @Test
    void initialize_ThrowsException_WhenAdminNotFound() {
        // Mock
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(new Permission()));
        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.empty()); // Admin manquant

        // Execution & Verification
        DatabaseDataInitException exception = assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
        assertEquals("Super administrator profile not found", exception.getMessage());

        verify(institutionRepository, never()).findInstitutionByIdentifier(anyString());
    }

    @Test
    void initialize_ThrowsException_WhenDefaultInstitutionNotFound() {
        // Mock
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(new Permission()));
        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.of(adminPerson));

        // Simule que l'assignation superAdmin passe
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(superAdminProfile.getId(), adminPerson.getId()))
                .thenReturn(Optional.empty());

        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.empty()); // Institution manquante

        // Execution & Verification
        DatabaseDataInitException exception = assertThrows(DatabaseDataInitException.class, () -> initializer.initialize());
        assertEquals("Default Institution not found", exception.getMessage());

        verify(profileService, never()).createOrGetOrganizationManagerProfile(any());
    }
}