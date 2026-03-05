package fr.siamois.domain.services.authorization;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.WritePermissionVerifier;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private InstitutionService institutionService;

    @Mock
    private WritePermissionVerifier writePermissionVerifier;

    @Mock
    private ConversionService conversionService;

    @Mock
    private AbstractEntityDTO resource;

    @Mock
    private UserInfo user;

    private PermissionServiceImpl permissionService;

    private Person person;
    private PersonDTO personDto;
    private Institution institutionA, institutionB;
    private InstitutionDTO institutionADto, institutionBDto;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(institutionService, List.of(writePermissionVerifier), conversionService);

        person = new Person();
        person.setUsername("username");
        person.setId(1L);

        institutionA = new Institution();
        institutionA.setName("Institution A");
        institutionA.setId(1L);

        institutionB = new Institution();
        institutionB.setName("Institution B");
        institutionB.setId(2L);

        personDto = new PersonDTO();
        personDto.setUsername("username");
        personDto.setId(1L);

        institutionADto = new InstitutionDTO();
        institutionADto.setName("Institution A");
        institutionADto.setId(1L);

        institutionBDto = new InstitutionDTO();
        institutionBDto.setName("Institution B");
        institutionBDto.setId(2L);
    }

    @Test
    void hasReadPermission_shouldReturnFalseWhenInstitutionDoesNotMatch() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionBDto);

        boolean result = permissionService.hasReadPermission(user, resource);

        assertFalse(result);
    }

    @Test
    void hasReadPermission_shouldReturnTrueWhenUserIsActionManager() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(permissionService.isActionManager(user)).thenReturn(true);

        boolean result = permissionService.hasReadPermission(user, resource);

        assertTrue(result);
    }

    @Test
    void hasReadPermission_shouldReturnTrueWhenUserIsInstitutionManager() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(permissionService.isInstitutionManager(user)).thenReturn(true);

        boolean result = permissionService.hasReadPermission(user, resource);

        assertTrue(result);
    }

    @Test
    void hasReadPermission_shouldReturnTrueWhenUserIsInInstitution() {
        // Arrange
        InstitutionDTO institutionADto = new InstitutionDTO();
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(permissionService.isActionManager(user)).thenReturn(false);
        when(permissionService.isInstitutionManager(user)).thenReturn(false);

        // Mock the conversion of InstitutionDTO to InstitutionDTO (if needed)
        when(conversionService.convert(institutionADto, InstitutionDTO.class)).thenReturn(institutionADto);

        // Mock the institution check
        when(institutionService.personIsInInstitution(user.getUser(), institutionADto)).thenReturn(true);

        // Act
        boolean result = permissionService.hasReadPermission(user, resource);

        // Assert
        assertTrue(result);
    }


    @Test
    void hasWritePermission_shouldReturnFalseWhenInstitutionDoesNotMatch() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);

        boolean result = permissionService.hasWritePermission(user, resource);

        assertFalse(result);
    }

    @Test
    void hasWritePermission_shouldReturnTrueWhenUserIsActionManager() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(permissionService.isActionManager(user)).thenReturn(true);

        boolean result = permissionService.hasWritePermission(user, resource);

        assertTrue(result);
    }

    @Test
    void hasWritePermission_shouldReturnTrueWhenUserIsInstitutionManager() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(permissionService.isInstitutionManager(user)).thenReturn(true);

        boolean result = permissionService.hasWritePermission(user, resource);

        assertTrue(result);
    }

    @Test
    void hasWritePermission_shouldReturnFalseWhenVerifierExistsAndPermissionDenied() {
        when(resource.getCreatedByInstitution()).thenReturn(institutionADto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(permissionService.isActionManager(user)).thenReturn(false);
        when(permissionService.isInstitutionManager(user)).thenReturn(false);

        boolean result = permissionService.hasWritePermission(user, resource);

        assertFalse(result);
    }

    @Test
    void isInstitutionManager_shouldReturnTrueWhenUserIsManager() {
        when(user.getUser()).thenReturn(personDto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(institutionService.personIsInstitutionManager(personDto, institutionADto)).thenReturn(true);

        boolean result = permissionService.isInstitutionManager(user);

        assertTrue(result);
    }

    @Test
    void isInstitutionManager_shouldReturnFalseWhenUserIsNotManager() {
        when(user.getUser()).thenReturn(personDto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(institutionService.personIsInstitutionManager(personDto, institutionADto)).thenReturn(false);

        boolean result = permissionService.isInstitutionManager(user);

        assertFalse(result);
    }

    @Test
    void isActionManager_shouldReturnTrueWhenUserIsManager() {
        when(user.getUser()).thenReturn(personDto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(institutionService.personIsActionManager(personDto, institutionADto)).thenReturn(true);

        boolean result = permissionService.isActionManager(user);

        assertTrue(result);
    }

    @Test
    void isActionManager_shouldReturnFalseWhenUserIsNotManager() {
        when(user.getUser()).thenReturn(personDto);
        when(user.getInstitution()).thenReturn(institutionADto);
        when(institutionService.personIsActionManager(personDto, institutionADto)).thenReturn(false);

        boolean result = permissionService.isActionManager(user);

        assertFalse(result);
    }
}