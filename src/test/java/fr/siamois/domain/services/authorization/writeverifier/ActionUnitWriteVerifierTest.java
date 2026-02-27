package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionUnitWriteVerifierTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private ActionUnitWriteVerifier actionUnitWriteVerifier;

    private UserInfo userInfo;

    @BeforeEach
    void setUp() {
        Person person = new Person();
        person.setUsername("testUser");
        person.setId(1L);

        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Test Institution");

        userInfo = new UserInfo(institution, person, "fr");
    }

    @Test
    void getEntityClass_shouldReturnActionUnitClass() {
        // Act
        Class<? extends TraceableEntity> result = actionUnitWriteVerifier.getEntityClass();

        // Assert
        assertEquals(ActionUnit.class, result);
    }

    @Test
    void hasSpecificWritePermission_shouldReturnTrueWhenPermissionExists() {
        // Arrange

        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);

        when(teamMemberRepository.existsByActionUnitIdAndPerson(actionUnit, userInfo.getUser())).thenReturn(true);

        // Act
        boolean result = actionUnitWriteVerifier.hasSpecificWritePermission(userInfo, actionUnit);

        // Assert
        assertTrue(result);
    }

    @Test
    void hasSpecificWritePermission_shouldReturnFalseWhenPermissionDoesNotExist() {
        // Arrange
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);

        when(teamMemberRepository.existsByActionUnitIdAndPerson(actionUnit, userInfo.getUser())).thenReturn(false);

        // Act
        boolean result = actionUnitWriteVerifier.hasSpecificWritePermission(userInfo, actionUnit);

        // Assert
        assertFalse(result);
    }

}