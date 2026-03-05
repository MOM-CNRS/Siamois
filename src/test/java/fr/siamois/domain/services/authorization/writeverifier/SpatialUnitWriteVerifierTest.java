package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialUnitWriteVerifierTest {

    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private SpatialUnitWriteVerifier spatialUnitWriteVerifier;

    private UserInfo userInfo;

    @BeforeEach
    void setUp() {
        PersonDTO person = new PersonDTO();
        person.setUsername("testUser");
        person.setId(1L);

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        institution.setName("Test Institution");

        userInfo = new UserInfo(institution, person, "fr");
    }

    @Test
    void getEntityClass_shouldReturnSpatialUnitClass() {
        // Act
        Class<? extends TraceableEntity> result = spatialUnitWriteVerifier.getEntityClass();

        // Assert
        assertEquals(SpatialUnit.class, result);
    }

    @Test
    void hasSpecificWritePermission_shouldReturnTrueWhenUserIsInstitutionManager() {
        // Arrange
        when(institutionService.personIsInstitutionManagerOrActionManager(
                userInfo.getUser(), userInfo.getInstitution())).thenReturn(true);

        // Act
        boolean result = spatialUnitWriteVerifier.hasSpecificWritePermission(userInfo, null);

        // Assert
        assertTrue(result);
    }

    @Test
    void hasSpecificWritePermission_shouldReturnFalseWhenUserIsNotInstitutionManager() {
        // Arrange
        when(institutionService.personIsInstitutionManagerOrActionManager(
                userInfo.getUser(), userInfo.getInstitution())).thenReturn(false);

        // Act
        boolean result = spatialUnitWriteVerifier.hasSpecificWritePermission(userInfo, null);

        // Assert
        assertFalse(result);
    }

}