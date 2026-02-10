package fr.siamois.domain.services;


import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionUnitServiceTest {

    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private ConceptService conceptService;
    @Mock private ActionCodeRepository actionCodeRepository;

    @InjectMocks
    private ActionUnitService actionUnitService;

    SpatialUnit spatialUnit1 ;
    ActionUnit actionUnit1 ;
    ActionUnit actionUnit2 ;

    ActionUnit actionUnitWithCodesBefore;
    ActionUnit actionUnitWithCodesAfter;
    ActionCode primaryActionCode;
    ActionCode primaryActionCodeBefore;
    ActionCode secondaryActionCode1;
    ActionCode secondaryActionCode2;
    ActionCode failedCode;
    Concept c1, c2, c3;

    UserInfo info;

    Page<ActionUnit> page ;
    Pageable pageable;

    @BeforeEach
    void setUp() {
        spatialUnit1 = new SpatialUnit();
        actionUnit1 = new ActionUnit();
        actionUnit2 = new ActionUnit();
        spatialUnit1.setId(1L);
        actionUnit1.setId(1L);
        actionUnit1.setIdentifier("1");
        actionUnit2.setId(2L);
        actionUnit2.setIdentifier("2");

        Person p =new Person();
        Institution i = new Institution();
        info = new UserInfo(i,p,"fr");
        c1 = new Concept();
        c2 = new Concept();
        c3 = new Concept();
        // For action codes test
        c1.setExternalId("1");
        c2.setExternalId("2");
        c3.setExternalId("3");

        actionUnitWithCodesAfter = new ActionUnit();
        actionUnitWithCodesBefore = new ActionUnit();
        primaryActionCode = new ActionCode();
        primaryActionCode.setCode("primary");
        primaryActionCode.setType(c1);
        primaryActionCode = new ActionCode();
        primaryActionCodeBefore = new ActionCode();
        primaryActionCodeBefore.setCode("primaryBefore");
        primaryActionCodeBefore.setType(c2);
        secondaryActionCode1 = new ActionCode();
        secondaryActionCode1.setCode("secondary1");
        secondaryActionCode1.setType(c2);
        secondaryActionCode2 = new ActionCode();
        secondaryActionCode2.setCode("secondary2");
        secondaryActionCode2.setType(c3);
        actionUnitWithCodesBefore.setPrimaryActionCode(primaryActionCodeBefore);
        actionUnitWithCodesAfter.setPrimaryActionCode(primaryActionCode);
        actionUnitWithCodesAfter.setSecondaryActionCodes(new HashSet<>(List.of(secondaryActionCode1, secondaryActionCode2)));

        failedCode = new ActionCode();
        failedCode.setType(c2);
        failedCode.setCode("primary");

        page = new PageImpl<>(List.of(actionUnit1, actionUnit2));
        pageable = PageRequest.of(0, 10);




    }


    @Test
    void findById_success() {

        when(actionUnitRepository.findById(actionUnit1.getId())).thenReturn(Optional.ofNullable(actionUnit1));

        // act
        ActionUnit actualResult = actionUnitService.findById(spatialUnit1.getId());

        // assert
        assertEquals(actionUnit1, actualResult);
    }

    @Test
    void findById_Exception() {

        when(actionUnitRepository.findById(actionUnit1.getId())).thenReturn(Optional.empty());


        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> actionUnitService.findById(spatialUnit1.getId())
        );

        assertEquals("ActionUnit not found with ID: 1", exception.getMessage());
    }

    @Test
    void save_withUserInfo_success() throws ActionUnitAlreadyExistsException {


        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setIdentifier("Test");
        Institution institution = new Institution();
        institution.setIdentifier("MOM");
        actionUnit.setCreatedByInstitution(institution);
        Concept typeConcept = new Concept();

        UserInfo userInfo = new UserInfo(institution, new Person(), "fr");

        when(conceptService.saveOrGetConcept(typeConcept)).thenReturn(typeConcept);
        when(actionUnitRepository.save(actionUnit)).thenReturn(actionUnit);

        ActionUnit result = actionUnitService.save(userInfo, actionUnit, typeConcept);

        assertNotNull(result);
        assertEquals("MOM-Test", result.getFullIdentifier());
        assertEquals(actionUnit, result);
        assertEquals(typeConcept, result.getType());
        assertEquals(userInfo.getUser(), result.getCreatedBy());
        assertEquals(userInfo.getInstitution(), result.getCreatedByInstitution());
    }

    @Test
    void findAllActionCodeByCodeIsContainingIgnoreCase_Success() {
        // Arrange
        String query = "test";
        ActionCode actionCode1 = new ActionCode();
        actionCode1.setCode("testCode1");
        ActionCode actionCode2 = new ActionCode();
        actionCode2.setCode("anotherTestCode");
        when(actionCodeRepository.findAllByCodeIsContainingIgnoreCase(query)).thenReturn(List.of(actionCode1, actionCode2));

        // Act
        List<ActionCode> actualResult = actionUnitService.findAllActionCodeByCodeIsContainingIgnoreCase(query);

        // Assert
        assertNotNull(actualResult);
        assertEquals(2, actualResult.size());
        assertThat(actualResult).extracting(ActionCode::getCode).containsExactlyInAnyOrder("testCode1", "anotherTestCode");
    }

    @Test
    void findAllActionCodeByCodeIsContainingIgnoreCase_Exception() {
        // Arrange
        String query = "test";
        when(actionCodeRepository.findAllByCodeIsContainingIgnoreCase(query)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(
                RuntimeException.class,
                () -> actionUnitService.findAllActionCodeByCodeIsContainingIgnoreCase(query)
        );

        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void testFindAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(actionUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(page);

        // Act
        Page<ActionUnit> actualResult = actionUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], new Long[2],"null", "fr", pageable
        );

        // Assert
        assertEquals(actionUnit1, actualResult.getContent().get(0));
        assertEquals(actionUnit2, actualResult.getContent().get(1));
    }

    @Test
    void findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(actionUnitRepository.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(page);

        // Act
        Page<ActionUnit> actualResult = actionUnitService.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                1L,1L, "null", new Long[2], new Long[2],"null", "fr", pageable
        );

        // Assert
        assertEquals(actionUnit1, actualResult.getContent().get(0));
        assertEquals(actionUnit2, actualResult.getContent().get(1));
    }

    @Test
    void testFindByTeamMember() {
        // 1. Préparation des données de test
        Long memberId = 1L;
        Person member = new Person();
        member.setId(memberId);

        ActionUnit actionUnit1 = new ActionUnit();
        actionUnit1.setId(101L);
        actionUnit1.setName("Action Unit 1");

        ActionUnit actionUnit2 = new ActionUnit();
        actionUnit2.setId(102L);
        actionUnit2.setName("Action Unit 2");

        Institution institution = new Institution();
        institution.setId(1L);

        List<ActionUnit> expectedActionUnits = Arrays.asList(actionUnit1, actionUnit2);

        // 2. Configuration du comportement simulé du repository
        when(actionUnitRepository.findByTeamMemberOrCreatorAndInstitution(memberId, 1L))
                .thenReturn(expectedActionUnits);

        // 3. Appel de la méthode à tester
        List<ActionUnit> result = actionUnitService.findByTeamMember(member, institution);

        // 4. Vérification des résultats
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Action Unit 1", result.get(0).getName());
        assertEquals("Action Unit 2", result.get(1).getName());

        // 5. Vérification que la méthode du repository a été appelée avec le bon argument
        verify(actionUnitRepository, times(1)).findByTeamMemberOrCreatorAndInstitution(memberId, 1L);
    }

    @Test
    void existsChildrenByParentAndInstitution_shouldReturnTrue_whenChildrenExist() {
        // Arrange
        Long parentId = 1L;
        Long institutionId = 10L;
        when(actionUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId))
                .thenReturn(true);

        // Act
        boolean result = actionUnitService.existsChildrenByParentAndInstitution(parentId, institutionId);

        // Assert
        assertTrue(result);
        verify(actionUnitRepository, times(1))
                .existsChildrenByParentAndInstitution(parentId, institutionId);
    }


}