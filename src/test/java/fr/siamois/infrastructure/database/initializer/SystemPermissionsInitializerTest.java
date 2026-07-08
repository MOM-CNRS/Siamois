package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
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

    @InjectMocks
    private SystemPermissionsInitializer initializer;

    private Person adminPerson;
    private Profile superAdminProfile;
    private Profile orgManagerProfile;
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

        orgManagerProfile = new Profile();
        orgManagerProfile.setId(20L);
        orgManagerProfile.setCode("ORGANIZATION_MANAGER");

        defaultInstitution = new Institution();
        defaultInstitution.setId(100L);

        defaultInstitutionDTO = new InstitutionDTO();
        defaultInstitutionDTO.setId(100L);
    }

    @Test
    void initialize_SuccessfulExecution_WhenAssignmentsAreMissing() {
        // Mock Permissions (Simule que les permissions n'existent pas encore pour déclencher la sauvegarde)
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.empty());

        // Mock ProfileService & Repositories
        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.of(adminPerson));

        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(defaultInstitution));
        when(institutionMapper.convert(defaultInstitution)).thenReturn(defaultInstitutionDTO);
        when(profileService.createOrGetOrganizationManagerProfile(defaultInstitutionDTO)).thenReturn(orgManagerProfile);

        // Mock Assignments: Simule que l'admin n'a pas encore ces profils
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(adminPerson.getId())))
                .thenReturn(Optional.empty());

        // Execution
        assertDoesNotThrow(() -> initializer.initialize());

        // Verifications
        // Vérifie qu'au moins une permission a été sauvegardée (basé sur la réflexion de PermissionConstants)
        verify(permissionRepository, atLeastOnce()).save(any(Permission.class));

        // Vérifie que les profils ont bien été assignés
        ArgumentCaptor<PersonProfileAssignment> assignmentCaptor = ArgumentCaptor.forClass(PersonProfileAssignment.class);
        verify(personProfileAssignmentRepository, times(2)).save(assignmentCaptor.capture());

        // Vérifie le contenu des assignations
        var savedAssignments = assignmentCaptor.getAllValues();
        assertTrue(savedAssignments.stream().anyMatch(a -> a.getProfile().getId().equals(superAdminProfile.getId())));
        assertTrue(savedAssignments.stream().anyMatch(a -> a.getProfile().getId().equals(orgManagerProfile.getId())));
        assertTrue(savedAssignments.stream().allMatch(a -> a.getPerson().getId().equals(adminPerson.getId())));
    }

    @Test
    void initialize_SuccessfulExecution_SkipsAssignmentWhenAlreadyAssigned() {
        // Mock Permissions (Simule que les permissions existent déjà)
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(new Permission()));

        when(profileService.createOrGetSuperadminProfile()).thenReturn(superAdminProfile);
        when(personRepository.findByUsernameIgnoreCase(ADMIN_USERNAME)).thenReturn(Optional.of(adminPerson));

        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(defaultInstitution));
        when(institutionMapper.convert(defaultInstitution)).thenReturn(defaultInstitutionDTO);
        when(profileService.createOrGetOrganizationManagerProfile(defaultInstitutionDTO)).thenReturn(orgManagerProfile);

        // Mock Assignments: Simule que l'admin a déjà ces profils
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(adminPerson.getId())))
                .thenReturn(Optional.of(new PersonProfileAssignment()));

        // Execution
        assertDoesNotThrow(() -> initializer.initialize());

        // Verifications
        verify(permissionRepository, never()).save(any(Permission.class));
        verify(personProfileAssignmentRepository, never()).save(any(PersonProfileAssignment.class));
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