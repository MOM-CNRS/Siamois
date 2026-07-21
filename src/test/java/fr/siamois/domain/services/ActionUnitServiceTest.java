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
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdLabelRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionUnitServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);


    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private RecordingUnitRepository recordingUnitRepository;
    @Mock private ConceptService conceptService;
    @Mock private ActionCodeRepository actionCodeRepository;
    @Mock private ActionUnitMapper actionUnitMapper;
    @Mock private PersonMapper personMapper;
    @Mock private ConceptMapper conceptMapper;
    @Mock private SpatialUnitRepository spatialUnitRepository;
    @Mock private PersonProfileAssignmentRepository personProfileAssignmentRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private RecordingUnitIdCounterRepository recordingUnitIdCounterRepository;
    @Mock private RecordingUnitIdLabelRepository recordingUnitIdLabelRepository;
    @Mock private ProfileService profileService;
    @Mock private PersonProfileAssignmentService personProfileAssignmentService;
    @Mock private ProfileMapper profileMapper;
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
        verify(personProfileAssignmentService).addToProjectMembers(eq(expectedResult), eq(personDto), anyList());
        verify(personProfileAssignmentService).addToInstitution(eq(institutionDto), eq(personDto), anyList());
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
        current.setCreationTime(NOW);

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
        current.setCreationTime(NOW);

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
        current.setCreationTime(NOW);

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
        current.setCreationTime(NOW);

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
        current.setCreationTime(NOW);

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
        info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("dup");

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId("dup", 1L))
                .thenReturn(Optional.of(new ActionUnit()));

        ConceptDTO conceptDto = new ConceptDTO();
        ActionUnitAlreadyExistsException ex = assertThrows(
                ActionUnitAlreadyExistsException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, conceptDto));
        assertThat(ex.getMessage()).contains("dup");
    }

    @Test
    void saveNotTransactional_identifierAlreadyExists_throws() {
        info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        dto.setIdentifier("id-1");

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId("name", 1L)).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("id-1", 1L))
                .thenReturn(Optional.of(new ActionUnit()));

        ConceptDTO conceptDto = new ConceptDTO();
        ActionUnitAlreadyExistsException ex = assertThrows(
                ActionUnitAlreadyExistsException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, conceptDto));
        assertThat(ex.getMessage()).contains("id-1");
    }

    @Test
    void saveNotTransactional_nullIdentifierAndNullFullIdentifier_throws() {
        info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        // identifier and fullIdentifier are null

        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());

        ConceptDTO emptyConcept = new ConceptDTO();

        assertThrows(NullActionUnitIdentifierException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, emptyConcept));
    }

    @Test
    void saveNotTransactional_setsCreationTimeAndFullIdentifierFromIdentifier() throws ActionUnitAlreadyExistsException {
        info = userInfo(1L);
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
        info = userInfo(1L);
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setName("name");
        dto.setIdentifier("id");
        SpatialUnitSummaryDTO existingLoc = new SpatialUnitSummaryDTO();
        existingLoc.setId(7L);
        dto.setMainLocation(existingLoc);

        ActionUnit entity = new ActionUnit();
        SpatialUnit existingMainLoc = new SpatialUnit();
        existingMainLoc.setId(7L);
        when(actionUnitRepository.findByNameAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId(any(), any())).thenReturn(Optional.empty());
        when(actionUnitMapper.invertConvert(dto)).thenReturn(entity);
        when(conceptService.saveOrGetConcept(any(ConceptDTO.class))).thenReturn(new Concept());
        when(personMapper.invertConvert(any())).thenReturn(new Person());
        when(spatialUnitRepository.findById(7L)).thenReturn(Optional.of(existingMainLoc));
        when(actionUnitRepository.save(entity)).thenReturn(entity);

        actionUnitService.saveNotTransactional(info, dto, new ConceptDTO());

        verify(spatialUnitRepository, never()).save(any(SpatialUnit.class));
        verify(spatialUnitRepository).findById(7L);
        assertSame(existingMainLoc, entity.getMainLocation());
    }

    @Test
    void saveNotTransactional_newMainLocation_isSaved() throws ActionUnitAlreadyExistsException {
        info = userInfo(1L);
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
        info = userInfo(1L);
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
        info = userInfo(1L);
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
        info = userInfo(1L);
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

        ConceptDTO emptyDto = new ConceptDTO();

        assertThrows(FailedActionUnitSaveException.class,
                () -> actionUnitService.saveNotTransactional(info, dto, emptyDto));
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
        // ActionUnit#equals keys on fullIdentifier; ActionUnitDTO#equals (Lombok @Data) keys
        // on every own field so we differentiate via name + fullIdentifier.
        actionUnit1.setFullIdentifier("INST-1");
        actionUnit2.setFullIdentifier("INST-2");
        actionUnit1dto.setName("dto-1");
        actionUnit2dto.setName("dto-2");
        when(actionUnitRepository.findByCreatedByInstitutionId(1L))
                .thenReturn(new HashSet<>(List.of(actionUnit1, actionUnit2)));
        when(actionUnitMapper.convert(any(ActionUnit.class)))
                .thenAnswer(inv -> inv.getArgument(0) == actionUnit1 ? actionUnit1dto : actionUnit2dto);

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
        au.setBeginDate(NOW.minusDays(1));
        assertTrue(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_nowInRange_returnsTrue() {
        try (MockedStatic<OffsetDateTime> mocked = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            mocked.when(OffsetDateTime::now).thenReturn(NOW);
            ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
            au.setBeginDate(NOW.minusDays(1));
            au.setEndDate(NOW.plusDays(1));
            assertTrue(actionUnitService.isActionUnitStillOngoing(au));
        }
    }

    @Test
    void isActionUnitStillOngoing_nowBeforeBegin_returnsFalse() {
        try (MockedStatic<OffsetDateTime> mocked = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            mocked.when(OffsetDateTime::now).thenReturn(NOW);
            ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
            au.setBeginDate(NOW.plusDays(1));
            au.setEndDate(NOW.plusDays(2));
            assertFalse(actionUnitService.isActionUnitStillOngoing(au));
        }
    }

    @Test
    void isActionUnitStillOngoing_nowAfterEnd_returnsFalse() {
        try (MockedStatic<OffsetDateTime> mocked = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            mocked.when(OffsetDateTime::now).thenReturn(NOW);
            ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
            au.setBeginDate(NOW.minusDays(2));
            au.setEndDate(NOW.minusDays(1));
            assertFalse(actionUnitService.isActionUnitStillOngoing(au));
        }
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

    // ------------------------------------------------------------------
    // findAccessibleProjects (API liste projets)
    // ------------------------------------------------------------------

    @Test
    void findAccessibleProjects_emptyInstitutions_returnsEmptyPageWithoutDb() {
        Pageable pageable1 = PageRequest.of(0, 20);

        Page<AccessibleProjectForApi> page1 = actionUnitService.findAccessibleProjects(
                 1L,
                 Set.of(), null, null, pageable1);

        assertThat(page1.getContent()).isEmpty();
        assertThat(page1.getTotalElements()).isZero();
        verifyNoInteractions(actionUnitRepository);
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void findAccessibleProjects_noActionUnits_skipsCountQueries() {
        when(actionUnitRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Page<AccessibleProjectForApi> page1 = actionUnitService.findAccessibleProjects(
                 1L,
                 Set.of(1L), null, null, PageRequest.of(0, 20));

        assertThat(page1.getContent()).isEmpty();
        assertThat(page1.getTotalElements()).isZero();
        verify(recordingUnitRepository, never()).countRecordingUnitsGroupedByActionUnitIds(any());
        verify(actionUnitRepository, never()).countChildActionUnitsByParentIds(any());
    }

    @Test
    void findAccessibleProjects_mapsDtosAndMergesAggregateCounts() {
        // ActionUnit.equals() utilise fullIdentifier : sans valeurs distinctes, Mockito confond les arguments de convert().
        actionUnit1.setFullIdentifier("TEST-FIND-AU-1");
        actionUnit2.setFullIdentifier("TEST-FIND-AU-2");
        actionUnit1dto.setId(1L);
        actionUnit2dto.setId(2L);

        when(actionUnitRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(actionUnit1, actionUnit2), PageRequest.of(0, 20, Sort.by("name")), 2));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(actionUnitMapper.convert(actionUnit2)).thenReturn(actionUnit2dto);
        List<Object[]> ruRows = new ArrayList<>();
        ruRows.add(new Object[]{1L, 5L});
        ruRows.add(new Object[]{2L, 3L});
        when(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(List.of(1L, 2L)))
                .thenReturn(ruRows);
        List<Object[]> childRows = new ArrayList<>();
        childRows.add(new Object[]{1L, 1L});
        when(actionUnitRepository.countChildActionUnitsByParentIds(List.of(1L, 2L)))
                .thenReturn(childRows);

        Page<AccessibleProjectForApi> page1 = actionUnitService.findAccessibleProjects(
                 1L,
                 Set.of(10L), null, "alpha", PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page1.getTotalElements()).isEqualTo(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getContent().get(0).actionUnit().getId()).isEqualTo(1L);
        assertThat(page1.getContent().get(1).actionUnit().getId()).isEqualTo(2L);
        assertThat(page1.getContent().get(0).recordingUnitCount()).isEqualTo(5L);
        assertThat(page1.getContent().get(0).childActionUnitCount()).isEqualTo(1L);
        assertThat(page1.getContent().get(1).recordingUnitCount()).isEqualTo(3L);
        assertThat(page1.getContent().get(1).childActionUnitCount()).isZero();
        verify(recordingUnitRepository).countRecordingUnitsGroupedByActionUnitIds(List.of(1L, 2L));
        verify(actionUnitRepository).countChildActionUnitsByParentIds(List.of(1L, 2L));
    }

    // ------------------------------------------------------------------
    // findAccessibleProjectByKey
    // ------------------------------------------------------------------

    @Test
    void findAccessibleProjectByKey_emptyInstitutions_throws() {
        assertThrows(ActionUnitNotFoundException.class,
                () -> actionUnitService.findAccessibleProjectByKey("1", Set.of()));
    }

    @Test
    void findAccessibleProjectByKey_unknownNumericId_throws() {
        when(actionUnitRepository.findById(99L)).thenReturn(Optional.empty());

        Set<Long> institutionIds = Set.of(10L);
        assertThrows(ActionUnitNotFoundException.class,
                () -> actionUnitService.findAccessibleProjectByKey("99", institutionIds));
    }

    @Test
    void findAccessibleProjectByKey_wrongInstitution_throws() {
        actionUnit1.setFullIdentifier("DETAIL-AU-WRONG");
        actionUnit1.setId(5L);
        actionUnit1dto.setId(5L);
        when(actionUnitRepository.findById(5L)).thenReturn(Optional.of(actionUnit1));
        InstitutionDTO wrongInst = new InstitutionDTO();
        wrongInst.setId(200L);
        actionUnit1dto.setCreatedByInstitution(wrongInst);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        Set<Long> institutionIds = Set.of(100L);
        assertThrows(ActionUnitNotFoundException.class,
                () -> actionUnitService.findAccessibleProjectByKey("5", institutionIds));
    }

    @Test
    void findAccessibleProjectByKey_returnsRowWithCounts_forNumericKey() {
        actionUnit1.setFullIdentifier("DETAIL-AU-OK");
        actionUnit1.setId(5L);
        actionUnit1dto.setId(5L);
        when(actionUnitRepository.findById(5L)).thenReturn(Optional.of(actionUnit1));
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(100L);
        actionUnit1dto.setCreatedByInstitution(inst);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        List<Object[]> ruRows = new ArrayList<>();
        ruRows.add(new Object[]{5L, 7L});
        when(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(List.of(5L))).thenReturn(ruRows);
        List<Object[]> childRows = new ArrayList<>();
        childRows.add(new Object[]{5L, 2L});
        when(actionUnitRepository.countChildActionUnitsByParentIds(List.of(5L))).thenReturn(childRows);

        AccessibleProjectForApi result = actionUnitService.findAccessibleProjectByKey("5", Set.of(100L));

        assertThat(result.actionUnit().getId()).isEqualTo(5L);
        assertThat(result.recordingUnitCount()).isEqualTo(7L);
        assertThat(result.childActionUnitCount()).isEqualTo(2L);
    }

    @Test
    void findAccessibleProjectByKey_resolvesByFullIdentifierWhenNonNumeric() {
        actionUnit1.setFullIdentifier("INST-PROJ-2025");
        actionUnit1.setId(5L);
        actionUnit1dto.setId(5L);
        when(actionUnitRepository.findByFullIdentifier("INST-PROJ-2025")).thenReturn(Optional.of(actionUnit1));
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(100L);
        actionUnit1dto.setCreatedByInstitution(inst);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(List.of(5L)))
                .thenReturn(new ArrayList<>());
        when(actionUnitRepository.countChildActionUnitsByParentIds(List.of(5L)))
                .thenReturn(new ArrayList<>());

        AccessibleProjectForApi result = actionUnitService.findAccessibleProjectByKey("INST-PROJ-2025", Set.of(100L));

        assertThat(result.actionUnit().getId()).isEqualTo(5L);
        verify(actionUnitRepository, never()).findById(anyLong());
    }

    @Test
    void findAccessibleProjectByKey_resolvesByIdentifierWhenFullIdentifierUnknown() {
        when(actionUnitRepository.findByFullIdentifier("C309_01")).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("C309_01", 100L))
                .thenReturn(Optional.of(actionUnit1));
        actionUnit1.setFullIdentifier("SHORT-ID-TEST-AU");
        actionUnit1.setId(33L);
        actionUnit1dto.setId(33L);
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(100L);
        actionUnit1dto.setCreatedByInstitution(inst);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(List.of(33L)))
                .thenReturn(new ArrayList<>());
        when(actionUnitRepository.countChildActionUnitsByParentIds(List.of(33L)))
                .thenReturn(new ArrayList<>());

        AccessibleProjectForApi result = actionUnitService.findAccessibleProjectByKey("C309_01", Set.of(100L));

        assertThat(result.actionUnit().getId()).isEqualTo(33L);
        verify(actionUnitRepository).findByFullIdentifier("C309_01");
        verify(actionUnitRepository).findByIdentifierAndCreatedByInstitutionId("C309_01", 100L);
    }

    @Test
    void findAccessibleProjectByKey_scansInstitutionsForIdentifierUntilFound() {
        when(actionUnitRepository.findByFullIdentifier("ID-SHORT")).thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("ID-SHORT", 100L))
                .thenReturn(Optional.empty());
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionId("ID-SHORT", 200L))
                .thenReturn(Optional.of(actionUnit1));

        actionUnit1.setFullIdentifier("AU-MULTI-INST");
        actionUnit1.setId(77L);
        actionUnit1dto.setId(77L);
        InstitutionDTO inst200 = new InstitutionDTO();
        inst200.setId(200L);
        actionUnit1dto.setCreatedByInstitution(inst200);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(recordingUnitRepository.countRecordingUnitsGroupedByActionUnitIds(List.of(77L)))
                .thenReturn(new ArrayList<>());
        when(actionUnitRepository.countChildActionUnitsByParentIds(List.of(77L)))
                .thenReturn(new ArrayList<>());

        Set<Long> institutionsInScanOrder = new LinkedHashSet<>();
        institutionsInScanOrder.add(100L);
        institutionsInScanOrder.add(200L);
        AccessibleProjectForApi result = actionUnitService.findAccessibleProjectByKey("ID-SHORT", institutionsInScanOrder);

        assertThat(result.actionUnit().getId()).isEqualTo(77L);
        verify(actionUnitRepository).findByIdentifierAndCreatedByInstitutionId("ID-SHORT", 100L);
        verify(actionUnitRepository).findByIdentifierAndCreatedByInstitutionId("ID-SHORT", 200L);

    }
    // findAllByActionManager
    // ------------------------------------------------------------------

    @Test
    void findAllByActionManager_nullUser_returnsEmptySet() {
        assertTrue(actionUnitService.findAllByActionManager(null).isEmpty());
        verifyNoInteractions(actionUnitRepository);
    }

    @Test
    void findAllByActionManager_userWithNullId_returnsEmptySet() {
        PersonDTO user = new PersonDTO();
        // id is null by default
        assertTrue(actionUnitService.findAllByActionManager(user).isEmpty());
        verifyNoInteractions(actionUnitRepository);
    }

    @Test
    void findAllByActionManager_noUnitsFound_returnsEmptySet() {
        PersonDTO user = new PersonDTO();
        user.setId(5L);
        when(actionUnitRepository.findAllByCreatedById(5L)).thenReturn(Set.of());

        assertTrue(actionUnitService.findAllByActionManager(user).isEmpty());
    }

    @Test
    void findAllByActionManager_unitsFound_delegatesToRepositoryAndMapsEachUnit() {
        PersonDTO user = new PersonDTO();
        user.setId(5L);
        actionUnit1.setFullIdentifier("inst-AU-1");
        actionUnit2.setFullIdentifier("inst-AU-2");
        Set<ActionUnit> units = new HashSet<>(List.of(actionUnit1, actionUnit2));
        when(actionUnitRepository.findAllByCreatedById(5L)).thenReturn(units);
        when(actionUnitMapper.convert(any(ActionUnit.class))).thenReturn(actionUnit1dto);

        Set<ActionUnitDTO> result = actionUnitService.findAllByActionManager(user);

        assertNotNull(result);
        verify(actionUnitRepository).findAllByCreatedById(5L);
        verify(actionUnitMapper, times(2)).convert(any(ActionUnit.class));
    }

    @Test
    void findAllByActionManager_returnsSet_notList() {
        PersonDTO user = new PersonDTO();
        user.setId(7L);
        when(actionUnitRepository.findAllByCreatedById(7L)).thenReturn(Set.of(actionUnit1));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);

        assertInstanceOf(Set.class, actionUnitService.findAllByActionManager(user));
    }

    // ------------------------------------------------------------------
    // searchActionUnitsInSpatialUnit
    // ------------------------------------------------------------------

    @Test
    void searchActionUnitsInSpatialUnit_happyPath_mapsResultsWithCount() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(false);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(actionUnitMapper.convert(actionUnit2)).thenReturn(actionUnit2dto);
        when(recordingUnitRepository.countByActionContext(any())).thenReturn(0);

        Page<ActionUnitDTO> result =
                actionUnitService.searchActionUnitsInSpatialUnit(inst, su, filters, pageable);

        assertEquals(2, result.getTotalElements());
        verify(actionUnitRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchActionUnitsInSpatialUnit_emptyPage_returnsEmptyAndSkipsMapper() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(false);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ActionUnitDTO> result =
                actionUnitService.searchActionUnitsInSpatialUnit(inst, su, filters, pageable);

        assertTrue(result.isEmpty());
        verifyNoInteractions(actionUnitMapper);
    }

    @Test
    void searchActionUnitsInSpatialUnit_rootOnlyFalse_neverCallsListVariant() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(false);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        actionUnitService.searchActionUnitsInSpatialUnit(inst, su, filters, pageable);

        verify(actionUnitRepository, never()).findAll(any(Specification.class));
    }

    @Test
    void searchActionUnitsInSpatialUnit_convertWithCount_setsRecordingUnitCount() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(false);
        actionUnit1.setId(1L);

        when(actionUnitRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(actionUnit1)));
        when(actionUnitMapper.convert(actionUnit1)).thenReturn(actionUnit1dto);
        when(recordingUnitRepository.countByActionContext(1L)).thenReturn(7);

        Page<ActionUnitDTO> result =
                actionUnitService.searchActionUnitsInSpatialUnit(inst, su, filters, pageable);

        assertEquals(7, result.getContent().get(0).getRecordingUnitCount());
    }

    // ------------------------------------------------------------------
    // countSearchResultsInSpatialUnit
    // ------------------------------------------------------------------

    @Test
    void countSearchResultsInSpatialUnit_noFilters_delegatesToCount() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(false);

        when(actionUnitRepository.count(any(Specification.class))).thenReturn(5L);

        assertEquals(5, actionUnitService.countSearchResultsInSpatialUnit(inst, su, filters));
        verify(actionUnitRepository).count(any(Specification.class));
    }

    @Test
    void countSearchResultsInSpatialUnit_withNameFilter_delegatesToCount() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(false);
        filters.add(ActionUnitSpec.NAME_FILTER, "test", FilterDTO.FilterType.CONTAINS);

        when(actionUnitRepository.count(any(Specification.class))).thenReturn(2L);

        assertEquals(2, actionUnitService.countSearchResultsInSpatialUnit(inst, su, filters));
    }

    @Test
    void countSearchResultsInSpatialUnit_rootOnlyTrue_noUserFilters_neverCallsListFindAll() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(true);

        when(actionUnitRepository.count(any(Specification.class))).thenReturn(3L);

        int result = actionUnitService.countSearchResultsInSpatialUnit(inst, su, filters);

        assertEquals(3, result);
        verify(actionUnitRepository, never()).findAll(any(Specification.class));
    }

    @Test
    void countSearchResultsInSpatialUnit_rootOnlyTrue_withUserFilters_resolvesClosureThenCounts() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "fouille", FilterDTO.FilterType.CONTAINS);

        actionUnit1.setId(1L);
        when(actionUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(actionUnit1));
        when(actionUnitRepository.findAncestorClosure(new Long[]{1L})).thenReturn(List.of(1L));
        when(actionUnitRepository.count(any(Specification.class))).thenReturn(1L);

        int result = actionUnitService.countSearchResultsInSpatialUnit(inst, su, filters);

        assertEquals(1, result);
        verify(actionUnitRepository).findAll(any(Specification.class));
        verify(actionUnitRepository).findAncestorClosure(new Long[]{1L});
    }

    @Test
    void countSearchResultsInSpatialUnit_rootOnlyTrue_noMatches_returnsZero() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "absent", FilterDTO.FilterType.CONTAINS);

        when(actionUnitRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(actionUnitRepository.count(any(Specification.class))).thenReturn(0L);

        int result = actionUnitService.countSearchResultsInSpatialUnit(inst, su, filters);

        assertEquals(0, result);
        verify(actionUnitRepository, never()).findAncestorClosure(any());
    }

    @Test
    void countSearchResultsInSpatialUnit_cachedClosure_skipsFindAllListVariant() {
        InstitutionDTO inst = new InstitutionDTO(); inst.setId(1L);
        SpatialUnitDTO su   = new SpatialUnitDTO(); su.setId(3L);
        FilterDTO filters   = new FilterDTO(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "fouille", FilterDTO.FilterType.CONTAINS);
        filters.setAncestorClosure(Set.of(1L, 2L));

        when(actionUnitRepository.count(any(Specification.class))).thenReturn(2L);

        actionUnitService.countSearchResultsInSpatialUnit(inst, su, filters);

        verify(actionUnitRepository, never()).findAll(any(Specification.class));
        verify(actionUnitRepository, never()).findAncestorClosure(any());
    }

}