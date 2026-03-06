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
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.PersonMapper;
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
    @Mock private ActionUnitMapper actionUnitMapper;
    @Mock private PersonMapper personMapper;

    @InjectMocks
    private ActionUnitService actionUnitService;

    SpatialUnit spatialUnit1 ;
    ActionUnit actionUnit1 ;
    ActionUnit actionUnit2 ;
    ActionUnitDTO actionUnit1dto ;
    ActionUnitDTO actionUnit2dto ;

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
        actionUnit1dto = new ActionUnitDTO();
        actionUnit2dto = new ActionUnitDTO();
        spatialUnit1.setId(1L);
        actionUnit1.setId(1L);
        actionUnit1.setIdentifier("1");
        actionUnit2.setId(2L);
        actionUnit2.setIdentifier("2");

        PersonDTO p =new PersonDTO();
        InstitutionDTO i = new InstitutionDTO();
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
        // Arrange
        Long actionUnitId = 1L;

        // Création d'un ActionUnit mocké avec toutes les propriétés nécessaires
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(actionUnitId);
        actionUnit.setCreatedByInstitution(new Institution()); // Évite les NullPointerException

        // Création du DTO attendu
        ActionUnitDTO expectedDto = new ActionUnitDTO();
        expectedDto.setId(actionUnitId);

        // Configuration des mocks
        when(actionUnitRepository.findById(actionUnitId)).thenReturn(Optional.of(actionUnit));
        when(actionUnitMapper.convert(actionUnit)).thenReturn(expectedDto);

        // Act
        ActionUnitDTO actualResult = actionUnitService.findById(actionUnitId);

        // Assert
        assertNotNull(actualResult, "Le résultat ne doit pas être null");
        assertEquals(expectedDto.getId(), actualResult.getId(), "Les IDs doivent correspondre");
        assertEquals(expectedDto, actualResult, "Les DTOs doivent être égaux");

        // Vérification des appels
        verify(actionUnitRepository).findById(actionUnitId);
        verify(actionUnitMapper).convert(actionUnit);
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
        // Arrange
        String identifier = "Test";
        String institutionIdentifier = "MOM";
        String name = "Test Action Unit";

        // Création des DTOs d'entrée
        ActionUnitDTO actionUnitDto = new ActionUnitDTO();
        actionUnitDto.setName(name);
        actionUnitDto.setIdentifier(identifier);
        actionUnitDto.setFullIdentifier(institutionIdentifier + "-" + identifier);

        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(1L);
        institutionDto.setIdentifier(institutionIdentifier);

        ConceptDTO typeConceptDto = new ConceptDTO();
        typeConceptDto.setId(10L);

        PersonDTO personDto = new PersonDTO();
        personDto.setId(1L);

        UserInfo userInfo = new UserInfo(institutionDto, personDto, "fr");

        // Création des entités mockées
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setName(name);
        actionUnit.setIdentifier(identifier);

        Institution institution = new Institution();
        institution.setId(1L);
        institution.setIdentifier(institutionIdentifier);

        Concept typeConcept = new Concept();
        typeConcept.setId(10L);

        Person person = new Person();
        person.setId(1L);

        // Création du DTO de résultat attendu
        ActionUnitDTO expectedResult = new ActionUnitDTO();
        expectedResult.setId(1L);
        expectedResult.setName(name);
        expectedResult.setIdentifier(identifier);
        expectedResult.setFullIdentifier(institutionIdentifier + "-" + identifier);
        expectedResult.setType(typeConceptDto);
        expectedResult.setCreatedBy(personDto);
        expectedResult.setCreatedByInstitution(institutionDto);

        // Configuration des mocks avec les bonnes valeurs
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(name, 1L))
                .thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(identifier, 1L))
                .thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(actionUnitDto)).thenReturn(actionUnit);
        when(conceptService.saveOrGetConcept(typeConceptDto)).thenReturn(typeConcept);
        when(personMapper.invertConvert(personDto)).thenReturn(person);
        when(actionUnitRepository.save(actionUnit)).thenReturn(actionUnit);
        when(actionUnitMapper.convert(actionUnit)).thenReturn(expectedResult);

        // Act
        ActionUnitDTO result = actionUnitService.save(userInfo, actionUnitDto, typeConceptDto);

        // Assert
        assertNotNull(result, "Le résultat ne doit pas être null");
        assertEquals(institutionIdentifier + "-" + identifier, result.getFullIdentifier(),
                "Le fullIdentifier doit être correctement formé");
        assertEquals(expectedResult.getId(), result.getId(), "Les IDs doivent correspondre");
        assertEquals(expectedResult.getType(), result.getType(), "Les types doivent correspondre");
        assertEquals(expectedResult.getCreatedBy(), result.getCreatedBy(), "Les créateurs doivent correspondre");
        assertEquals(expectedResult.getCreatedByInstitution(), result.getCreatedByInstitution(),
                "Les institutions doivent correspondre");

        // Vérification des appels
        verify(actionUnitRepository).findByNameAndCreatedByInstitutionId(name, 1L);
        verify(actionUnitRepository).findByIdentifierAndCreatedByInstitutionId(identifier ,1L);
        verify(actionUnitMapper).invertConvert(actionUnitDto);
        verify(conceptService).saveOrGetConcept(typeConceptDto);
        verify(personMapper).invertConvert(personDto);
        verify(actionUnitRepository).save(actionUnit);
        verify(actionUnitMapper).convert(actionUnit);
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
        // Arrange
        Long institutionId = 1L;
        String name = "test";
        Long[] categoryIds = {1L, 2L};
        Long[] personIds = {3L, 4L};
        String global = "search";
        String langCode = "fr";

        // Création des objets mockés
        ActionUnit mockActionUnit1 = new ActionUnit();
        mockActionUnit1.setId(1L);
        mockActionUnit1.setCreatedByInstitution(new Institution());

        ActionUnit mockActionUnit2 = new ActionUnit();
        mockActionUnit2.setId(2L);
        mockActionUnit2.setCreatedByInstitution(new Institution());

        ActionUnitDTO mockActionUnitDTO1 = new ActionUnitDTO();
        mockActionUnitDTO1.setId(1L);

        ActionUnitDTO mockActionUnitDTO2 = new ActionUnitDTO();
        mockActionUnitDTO2.setId(2L);

        // Création de la page de résultats mockée
        Page<ActionUnit> mockPage = new PageImpl<>(List.of(mockActionUnit1, mockActionUnit2));

        // Configuration des mocks avec les bonnes valeurs
        when(actionUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId,
                name,
                categoryIds,
                personIds,
                global,
                langCode,
                pageable
        )).thenReturn(mockPage);

        // Configuration du mapper pour chaque ActionUnit
        when(actionUnitMapper.convert(mockActionUnit1)).thenReturn(mockActionUnitDTO1);
        when(actionUnitMapper.convert(mockActionUnit2)).thenReturn(mockActionUnitDTO2);

        // Act
        Page<ActionUnitDTO> actualResult = actionUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId,
                name,
                categoryIds,
                personIds,
                global,
                langCode,
                pageable
        );

        // Assert
        assertNotNull(actualResult, "La page ne doit pas être null");
        assertEquals(2, actualResult.getTotalElements(), "La page doit contenir 2 éléments");
        assertEquals(mockActionUnitDTO1, actualResult.getContent().get(0), "Le premier élément doit correspondre");
        assertEquals(mockActionUnitDTO2, actualResult.getContent().get(1), "Le deuxième élément doit correspondre");

        // Vérification des appels - on s'attend à 2 appels au mapper (un par élément)
        verify(actionUnitRepository).findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId,
                name,
                categoryIds,
                personIds,
                global,
                langCode,
                pageable
        );
        verify(actionUnitMapper, times(2)).convert(any(ActionUnit.class));
    }


    @Test
    void findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {
        // Arrange
        Long institutionId = 1L;
        Long spatialUnitId = 1L;
        String name = "test";
        Long[] categoryIds = {1L, 2L};
        Long[] personIds = {3L, 4L};
        String global = "search";
        String langCode = "fr";

        // Création des objets mockés avec toutes les propriétés nécessaires
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setIdentifier("INST-001");

        ActionUnit mockActionUnit1 = new ActionUnit();
        mockActionUnit1.setId(1L);
        mockActionUnit1.setCreatedByInstitution(institution); // Initialisation de l'institution

        ActionUnit mockActionUnit2 = new ActionUnit();
        mockActionUnit2.setId(2L);
        mockActionUnit2.setCreatedByInstitution(institution); // Initialisation de l'institution

        ActionUnitDTO mockActionUnitDTO1 = new ActionUnitDTO();
        mockActionUnitDTO1.setId(1L);

        ActionUnitDTO mockActionUnitDTO2 = new ActionUnitDTO();
        mockActionUnitDTO2.setId(2L);

        // Création de la page de résultats mockée
        Page<ActionUnit> mockPage = new PageImpl<>(List.of(mockActionUnit1, mockActionUnit2));

        // Configuration des mocks
        when(actionUnitRepository.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId,
                spatialUnitId,
                name,
                categoryIds,
                personIds,
                global,
                langCode,
                pageable
        )).thenReturn(mockPage);

        when(actionUnitMapper.convert(mockActionUnit1)).thenReturn(mockActionUnitDTO1);
        when(actionUnitMapper.convert(mockActionUnit2)).thenReturn(mockActionUnitDTO2);

        // Act
        Page<ActionUnitDTO> actualResult = actionUnitService.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId,
                spatialUnitId,
                name,
                categoryIds,
                personIds,
                global,
                langCode,
                pageable
        );

        // Assert
        assertNotNull(actualResult, "La page ne doit pas être null");
        assertEquals(2, actualResult.getTotalElements(), "La page doit contenir 2 éléments");
        assertEquals(mockActionUnitDTO1, actualResult.getContent().get(0), "Le premier élément doit correspondre");
        assertEquals(mockActionUnitDTO2, actualResult.getContent().get(1), "Le deuxième élément doit correspondre");

        // Vérification des appels
        verify(actionUnitRepository).findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                institutionId,
                spatialUnitId,
                name,
                categoryIds,
                personIds,
                global,
                langCode,
        pageable
        );
    }
    @Test
    void testFindByTeamMember() {
        // 1. Préparation des données de test
        Long memberId = 1L;
        Long institutionId = 1L;

        PersonDTO memberDto = new PersonDTO();
        memberDto.setId(memberId);

        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(institutionId);

        // Création des entités mockées
        ActionUnit actionUnit11 = new ActionUnit();
        actionUnit11.setId(101L);
        actionUnit11.setName("Action Unit 1");

        ActionUnit actionUnit22 = new ActionUnit();
        actionUnit22.setId(102L);
        actionUnit22.setName("Action Unit 2");

        // Création des DTOs attendus
        ActionUnitDTO actionUnitDTO1 = new ActionUnitDTO();
        actionUnitDTO1.setId(101L);
        actionUnitDTO1.setName("Action Unit 1");

        ActionUnitDTO actionUnitDTO2 = new ActionUnitDTO();
        actionUnitDTO2.setId(102L);
        actionUnitDTO2.setName("Action Unit 2");

        List<ActionUnit> expectedActionUnits = Arrays.asList(actionUnit11, actionUnit22);

        // 2. Configuration des mocks
        when(actionUnitRepository.findByTeamMemberOrCreatorAndInstitution(memberId, institutionId))
                .thenReturn(expectedActionUnits);

        when(actionUnitMapper.convert(actionUnit11)).thenReturn(actionUnitDTO1);
        when(actionUnitMapper.convert(actionUnit22)).thenReturn(actionUnitDTO2);

        // 3. Appel de la méthode à tester
        List<ActionUnitDTO> result = actionUnitService.findByTeamMember(memberDto, institutionDto);

        // 4. Vérification des résultats
        assertNotNull(result, "Le résultat ne doit pas être null");
        assertEquals(2, result.size(), "La liste doit contenir 2 éléments");

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

    @Test
    void existsRootChildrenByInstitution_ShouldReturnTrue_WhenChildrenExist() {
        // Arrange
        Long institutionId = 1L;
        when(actionUnitRepository.existsRootChildrenByInstitution(institutionId))
                .thenReturn(true);

        // Act
        boolean result = actionUnitService.existsRootChildrenByInstitution(institutionId);

        // Assert
        assertTrue(result, "La méthode doit retourner true si des enfants existent.");
    }



}