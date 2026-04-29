package fr.siamois.domain.services;


import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    @Mock private ConceptMapper conceptMapper;
    @Mock private SpatialUnitRepository spatialUnitRepository;
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

        SpatialUnit newMainLocation = new SpatialUnit();
        newMainLocation.setName("New");
        actionUnit.setMainLocation(newMainLocation);
        actionUnit.setSpatialContext(Set.of(newMainLocation));
        SpatialUnitSummaryDTO newMainLocationDto = new SpatialUnitSummaryDTO();
        newMainLocationDto.setName("New");
        actionUnitDto.setMainLocation(newMainLocationDto);
        actionUnitDto.setSpatialContext(Set.of(newMainLocationDto));

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
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenReturn(new SpatialUnit());
        when(conceptMapper.invertConvert(null)).thenReturn(null);

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

    // --- Tests for findNextByInstitution ---

    @Test
    void testFindNextByInstitution_ShouldReturnNextActionUnit() {
        // Arrange
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        ActionUnitDTO current = new ActionUnitDTO();
        current.setId(100L);
        current.setCreationTime(OffsetDateTime.now());

        ActionUnit nextEntity = new ActionUnit();
        ActionUnitDTO nextDTO = new ActionUnitDTO();

        when(actionUnitRepository.findNext(eq(1L), any(), eq(100L)))
                .thenReturn(Optional.of(nextEntity));
        when(actionUnitMapper.convert(nextEntity)).thenReturn(nextDTO);

        // Act
        ActionUnitDTO result = actionUnitService.findNextByInstitution(institution, current);

        // Assert
        assertEquals(nextDTO, result);
        verify(actionUnitRepository, never()).findFirst(anyLong());
    }

    @Test
    void testFindNextByInstitution_ShouldWrapAroundToFirst() {
        // 1. Préparation des données avec des IDs cohérents
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);

        ActionUnitDTO current = new ActionUnitDTO();
        current.setId(100L); // Évitez de laisser l'ID à null si possible
        current.setCreationTime(OffsetDateTime.now());

        ActionUnit firstEntity = new ActionUnit();
        ActionUnitDTO firstDTO = new ActionUnitDTO();

        // 2. Mocking avec des matchers flexibles
        // On utilise eq(1L) pour l'institution, n'importe quelle date, et n'importe quel ID (ou null)
        when(actionUnitRepository.findNext(eq(1L), any(), any()))
                .thenReturn(Optional.empty());

        when(actionUnitRepository.findFirst(1L))
                .thenReturn(Optional.of(firstEntity));

        when(actionUnitMapper.convert(firstEntity)).thenReturn(firstDTO);

        // 3. Appel
        ActionUnitDTO result = actionUnitService.findNextByInstitution(institution, current);

        // 4. Assertions
        assertEquals(firstDTO, result);
    }
    // --- Tests for findPreviousByInstitution ---

    @Test
    void testFindPreviousByInstitution_ShouldReturnPreviousActionUnit() {
        // Arrange
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        ActionUnitDTO current = new ActionUnitDTO();
        current.setId(100L);
        current.setCreationTime(OffsetDateTime.now());

        ActionUnit prevEntity = new ActionUnit();
        ActionUnitDTO prevDTO = new ActionUnitDTO();

        when(actionUnitRepository.findPrevious(eq(1L), any(), eq(100L)))
                .thenReturn(Optional.of(prevEntity));
        when(actionUnitMapper.convert(prevEntity)).thenReturn(prevDTO);

        // Act
        ActionUnitDTO result = actionUnitService.findPreviousByInstitution(institution, current);

        // Assert
        assertEquals(prevDTO, result);
    }

    @Test
    void testFindPreviousByInstitution_ShouldWrapAroundToLast() {
        // 1. Arrange - Préparation avec des IDs qui correspondent
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L); // L'ID utilisé par le service

        ActionUnitDTO current = new ActionUnitDTO();
        current.setId(100L); // Un ID pour éviter le null (ou utilisez any() dans le mock)
        current.setCreationTime(OffsetDateTime.now());

        ActionUnit lastEntity = new ActionUnit();
        ActionUnitDTO lastDTO = new ActionUnitDTO();

        // 2. Mocking - Utilisation de matchers flexibles
        // eq(1L) pour l'institution, any() pour la date, any() pour l'ID de l'action unit
        when(actionUnitRepository.findPrevious(eq(1L), any(), any()))
                .thenReturn(Optional.empty());

        // Le fallback vers le dernier élément
        when(actionUnitRepository.findLast(1L))
                .thenReturn(Optional.of(lastEntity));

        when(actionUnitMapper.convert(lastEntity)).thenReturn(lastDTO);

        // 3. Act
        ActionUnitDTO result = actionUnitService.findPreviousByInstitution(institution, current);

        // 4. Assert
        assertNotNull(result);
        assertEquals(lastDTO, result);
    }

    @Test
    void testNavigation_ShouldReturnCurrentIfRepoIsEmpty() {
        // 1. Arrange
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L); // On utilise 1L pour correspondre à l'erreur

        ActionUnitDTO current = new ActionUnitDTO();
        current.setId(100L);
        current.setCreationTime(OffsetDateTime.now());

        // Mock de findNext : on simule qu'aucune entité suivante n'existe
        // On utilise eq(1L) et any() pour la flexibilité
        when(actionUnitRepository.findNext(eq(1L), any(), any()))
                .thenReturn(Optional.empty());

        // Mock de findFirst : on simule que la liste globale est vide pour cette institution
        when(actionUnitRepository.findFirst(1L))
                .thenReturn(Optional.empty());

        // 2. Act
        ActionUnitDTO result = actionUnitService.findNextByInstitution(institution, current);

        // 3. Assert
        // Selon votre code, si findNext et findFirst échouent, on retourne 'current'
        assertEquals(current, result);

        // Vérification optionnelle pour s'assurer que le mapper n'a jamais été appelé
        verifyNoInteractions(actionUnitMapper);
    }

    // --- Tests for toggleValidated ---

    @Test
    void testToggleValidated_ShouldCycleThroughStatuses() {
        // Arrange
        Long id = 50L;
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(id);

        when(actionUnitRepository.findById(id)).thenReturn(Optional.of(actionUnit));
        when(actionUnitRepository.save(any(ActionUnit.class))).thenAnswer(i -> i.getArguments()[0]);
        when(actionUnitMapper.convert(any(ActionUnit.class))).thenReturn(new ActionUnitDTO());

        // INCOMPLETE -> COMPLETE
        actionUnit.setValidated(ValidationStatus.INCOMPLETE);
        actionUnitService.toggleValidated(id);
        assertEquals(ValidationStatus.COMPLETE, actionUnit.getValidated());

        // COMPLETE -> VALIDATED
        actionUnitService.toggleValidated(id);
        assertEquals(ValidationStatus.VALIDATED, actionUnit.getValidated());

        // VALIDATED -> INCOMPLETE
        actionUnitService.toggleValidated(id);
        assertEquals(ValidationStatus.INCOMPLETE, actionUnit.getValidated());
    }

    @Test
    void testToggleValidated_NotFound_ThrowsException() {
        // Arrange
        when(actionUnitRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ActionUnitNotFoundException.class, () -> actionUnitService.toggleValidated(999L));
    }

    // ------------------------------------------------------------------
    // saveNotTransactional — branches
    // ------------------------------------------------------------------

    private UserInfo userInfo(long institutionId) {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(institutionId);
        inst.setName("Inst");
        PersonDTO person = new PersonDTO();
        person.setId(99L);
        return new UserInfo(inst, person, "fr");
    }

    @Test
    void saveNotTransactional_nameAlreadyExists_throws() {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("dup");

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId("dup", 1L))
                .thenReturn(Optional.of(new ActionUnit()));

        ActionUnitAlreadyExistsException ex = assertThrows(
                ActionUnitAlreadyExistsException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, new ConceptDTO()));
        assertThat(ex.getMessage()).contains("dup");
    }

    @Test
    void saveNotTransactional_identifierAlreadyExists_throws() {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        dto.setIdentifier("id-1");

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId("name", 1L)).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("id-1", 1L))
                .thenReturn(Optional.of(new ActionUnit()));

        ActionUnitAlreadyExistsException ex = assertThrows(
                ActionUnitAlreadyExistsException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, new ConceptDTO()));
        assertThat(ex.getMessage()).contains("id-1");
    }

    @Test
    void saveNotTransactional_nullIdentifierAndNullFullIdentifier_throws() {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        // identifier and fullIdentifier are null

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());

        assertThrows(NullActionUnitIdentifierException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, new ConceptDTO()));
    }

    @Test
    void saveNotTransactional_setsCreationTimeAndFullIdentifierFromIdentifier() throws ActionUnitAlreadyExistsException {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        dto.setIdentifier("ABC");
        // creationTime null and fullIdentifier null

        ActionUnit entity = new ActionUnit();
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        when(actionUnitRepository.save(entity)).thenReturn(entity);

        ActionUnit saved = actionUnitService.saveNotTransactional(info, dto, new ConceptDTO());

        assertSame(entity, saved);
        assertEquals("ABC", dto.getFullIdentifier());
        assertNotNull(dto.getCreationTime());
        assertSame(info.getInstitution(), dto.getCreatedByInstitution());
    }

    @Test
    void saveNotTransactional_existingMainLocation_isNotReSaved() throws ActionUnitAlreadyExistsException {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        dto.setIdentifier("id");
        SpatialUnitSummaryDTO existingLoc = new SpatialUnitSummaryDTO();
        existingLoc.setId(7L);
        dto.setMainLocation(existingLoc);

        ActionUnit entity = new ActionUnit();
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        when(actionUnitRepository.save(entity)).thenReturn(entity);

        actionUnitService.saveNotTransactional(info, dto, new ConceptDTO());

        verify(spatialUnitRepository, never()).save(any(SpatialUnit.class));
    }

    @Test
    void saveNotTransactional_newMainLocation_isSaved() throws ActionUnitAlreadyExistsException {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        dto.setIdentifier("id");
        SpatialUnitSummaryDTO newLoc = new SpatialUnitSummaryDTO();
        newLoc.setName("New place");
        newLoc.setCode("CODE");
        dto.setMainLocation(newLoc);

        ActionUnit entity = new ActionUnit();
        SpatialUnit existingMainLoc = new SpatialUnit();
        existingMainLoc.setCategory(new Concept());
        entity.setMainLocation(existingMainLoc);

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        SpatialUnit savedLoc = new SpatialUnit();
        savedLoc.setId(42L);
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenReturn(savedLoc);
        when(actionUnitRepository.save(entity)).thenReturn(entity);

        actionUnitService.saveNotTransactional(info, dto, new ConceptDTO());

        verify(spatialUnitRepository).save(any(SpatialUnit.class));
        assertSame(savedLoc, entity.getMainLocation());
    }

    @Test
    void saveNotTransactional_spatialContextWithNewAndExistingEntries_handlesBoth() throws ActionUnitAlreadyExistsException {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("n");
        dto.setIdentifier("i");

        SpatialUnitSummaryDTO existingPlace = new SpatialUnitSummaryDTO();
        existingPlace.setId(11L);

        SpatialUnitSummaryDTO newPlace = new SpatialUnitSummaryDTO();
        newPlace.setName("Brand new");
        newPlace.setCategory(new ConceptDTO());

        dto.setSpatialContext(Set.of(existingPlace, newPlace));

        ActionUnit entity = new ActionUnit();
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        when(conceptMapper.invertConvert(any(ConceptDTO.class))).thenReturn(new Concept());
        SpatialUnit savedNew = new SpatialUnit();
        savedNew.setId(99L);
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenReturn(savedNew);
        SpatialUnit existingEntity = new SpatialUnit();
        existingEntity.setId(11L);
        when(spatialUnitRepository.findById(11L)).thenReturn(Optional.of(existingEntity));
        when(actionUnitRepository.save(entity)).thenReturn(entity);

        actionUnitService.saveNotTransactional(info, dto, new ConceptDTO());

        verify(spatialUnitRepository).save(any(SpatialUnit.class));
        verify(spatialUnitRepository).findById(11L);
        assertEquals(2, entity.getSpatialContext().size());
    }

    @Test
    void saveNotTransactional_existingPlaceMissingInRepository_isOmitted() throws ActionUnitAlreadyExistsException {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("n");
        dto.setIdentifier("i");

        SpatialUnitSummaryDTO existingPlace = new SpatialUnitSummaryDTO();
        existingPlace.setId(11L);
        dto.setSpatialContext(Set.of(existingPlace));

        ActionUnit entity = new ActionUnit();
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        when(spatialUnitRepository.findById(11L)).thenReturn(Optional.empty());
        when(actionUnitRepository.save(entity)).thenReturn(entity);

        actionUnitService.saveNotTransactional(info, dto, new ConceptDTO());

        assertTrue(entity.getSpatialContext().isEmpty());
    }

    @Test
    void saveNotTransactional_repositorySaveFailure_wrapsAsFailedActionUnitSaveException() {
        UserInfo info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("n");
        dto.setIdentifier("i");

        ActionUnit entity = new ActionUnit();
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        when(actionUnitRepository.save(entity)).thenThrow(new RuntimeException("boom"));

        assertThrows(FailedActionUnitSaveException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, new ConceptDTO()));
    }

    // ------------------------------------------------------------------
    // findByArk / findWithoutArk
    // ------------------------------------------------------------------

    @Test
    void findByArk_returnsOptionalFromRepository() {
        Ark ark = new Ark();
        when(actionUnitRepository.findByArk(ark)).thenReturn(Optional.of(actionUnit1));

        Optional<ActionUnit> result = actionUnitService.findByArk(ark);

        assertTrue(result.isPresent());
        assertSame(actionUnit1, result.get());
    }

    @Test
    void findWithoutArk_delegatesToRepository() {
        Institution inst = new Institution();
        inst.setId(1L);
        when(actionUnitRepository.findAllByArkIsNullAndCreatedByInstitution(inst))
                .thenReturn(List.of(actionUnit1, actionUnit2));

        List<ActionUnit> result = actionUnitService.findWithoutArk(inst);

        assertEquals(2, result.size());
    }

    // ------------------------------------------------------------------
    // save(AbstractEntityDTO)
    // ------------------------------------------------------------------

    @Test
    void save_AbstractEntity_happyPath() {
        when(actionUnitMapper.invertConvert(actionUnit1dto)).thenReturn(actionUnit1);
        when(actionUnitRepository.save(actionUnit1)).thenReturn(actionUnit1);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        AbstractEntityDTO result = actionUnitService.save(actionUnit1dto);

        assertSame(actionUnit1dto, result);
    }

    @Test
    void save_AbstractEntity_dataIntegrityViolation_wraps() {
        when(actionUnitMapper.invertConvert(actionUnit1dto)).thenReturn(actionUnit1);
        when(actionUnitRepository.save(actionUnit1)).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(FailedActionUnitSaveException.class,
                () -> actionUnitService.save(actionUnit1dto));
    }

    // ------------------------------------------------------------------
    // count delegators
    // ------------------------------------------------------------------

    @Test
    void countByInstitutionId_delegatesToRepository() {
        when(actionUnitRepository.countByCreatedByInstitutionId(5L)).thenReturn(7L);
        assertEquals(7L, actionUnitService.countByInstitutionId(5L));
    }

    @Test
    void countBySpatialContext_delegatesToRepository() {
        SpatialUnitDTO su = new SpatialUnitDTO();
        su.setId(3L);
        when(actionUnitRepository.countBySpatialContext(3L)).thenReturn(4);

        assertEquals(4, actionUnitService.countBySpatialContext(su));
    }

    @Test
    void countRootsInInstitution_delegatesToRepository() {
        when(actionUnitRepository.countRootsInInstitution(8L)).thenReturn(11);
        assertEquals(11, actionUnitService.countRootsInInstitution(8L));
    }

    @Test
    void countRootsByInstitutionAndName_delegatesToRepository() {
        when(actionUnitRepository.countRootsByInstitutionAndName(1L, "x")).thenReturn(2);
        assertEquals(2, actionUnitService.countRootsByInstitutionAndName(1L, "x"));
    }

    // ------------------------------------------------------------------
    // findAll* delegators that map entities to DTOs
    // ------------------------------------------------------------------

    @Test
    void findAllByInstitution_mapsToDtoSet() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        // ActionUnit#equals/ActionUnitDTO#equals key on fullIdentifier/id — give distinct
        // values so Set dedup doesn't swallow rows.
        actionUnit1.setFullIdentifier("INST-1");
        actionUnit2.setFullIdentifier("INST-2");
        actionUnit1dto.setId(1L);
        actionUnit2dto.setId(2L);
        when(actionUnitRepository.findByCreatedByInstitutionId(1L))
                .thenReturn(new HashSet<>(List.of(actionUnit1, actionUnit2)));
        when(actionUnitMapper.convert(any(ActionUnit.class)))
                .thenAnswer(inv -> ((ActionUnit) inv.getArgument(0)) == actionUnit1 ? actionUnit1dto : actionUnit2dto);

        Set<ActionUnitDTO> result = actionUnitService.findAllByInstitution(inst);

        assertEquals(2, result.size());
        assertThat(result).containsExactlyInAnyOrder(actionUnit1dto, actionUnit2dto);
    }

    @Test
    void findAllWithoutParentsByInstitution_mapsToDtos() {
        when(actionUnitRepository.findRootsByInstitution(1L, 50L)).thenReturn(List.of(actionUnit1));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        List<ActionUnitDTO> result = actionUnitService.findAllWithoutParentsByInstitution(1L);

        assertEquals(List.of(actionUnit1dto), result);
    }

    @Test
    void findChildrenByParentAndInstitution_mapsToDtos() {
        when(actionUnitRepository.findChildrenByParentAndInstitution(1L, 2L)).thenReturn(List.of(actionUnit1));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        List<ActionUnitDTO> result = actionUnitService.findChildrenByParentAndInstitution(1L, 2L);

        assertEquals(List.of(actionUnit1dto), result);
    }

    @Test
    void findBySpatialContext_mapsToDtos() {
        when(actionUnitRepository.findBySpatialContext(9L)).thenReturn(List.of(actionUnit1));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        List<ActionUnitDTO> result = actionUnitService.findBySpatialContext(9L);

        assertEquals(List.of(actionUnit1dto), result);
    }

    @Test
    void findByTeamMember_mapsToDtos() {
        PersonDTO member = new PersonDTO();
        member.setId(3L);
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(4L);
        when(actionUnitRepository.findByTeamMemberOrCreatorAndInstitutionLimit(3L, 4L, 5L))
                .thenReturn(List.of(actionUnit1));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        List<ActionUnitDTO> result = actionUnitService.findByTeamMember(member, inst, 5L);

        assertEquals(List.of(actionUnit1dto), result);
    }

    @Test
    void findRootsByInstitution_delegatesToRepository() {
        when(actionUnitRepository.findRootsByInstitution(1L, 0, 10)).thenReturn(List.of(actionUnit1));
        List<ActionUnit> result = actionUnitService.findRootsByInstitution(1L, 0, 10);
        assertEquals(List.of(actionUnit1), result);
    }

    @Test
    void findRootsByInstitutionAndName_delegatesToRepository() {
        when(actionUnitRepository.findRootsByInstitutionAndName(1L, "n", 0, 5))
                .thenReturn(List.of(actionUnit2));
        List<ActionUnit> result = actionUnitService.findRootsByInstitutionAndName(1L, "n", 0, 5);
        assertEquals(List.of(actionUnit2), result);
    }

    // ------------------------------------------------------------------
    // exists* false branches (truthy ones already covered)
    // ------------------------------------------------------------------

    @Test
    void existsChildrenByParentAndInstitution_returnsFalseWhenRepositoryDoes() {
        when(actionUnitRepository.existsChildrenByParentAndInstitution(1L, 2L)).thenReturn(false);
        assertFalse(actionUnitService.existsChildrenByParentAndInstitution(1L, 2L));
    }

    @Test
    void existsRootChildrenByInstitution_returnsFalseWhenRepositoryDoes() {
        when(actionUnitRepository.existsRootChildrenByInstitution(1L)).thenReturn(false);
        assertFalse(actionUnitService.existsRootChildrenByInstitution(1L));
    }

    @Test
    void existsRootChildrenByRelatedSpatialUnit_delegatesToRepository() {
        when(actionUnitRepository.existsRootChildrenByRelatedSpatialUnit(7L)).thenReturn(true);
        assertTrue(actionUnitService.existsRootChildrenByRelatedSpatialUnit(7L));
    }

    @Test
    void isRoot_delegatesToRepository_trueAndFalse() {
        when(actionUnitRepository.isRoot(1L, 2L)).thenReturn(true);
        when(actionUnitRepository.isRoot(3L, 4L)).thenReturn(false);

        assertTrue(actionUnitService.isRoot(1L, 2L));
        assertFalse(actionUnitService.isRoot(3L, 4L));
    }

    // ------------------------------------------------------------------
    // toggleValidated — IllegalStateException on unknown status
    // ------------------------------------------------------------------

    @Test
    void toggleValidated_unknownStatus_throwsIllegalState() {
        ActionUnit au = new ActionUnit();
        au.setId(1L);
        au.setValidated(null); // unknown / unhandled

        when(actionUnitRepository.findById(1L)).thenReturn(Optional.of(au));

        assertThrows(NullPointerException.class, () -> actionUnitService.toggleValidated(1L));
    }

    // ------------------------------------------------------------------
    // isActionUnitStillOngoing — every branch
    // ------------------------------------------------------------------

    @Test
    void isActionUnitStillOngoing_noBeginDate_returnsFalse() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        assertFalse(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_beginDateButNoEndDate_returnsTrue() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setBeginDate(OffsetDateTime.now().minusDays(1));
        assertTrue(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_nowInRange_returnsTrue() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setBeginDate(OffsetDateTime.now().minusDays(1));
        au.setEndDate(OffsetDateTime.now().plusDays(1));
        assertTrue(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_nowBeforeBegin_returnsFalse() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setBeginDate(OffsetDateTime.now().plusDays(1));
        au.setEndDate(OffsetDateTime.now().plusDays(2));
        assertFalse(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_nowAfterEnd_returnsFalse() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setBeginDate(OffsetDateTime.now().minusDays(2));
        au.setEndDate(OffsetDateTime.now().minusDays(1));
        assertFalse(actionUnitService.isActionUnitStillOngoing(au));
    }

    // ------------------------------------------------------------------
    // isManagerOf
    // ------------------------------------------------------------------

    @Test
    void isManagerOf_sameId_returnsTrue() {
        ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
        PersonDTO creator = new PersonDTO();
        creator.setId(42L);
        action.setCreatedBy(creator);

        PersonDTO same = new PersonDTO();
        same.setId(42L);

        assertTrue(actionUnitService.isManagerOf(action, same));
    }

    @Test
    void isManagerOf_differentId_returnsFalse() {
        ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
        PersonDTO creator = new PersonDTO();
        creator.setId(42L);
        action.setCreatedBy(creator);

        PersonDTO other = new PersonDTO();
        other.setId(43L);

        assertFalse(actionUnitService.isManagerOf(action, other));
    }

    // ------------------------------------------------------------------
    // searchActionUnits / countSearchResults / computeAncestorClosure / prepareSpecs branches
    // ------------------------------------------------------------------

    @Test
    void searchActionUnits_userMode_returnsMappedPage() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(actionUnitMapper.convert(actionUnit2)).thenReturn(actionUnit2dto);

        Page<ActionUnitDTO> result = actionUnitService.searchActionUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
        assertThat(result.getContent()).containsExactly(actionUnit1dto, actionUnit2dto);
    }

    @Test
    void searchActionUnits_withNameFilter_logsAndReturns() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        filters.add(ActionUnitSpec.NAME_FILTER, "foo", FilterDTO.FilterType.CONTAINS);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(actionUnitMapper.convert(any(ActionUnit.class))).thenReturn(actionUnit1dto);

        Page<ActionUnitDTO> result = actionUnitService.searchActionUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void searchActionUnits_withGlobalFilterOnly_runs() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        filters.add(ActionUnitSpec.GLOBAL_FILTER, "g", FilterDTO.FilterType.CONTAINS);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<ActionUnitDTO> result = actionUnitService.searchActionUnits(inst, filters, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void searchActionUnits_rootOnlyWithoutUserFilters_runs() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(actionUnitMapper.convert(any(ActionUnit.class))).thenReturn(actionUnit1dto);

        Page<ActionUnitDTO> result = actionUnitService.searchActionUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void searchActionUnits_rootOnlyWithUserFiltersAndMatches_runs() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "match", FilterDTO.FilterType.CONTAINS);

        when(actionUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(actionUnit1, actionUnit2));
        when(actionUnitRepository.findAncestorClosure(any(Long[].class))).thenReturn(List.of(1L, 2L));
        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(actionUnitMapper.convert(any(ActionUnit.class))).thenReturn(actionUnit1dto);

        Page<ActionUnitDTO> result = actionUnitService.searchActionUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(Set.of(1L, 2L), filters.getMatchIds());
    }

    @Test
    void searchActionUnits_rootOnlyWithUserFiltersNoMatches_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "nope", FilterDTO.FilterType.CONTAINS);

        // No matches: empty closure → disjunction spec → empty page.
        when(actionUnitRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<ActionUnitDTO> result = actionUnitService.searchActionUnits(inst, filters, pageable);

        assertTrue(result.getContent().isEmpty());
        verify(actionUnitRepository, never()).findAncestorClosure(any(Long[].class));
    }

    @Test
    void countSearchResults_delegatesToRepositoryCount() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        when(actionUnitRepository.count(any(Specification.class))).thenReturn(13L);

        assertEquals(13, actionUnitService.countSearchResults(inst, filters));
    }

    @Test
    void computeAncestorClosure_notRootOnly_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        Set<Long> result = actionUnitService.computeAncestorClosure(inst, filters);

        assertEquals(Collections.emptySet(), result);
        verifyNoInteractions(actionUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyButNoUserFilters_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        Set<Long> result = actionUnitService.computeAncestorClosure(inst, filters);

        assertEquals(Collections.emptySet(), result);
        verifyNoInteractions(actionUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyWithUserFilters_returnsClosure() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "x", FilterDTO.FilterType.CONTAINS);

        when(actionUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(actionUnit1));
        when(actionUnitRepository.findAncestorClosure(any(Long[].class))).thenReturn(List.of(1L, 5L));

        Set<Long> result = actionUnitService.computeAncestorClosure(inst, filters);

        assertEquals(Set.of(1L, 5L), result);
    }

    @Test
    void computeAncestorClosure_reusesCachedClosureFromFilter() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "x", FilterDTO.FilterType.CONTAINS);
        filters.setAncestorClosure(List.of(7L, 8L));

        Set<Long> result = actionUnitService.computeAncestorClosure(inst, filters);

        assertEquals(Set.of(7L, 8L), result);
        // No DB call: the closure was precomputed.
        verify(actionUnitRepository, never()).findAll(any(Specification.class));
        verify(actionUnitRepository, never()).findAncestorClosure(any(Long[].class));
    }

    // ------------------------------------------------------------------
    // findMatchingInInstitutionByName
    // ------------------------------------------------------------------

    @Test
    void findMatchingInInstitutionByName_returnsMappedDtos() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);

        when(actionUnitRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(actionUnit1)));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        List<ActionUnitDTO> result = actionUnitService.findMatchingInInstitutionByName(inst, "q", 25);

        assertEquals(List.of(actionUnit1dto), result);
    }

}