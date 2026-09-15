package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.utils.context.PermissionCheckCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilePermissionServiceTest {

    @Mock
    private PersonProfileAssignmentRepository assignmentRepository;

    @InjectMocks
    private ProfilePermissionService profilePermissionService;

    private PersonDTO person;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        // The permission cache is a per-request ThreadLocal in production (cleared by
        // UserInfoContextFilter); tests run on a reused thread with no such filter, so it must be
        // cleared explicitly or a result cached by an earlier test leaks into this one.
        PermissionCheckCache.clear();
        person = new PersonDTO();
        person.setId(3L);
        institution = new InstitutionDTO();
        institution.setId(12L);
    }

    @Test
    void canAccessInstitution_shouldReturnTrueWhenPersonHasAnyProfileInInstitution() {
        when(assignmentRepository.personHasAnyProfileInInstitution(3L, 12L)).thenReturn(true);

        assertTrue(profilePermissionService.canAccessInstitution(person, institution));
    }

    @Test
    void canAccessInstitution_shouldReturnTrueWhenPersonManagesTheInstitution() {
        when(assignmentRepository.personHasAnyProfileInInstitution(3L, 12L)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(true);

        assertTrue(profilePermissionService.canAccessInstitution(person, institution));
    }

    @Test
    void canAccessInstitution_shouldReturnFalseWhenPersonHasNoProfileInInstitution() {
        when(assignmentRepository.personHasAnyProfileInInstitution(3L, 12L)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(false);

        assertFalse(profilePermissionService.canAccessInstitution(person, institution));
    }

    @Test
    void canAccessInstitution_shouldReturnFalseWhenInstitutionIsNull() {
        assertFalse(profilePermissionService.canAccessInstitution(person, null));
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void canAccessInstitution_shouldReturnFalseWhenPersonIsNull() {
        assertFalse(profilePermissionService.canAccessInstitution(null, institution));
        verifyNoInteractions(assignmentRepository);
    }

}
