package fr.siamois.domain.services.spatialunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.PermissionServiceImpl;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.SpatialUnitSpec;
import fr.siamois.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class SpatialUnitServiceTest {

    @Mock
    private SpatialUnitRepository spatialUnitRepository;

    @Mock
    private PersonService personService;

    @Mock
    private PermissionServiceImpl permissionService;

    @Mock
    private ConceptService conceptService;

    @Mock
    private InstitutionService institutionService;

    @Mock
    private SpatialUnitMapper spatialUnitMapper;
    @Mock
    private RecordingUnitMapper recordingUnitMapper;
    @Mock
    private SpatialUnitSummaryMapper spatialUnitSummaryMapper;
    @Mock
    private InstitutionMapper institutionMapper;
    @Mock
    private ConceptMapper conceptMapper;

    @Mock
    private fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository actionUnitRepository;

    @Mock
    private fr.siamois.domain.services.ark.ArkService arkService;

    @InjectMocks
    private SpatialUnitService spatialUnitService;

    SpatialUnit spatialUnit1;

    SpatialUnit spatialUnit2;

    Page<SpatialUnit> p ;
    Pageable pageable;

    SpatialUnitDTO spatialUnit1DTO;

    SpatialUnitDTO spatialUnit2DTO;

    Page<SpatialUnitDTO> pageDTO ;


    @BeforeEach
    void setUp() {
        spatialUnit1 = new SpatialUnit();
        spatialUnit2 = new SpatialUnit();
        spatialUnit1.setId(1L);
        spatialUnit2.setId(2L);
        p = new PageImpl<>(List.of(spatialUnit1, spatialUnit2));
        pageable = PageRequest.of(0, 10);
        spatialUnit1DTO = new SpatialUnitDTO();
        spatialUnit2DTO = new SpatialUnitDTO();
        spatialUnit1DTO.setId(1L);
        spatialUnit2DTO.setId(2L);
        pageDTO = new PageImpl<>(List.of(spatialUnit1DTO, spatialUnit2DTO));


        lenient().when(spatialUnitRepository.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);
        lenient().when(spatialUnitRepository.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);

    }

    @Test
    void testFindAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(spatialUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);

        // Act
        Page<SpatialUnitDTO> actualResult = spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], new Long[2],"null", "fr", pageable
        );

        // Assert
        assertThat(actualResult)
                .hasSize(2);
    }

    @Test
    void testFindAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining_Exception() {

        // Arrange
        when(spatialUnitRepository.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                        1L, "null", new Long[2], new Long[2], "null", "fr", pageable
                )
        );

        assertEquals("Database error", exception.getMessage());

    }

    @Test
    void findAllChildOfSpatialUnit_Success() {

        when(spatialUnitRepository.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);

        // Act
        Page<SpatialUnitDTO> actualResult = spatialUnitService.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit1DTO, "null", new Long[2], new Long[2],"null", "fr", pageable);


        // Assert
        // Assert
        assertThat(actualResult)
                .hasSize(2);

    }

    @Test
    void findAllChildOfSpatialUnit_Exception() {

        // Arrange
        when(spatialUnitRepository.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> spatialUnitService.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                        spatialUnit1DTO, "null", new Long[2],new Long[2], "null", "fr", pageable)
        );

        assertEquals("Database error", exception.getMessage());

    }

    @Test
    void testFindById_Success() {
        // Arrange
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(1L);

        // Mock the repository
        when(spatialUnitRepository.findById(1L))
                .thenReturn(Optional.of(spatialUnit));

        // Mock the mapper
        when(spatialUnitMapper.convert(spatialUnit))
                .thenReturn(spatialUnit1DTO);

        // Act
        SpatialUnitDTO actualResult = spatialUnitService.findById(1L);

        // Assert
        assertEquals(spatialUnit1DTO, actualResult);
    }


    @Test
    void testFindById_SpatialUnitNotFoundException() {
        // Arrange
        long id = 1;
        when(spatialUnitRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        SpatialUnitNotFoundException exception = assertThrows(
                SpatialUnitNotFoundException.class,
                () -> spatialUnitService.findById(id)
        );

        assertEquals("SpatialUnit not found with ID: " + id, exception.getMessage());
    }

    @Test
    void testFindById_Exception() {
        // Arrange
        long id = 1;
        when(spatialUnitRepository.findById(id)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> spatialUnitService.findById(id)
        );

        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void findAllParentsOfSpatialUnit_Success() {

        // Arrange
        when(spatialUnitRepository.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);

        // Act
        Page<SpatialUnitDTO> actualResult = spatialUnitService.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit1DTO, "null", new Long[2], new Long[2], "null", "fr", pageable);

        // Assert
        // Assert
        assertThat(actualResult)
                .hasSize(2);

    }

    @Test
    void findAllParentsOfSpatialUnit_Exception() {
        // Arrange
        when(spatialUnitRepository.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> spatialUnitService.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                        spatialUnit1DTO, "null", new Long[2], new Long[2], "null", "fr", pageable)
        );

        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void findAllOfInstitution_Success() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);

        // Mock the repository
        when(spatialUnitRepository.findAllOfInstitution(institution.getId()))
                .thenReturn(List.of(spatialUnit1, spatialUnit2));

        // Mock the mapper
        when(spatialUnitMapper.convert(spatialUnit1)).thenReturn(spatialUnit1DTO);
        when(spatialUnitMapper.convert(spatialUnit2)).thenReturn(spatialUnit2DTO);

        // Act
        List<SpatialUnitDTO> actualResult = spatialUnitService.findAllOfInstitution(institution.getId());

        // Assert
        assertEquals(List.of(spatialUnit1DTO, spatialUnit2DTO), actualResult);
    }


    @Test
    void findAllOfInstitution_Exception() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);
        when(spatialUnitRepository.findAllOfInstitution(institution.getId())).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> spatialUnitService.findAllOfInstitution(institution.getId())
        );

        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void save_Success() throws SpatialUnitAlreadyExistsException {
        // Arrange
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        UserInfo userInfo = new UserInfo(institutionDTO, person, "fr");

        String name = "SpatialUnitName";
        ConceptDTO type = new ConceptDTO();
        SpatialUnitSummaryDTO parent = new SpatialUnitSummaryDTO(); parent.setId(0L);
        List<SpatialUnitSummaryDTO> parents = List.of(parent);
        SpatialUnitDTO unit = new SpatialUnitDTO();
        unit.setName(name);
        unit.setCategory(type);
        unit.setParents(new HashSet<>(parents));

        List<SpatialUnitSummaryDTO> children = List.of(new SpatialUnitSummaryDTO(spatialUnit2DTO));
        unit.setChildren(new HashSet<>(children));

        // Mock the repository and services
        when(institutionService.createOrGetSettingsOf(userInfo.getInstitution()))
                .thenReturn(new InstitutionSettings());
        when(spatialUnitRepository.findByNameAndInstitution(name, userInfo.getInstitution().getId()))
                .thenReturn(Optional.empty());
        when(conceptService.saveOrGetConcept(type))
                .thenReturn(new Concept());
        when(spatialUnitRepository.save(any(SpatialUnit.class)))
                .thenReturn(spatialUnit1);
        when(spatialUnitRepository.findById(2L))
                .thenReturn(Optional.of(new SpatialUnit()));
        when(spatialUnitRepository.findById(0L))
                .thenReturn(Optional.of(new SpatialUnit()));
        when(institutionService.findById(anyLong()))
                .thenReturn(institutionDTO);
        when(personService.findById(anyLong()))
                .thenReturn(new Person());


        // Mock the mappers
        when(spatialUnitMapper.convert(spatialUnit1))
                .thenReturn(spatialUnit1DTO);



        // Act
        SpatialUnitDTO result = spatialUnitService.save(userInfo, unit);

        // Assert
        assertNotNull(result);
        assertEquals(spatialUnit1DTO, result);
    }


    @Test
    void save_SpatialUnitAlreadyExistsException() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO(); institutionDTO.setId(3L);
        institutionDTO.setName("Test Institution"); // Set a name to avoid "null" in the message
        UserInfo userInfo = new UserInfo(institutionDTO, new PersonDTO(), "fr");

        String name = "SpatialUnitName";
        ConceptDTO type = new ConceptDTO();
        List<SpatialUnitSummaryDTO> parents = List.of(new SpatialUnitSummaryDTO(spatialUnit1DTO));
        SpatialUnitDTO unit = new SpatialUnitDTO();
        unit.setName(name);
        unit.setCategory(type);
        unit.setChildren(new HashSet<>());
        unit.setParents(new HashSet<>(parents));

        when(spatialUnitRepository.findByNameAndInstitution(anyString(), anyLong()))
                .thenReturn(Optional.of(spatialUnit1));

        // Expected exception message
        String expectedMessage = String.format(
                "Spatial Unit with name %s already exist in institution %s",
                name,
                institutionDTO.getName()
        );

        // Act & Assert
        SpatialUnitAlreadyExistsException exception = assertThrows(
                SpatialUnitAlreadyExistsException.class,
                () -> spatialUnitService.save(userInfo, unit)
        );

        assertEquals(expectedMessage, exception.getMessage());
    }


    @Test
    void findByArk() {
        // Arrange
        Ark ark = new Ark();
        SpatialUnit spatialUnit = new SpatialUnit();
        when(spatialUnitRepository.findByArk(ark)).thenReturn(Optional.of(spatialUnit));

        // Act
        Optional<SpatialUnit> result = spatialUnitService.findByArk(ark);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(spatialUnit, result.get());
        verify(spatialUnitRepository, times(1)).findByArk(ark);
    }

    @Test
    void findWithoutArk() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);
        SpatialUnit spatialUnit = new SpatialUnit();
        when(spatialUnitRepository.findAllByArkIsNullAndCreatedByInstitution(institution))
                .thenReturn(List.of(spatialUnit));

        // Act
        List<? extends ArkEntity> result = spatialUnitService.findWithoutArk(institution);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(spatialUnit, result.get(0));
        verify(spatialUnitRepository, times(1)).findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    @Test
    void save() {
        // Arrange
        SpatialUnitDTO spatialUnit = new SpatialUnitDTO();
        when(spatialUnitMapper.invertConvert(any(SpatialUnitDTO.class))).thenReturn(new SpatialUnit());
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spatialUnitMapper.convert(any(SpatialUnit.class))).thenReturn(new SpatialUnitDTO());
        // Act
        AbstractEntityDTO result = spatialUnitService.save(spatialUnit);

        // Assert
        assertNotNull(result);
        assertEquals(spatialUnit, result);
        verify(spatialUnitRepository, times(1)).save(any(SpatialUnit.class));
    }

    @Test
    void countByInstitution_success() {
        when(spatialUnitRepository.countByCreatedByInstitutionId(3L)).thenReturn(3L);
        assertEquals(3, spatialUnitService.countByInstitutionId(3L));
    }

    @Test
    void test_countChildrenByParent() {
        SpatialUnit su = new SpatialUnit();
        su.setId(1L);

        when(spatialUnitRepository.countChildrenByParentId(1L)).thenReturn(1L);

        long result = spatialUnitService.countChildrenByParent(su);

        assertEquals(1L, result);
    }

    @Test
    void test_countParentByChild() {
        SpatialUnitDTO su = new SpatialUnitDTO();
        su.setId(1L);

        when(spatialUnitRepository.countParentsByChildId(1L)).thenReturn(1L);

        long result = spatialUnitService.countParentsByChild(su);

        assertEquals(1L, result);
    }

    @Test
    void test_findRootsOf() {
        // Arrange
        SpatialUnit su1 = new SpatialUnit();
        su1.setId(1L);

        SpatialUnit su2 = new SpatialUnit();
        su2.setId(2L);

        SpatialUnit su3 = new SpatialUnit();
        su3.setId(3L);

        Institution institution = new Institution();
        institution.setId(1L);

        // Mock the repository
        when(spatialUnitRepository.findAllOfInstitution(institution.getId()))
                .thenReturn(List.of(su1, su2, su3));

        when(spatialUnitRepository.countParentsByChildId(su1.getId()))
                .thenReturn(0L);
        when(spatialUnitRepository.countParentsByChildId(su2.getId()))
                .thenReturn(1L);
        when(spatialUnitRepository.countParentsByChildId(su3.getId()))
                .thenReturn(1L);

        // Mock the mapper
        SpatialUnitDTO su1DTO = new SpatialUnitDTO();
        when(spatialUnitMapper.convert(su1)).thenReturn(su1DTO);

        // Act
        List<SpatialUnitDTO> roots = spatialUnitService.findRootsOf(institution.getId());

        // Assert
        assertThat(roots)
                .hasSize(1)
                .containsExactly(su1DTO);
    }


    @Test
    void test_findDirectChildrensOf() {
        SpatialUnit su1 = new SpatialUnit();
        su1.setId(1L);

        SpatialUnit su2 = new SpatialUnit();
        su2.setId(2L);

        SpatialUnit su3 = new SpatialUnit();
        su3.setId(3L);

        when(spatialUnitRepository.findChildrensOf(su1.getId())).thenReturn(Set.of(su2,su3));

        List<SpatialUnitDTO> result = spatialUnitService.findDirectChildrensOf(su1.getId());

        assertThat(result)
                .hasSize(2);
    }


    @Test
    void returnsTrue_whenUserIsInstitutionManager() {
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        InstitutionDTO i = new InstitutionDTO();
        i.setId(1L);
        UserInfo user = new UserInfo(i ,person, "fr");

        when(permissionService.isInstitutionManager(user)).thenReturn(true);


        assertTrue(spatialUnitService.hasCreatePermission(user));
    }

    @Test
    void returnsTrue_whenUserIsActionManager() {
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        InstitutionDTO i = new InstitutionDTO();
        i.setId(1L);
        UserInfo user = new UserInfo(i ,person, "fr");
        when(permissionService.isInstitutionManager(user)).thenReturn(false);
        when(permissionService.isActionManager(user)).thenReturn(true);

        assertTrue(spatialUnitService.hasCreatePermission(user));
    }

    @Test
    void returnsFalse_whenUserHasNoPermissions() {
        PersonDTO person = new PersonDTO();
        person.setId(1L);
        InstitutionDTO i = new InstitutionDTO();
        i.setId(1L);
        UserInfo user = new UserInfo(i ,person, "fr");
        when(permissionService.isInstitutionManager(user)).thenReturn(false);
        when(permissionService.isActionManager(user)).thenReturn(false);

        assertFalse(spatialUnitService.hasCreatePermission(user));
    }

    @Test
    void shouldReturnDirectParentsAsList() {
        // given
        Long id = 1L;
        SpatialUnit parent1 = new SpatialUnit();
        parent1.setId(1L);
        SpatialUnit parent2 = new SpatialUnit();
        Set<SpatialUnit> repoResult = Set.of(parent1, parent2);

        when(spatialUnitRepository.findParentsOf(id)).thenReturn(repoResult);

        // when
        spatialUnitService.findDirectParentsOf(id);

        // then
        verify(spatialUnitRepository).findParentsOf(id);
        verifyNoMoreInteractions(spatialUnitRepository);
    }

    @Test
    void shouldReturnEmptyListWhenNoParentsFound() {
        // given
        Long id = 2L;
        when(spatialUnitRepository.findParentsOf(id)).thenReturn(Set.of());

        // when
       spatialUnitService.findDirectParentsOf(id);

        // then
        verify(spatialUnitRepository).findParentsOf(id);
        verifyNoMoreInteractions(spatialUnitRepository);
    }

    @SuppressWarnings("unchecked")
    @Test
    void restore_shouldSaveRevisionFromHistory() {
        // GIVEN
        SpatialUnit spatialUnit = new SpatialUnit();
        RevisionWithInfo<SpatialUnit> history = mock(RevisionWithInfo.class);
        when(history.entity()).thenReturn(spatialUnit);

        // WHEN
        spatialUnitService.restore(history);

        // THEN
        ArgumentCaptor<SpatialUnit> captor = ArgumentCaptor.forClass(SpatialUnit.class);
        verify(spatialUnitRepository).save(captor.capture());

        // Vérifie que c’est bien le spatialUnit récupéré du history
        assert(captor.getValue() == spatialUnit);
    }
    

    @Test
    void whenNoActionUnit_thenReturnsEmpty() {
        // given
        RecordingUnitDTO unit = mock(RecordingUnitDTO.class);
        when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(new RecordingUnit());

        // when
        List<SpatialUnitSummaryDTO> result = spatialUnitService.getSpatialUnitOptionsFor(unit);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(spatialUnitRepository);
    }


    @Test
    void existsChildrenByParentAndInstitution_shouldReturnTrue_whenChildrenExist() {
        // Arrange
        Long parentId = 1L;
        Long institutionId = 10L;
        when(spatialUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId))
                .thenReturn(true);

        // Act
        boolean result = spatialUnitService.existsChildrenByParentAndInstitution(parentId, institutionId);

        // Assert
        assertTrue(result);
        verify(spatialUnitRepository, times(1))
                .existsChildrenByParentAndInstitution(parentId, institutionId);
    }

    @Test
    void existsRootChildrenByInstitution_ShouldReturnTrue_WhenChildrenExist() {
        // Arrange
        Long institutionId = 1L;
        when(spatialUnitRepository.existsRootChildrenByInstitution(institutionId))
                .thenReturn(true);

        // Act
        boolean result = spatialUnitService.existsRootChildrenByInstitution(institutionId);

        // Assert
        assertTrue(result, "La méthode doit retourner true si des enfants existent.");
    }

    @Test
    void existsRootChildrenByParent_ShouldReturnTrue_WhenChildrenExist() {
        // Arrange
        Long spatialUnitId = 1L;
        when(spatialUnitRepository.existsRootChildrenByParent(spatialUnitId))
                .thenReturn(true);

        // Act
        boolean result = spatialUnitService.existsRootChildrenByParent(spatialUnitId);

        // Assert
        assertTrue(result, "La méthode doit retourner true si des enfants existent.");
    }

    @Test
    void testFindNextByInstitution_ShouldReturnNextSpatialUnit() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        SpatialUnitDTO currentDTO = new SpatialUnitDTO();
        currentDTO.setCreationTime(OffsetDateTime.now());

        SpatialUnit nextEntity = new SpatialUnit();
        SpatialUnitDTO nextDTO = new SpatialUnitDTO();

        when(spatialUnitRepository.findFirstByCreatedByInstitutionIdAndCreationTimeAfterOrderByCreationTimeAsc(eq(1L), any()))
                .thenReturn(Optional.of(nextEntity));
        when(spatialUnitMapper.convert(nextEntity)).thenReturn(nextDTO);

        // Act
        SpatialUnitDTO result = spatialUnitService.findNextByInstitution(institutionDTO, currentDTO);

        // Assert
        assertNotNull(result);
        assertEquals(nextDTO, result);
        verify(spatialUnitRepository, never()).findFirstByCreatedByInstitutionIdOrderByCreationTimeAsc(anyLong());
    }

    @Test
    void testFindNextByInstitution_ShouldWrapAroundToOldest() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        SpatialUnitDTO currentDTO = new SpatialUnitDTO();

        SpatialUnit oldestEntity = new SpatialUnit();
        SpatialUnitDTO oldestDTO = new SpatialUnitDTO();

        // No "next" found
        when(spatialUnitRepository.findFirstByCreatedByInstitutionIdAndCreationTimeAfterOrderByCreationTimeAsc(anyLong(), any()))
                .thenReturn(Optional.empty());
        // Should trigger the wrap around call
        when(spatialUnitRepository.findFirstByCreatedByInstitutionIdOrderByCreationTimeAsc(1L))
                .thenReturn(Optional.of(oldestEntity));
        when(spatialUnitMapper.convert(oldestEntity)).thenReturn(oldestDTO);

        // Act
        SpatialUnitDTO result = spatialUnitService.findNextByInstitution(institutionDTO, currentDTO);

        // Assert
        assertEquals(oldestDTO, result);
    }

    @Test
    void testFindPreviousByInstitution_ShouldReturnPreviousSpatialUnit() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        SpatialUnitDTO currentDTO = new SpatialUnitDTO();
        currentDTO.setCreationTime(OffsetDateTime.now());

        SpatialUnit prevEntity = new SpatialUnit();
        SpatialUnitDTO prevDTO = new SpatialUnitDTO();

        when(spatialUnitRepository.findFirstByCreatedByInstitutionIdAndCreationTimeBeforeOrderByCreationTimeDesc(eq(1L), any()))
                .thenReturn(Optional.of(prevEntity));
        when(spatialUnitMapper.convert(prevEntity)).thenReturn(prevDTO);

        // Act
        SpatialUnitDTO result = spatialUnitService.findPreviousByInstitution(institutionDTO, currentDTO);

        // Assert
        assertNotNull(result);
        assertEquals(prevDTO, result);
    }

    @Test
    void testFindPreviousByInstitution_ShouldWrapAroundToMostRecent() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        SpatialUnitDTO currentDTO = new SpatialUnitDTO();

        SpatialUnit mostRecentEntity = new SpatialUnit();
        SpatialUnitDTO mostRecentDTO = new SpatialUnitDTO();

        // No "previous" found
        when(spatialUnitRepository.findFirstByCreatedByInstitutionIdAndCreationTimeBeforeOrderByCreationTimeDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
        // Should trigger the wrap around call
        when(spatialUnitRepository.findFirstByCreatedByInstitutionIdOrderByCreationTimeDesc(1L))
                .thenReturn(Optional.of(mostRecentEntity));
        when(spatialUnitMapper.convert(mostRecentEntity)).thenReturn(mostRecentDTO);

        // Act
        SpatialUnitDTO result = spatialUnitService.findPreviousByInstitution(institutionDTO, currentDTO);

        // Assert
        assertEquals(mostRecentDTO, result);
    }

    @Test
    void testToggleValidated_CycleLogic() {
        // Arrange
        Long id = 1L;
        SpatialUnit unit = new SpatialUnit();
        when(spatialUnitRepository.findById(id)).thenReturn(Optional.of(unit));
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenAnswer(i -> i.getArguments()[0]);
        when(spatialUnitMapper.convert(any(SpatialUnit.class))).thenReturn(new SpatialUnitDTO());

        // 1. Incomplete -> Complete
        unit.setValidated(fr.siamois.domain.models.ValidationStatus.INCOMPLETE);
        spatialUnitService.toggleValidated(id);
        assertEquals(fr.siamois.domain.models.ValidationStatus.COMPLETE, unit.getValidated());

        // 2. Complete -> Validated
        spatialUnitService.toggleValidated(id);
        assertEquals(fr.siamois.domain.models.ValidationStatus.VALIDATED, unit.getValidated());

        // 3. Validated -> Incomplete
        spatialUnitService.toggleValidated(id);
        assertEquals(fr.siamois.domain.models.ValidationStatus.INCOMPLETE, unit.getValidated());
    }

    @Test
    void testToggleValidated_NotFound_ThrowsException() {
        // Arrange
        when(spatialUnitRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException.class,
                () -> spatialUnitService.toggleValidated(99L));
    }

    @Test
    void findTop3BySimilarity_ShouldReturnEmptyList_WhenQueryIsBlank() {
        // Arrange
        Long instId = 1L;

        // Act
        List<PlaceSuggestionDTO> resultNull = spatialUnitService.findTop3ByInstitutionIdBySimilarity(instId, null);
        List<PlaceSuggestionDTO> resultEmpty = spatialUnitService.findTop3ByInstitutionIdBySimilarity(instId, "  ");

        // Assert
        assertTrue(resultNull.isEmpty());
        assertTrue(resultEmpty.isEmpty());
        verifyNoInteractions(spatialUnitRepository);
    }

    @Test
    void findTop3BySimilarity_ShouldReturnMappedDtos_WhenResultsExist() {
        // Arrange
        Long instId = 1L;
        String query = "Paris";

        SpatialUnit unit = new SpatialUnit();
        unit.setId(100L);
        unit.setName("Paris Office");
        unit.setCode("PAR-01");
        // Mock category if necessary
        unit.setCategory(new Concept());

        when(spatialUnitRepository.findTop3ByInstitutionIdBySimilarity(instId, query))
                .thenReturn(List.of(unit));
        when(conceptMapper.convert(any())).thenReturn(new ConceptDTO());

        // Act
        List<PlaceSuggestionDTO> results = spatialUnitService.findTop3ByInstitutionIdBySimilarity(instId, query);

        // Assert
        assertEquals(1, results.size());
        PlaceSuggestionDTO dto = results.get(0);
        assertEquals(100L, dto.getId());
        assertEquals("Paris Office", dto.getName());
        assertEquals("PAR-01", dto.getCode());
        assertEquals("SIAMOIS", dto.getSourceName());
        assertNotNull(dto.getCategory());
    }

    // ------------------------------------------------------------------
    // findTop3BySimilarity — null query branch
    // ------------------------------------------------------------------

    @Test
    void findTop3BySimilarity_nullQuery_returnsEmpty() {
        assertTrue(spatialUnitService.findTop3ByInstitutionIdBySimilarity(1L, null).isEmpty());
        verify(spatialUnitRepository, never()).findTop3ByInstitutionIdBySimilarity(any(), any());
    }

    // ------------------------------------------------------------------
    // Summary variants
    // ------------------------------------------------------------------

    @Test
    void findAllSummaryOfInstitution_mapsToSummaryDtos() {
        SpatialUnitSummaryDTO summary1 = new SpatialUnitSummaryDTO();
        SpatialUnitSummaryDTO summary2 = new SpatialUnitSummaryDTO();
        when(spatialUnitRepository.findAllOfInstitution(1L)).thenReturn(List.of(spatialUnit1, spatialUnit2));
        when(spatialUnitSummaryMapper.convert(spatialUnit1)).thenReturn(summary1);
        when(spatialUnitSummaryMapper.convert(spatialUnit2)).thenReturn(summary2);

        List<SpatialUnitSummaryDTO> result = spatialUnitService.findAllSummaryOfInstitution(1L);

        assertEquals(List.of(summary1, summary2), result);
    }

    @Test
    void findSummaryRootsOf_returnsOnlyOrphans() {
        SpatialUnitSummaryDTO summary = new SpatialUnitSummaryDTO();
        when(spatialUnitRepository.findAllOfInstitution(1L)).thenReturn(List.of(spatialUnit1, spatialUnit2));
        when(spatialUnitRepository.countParentsByChildId(1L)).thenReturn(0L);
        when(spatialUnitRepository.countParentsByChildId(2L)).thenReturn(2L);
        when(spatialUnitSummaryMapper.convert(spatialUnit1)).thenReturn(summary);

        List<SpatialUnitSummaryDTO> result = spatialUnitService.findSummaryRootsOf(1L);

        assertEquals(List.of(summary), result);
    }

    @Test
    void findDirectChildrensSummaryOf_mapsToSummaryDtos() {
        SpatialUnitSummaryDTO summary = new SpatialUnitSummaryDTO();
        when(spatialUnitRepository.findChildrensOf(7L)).thenReturn(Set.of(spatialUnit1));
        when(spatialUnitSummaryMapper.convert(spatialUnit1)).thenReturn(summary);

        List<SpatialUnitSummaryDTO> result = spatialUnitService.findDirectChildrensSummaryOf(7L);

        assertEquals(List.of(summary), result);
    }

    // ------------------------------------------------------------------
    // existsXxx — false branches
    // ------------------------------------------------------------------

    @Test
    void existsChildrenByParentAndInstitution_returnsFalseWhenRepositoryDoes() {
        when(spatialUnitRepository.existsChildrenByParentAndInstitution(1L, 2L)).thenReturn(false);
        assertFalse(spatialUnitService.existsChildrenByParentAndInstitution(1L, 2L));
    }

    @Test
    void existsRootChildrenByInstitution_returnsFalseWhenRepositoryDoes() {
        when(spatialUnitRepository.existsRootChildrenByInstitution(1L)).thenReturn(false);
        assertFalse(spatialUnitService.existsRootChildrenByInstitution(1L));
    }

    @Test
    void existsRootChildrenByParent_returnsFalseWhenRepositoryDoes() {
        when(spatialUnitRepository.existsRootChildrenByParent(1L)).thenReturn(false);
        assertFalse(spatialUnitService.existsRootChildrenByParent(1L));
    }

    // ------------------------------------------------------------------
    // toggleValidated — IllegalStateException branch (validated == null)
    // ------------------------------------------------------------------

    @Test
    void toggleValidated_unknownStatus_throwsNullPointer() {
        SpatialUnit unit = new SpatialUnit();
        unit.setId(1L);
        unit.setValidated(null);
        when(spatialUnitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThrows(NullPointerException.class, () -> spatialUnitService.toggleValidated(1L));
    }

    // ------------------------------------------------------------------
    // getSpatialUnitOptionsFor — three branches
    // ------------------------------------------------------------------

    @Test
    void getSpatialUnitOptionsFor_nullActionUnit_returnsEmpty() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        when(recordingUnitMapper.invertConvert(dto)).thenReturn(new RecordingUnit());

        List<SpatialUnitSummaryDTO> result = spatialUnitService.getSpatialUnitOptionsFor(dto);

        assertTrue(result.isEmpty());
    }

    @Test
    void getSpatialUnitOptionsFor_actionUnitNotInRepository_returnsEmpty() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        RecordingUnit ru = new RecordingUnit();
        fr.siamois.domain.models.actionunit.ActionUnit au = new fr.siamois.domain.models.actionunit.ActionUnit();
        au.setId(1L);
        ru.setActionUnit(au);
        when(recordingUnitMapper.invertConvert(dto)).thenReturn(ru);
        when(actionUnitRepository.findById(1L)).thenReturn(Optional.empty());

        List<SpatialUnitSummaryDTO> result = spatialUnitService.getSpatialUnitOptionsFor(dto);

        assertTrue(result.isEmpty());
    }

    @Test
    void getSpatialUnitOptionsFor_returnsRootsAndDescendants_dedupedByInsertionOrder() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        RecordingUnit ru = new RecordingUnit();
        fr.siamois.domain.models.actionunit.ActionUnit au = new fr.siamois.domain.models.actionunit.ActionUnit();
        au.setId(1L);
        SpatialUnit root = new SpatialUnit();
        root.setId(10L);
        au.setSpatialContext(Set.of(root));
        SpatialUnit descendant = new SpatialUnit();
        descendant.setId(20L);
        ru.setActionUnit(au);

        SpatialUnitSummaryDTO rootSummary = new SpatialUnitSummaryDTO();
        rootSummary.setId(10L);
        SpatialUnitSummaryDTO descSummary = new SpatialUnitSummaryDTO();
        descSummary.setId(20L);

        when(recordingUnitMapper.invertConvert(dto)).thenReturn(ru);
        when(actionUnitRepository.findById(1L)).thenReturn(Optional.of(au));
        when(spatialUnitRepository.findDescendantsUpToDepth(any(Long[].class), eq(10)))
                .thenReturn(List.of(descendant));
        when(spatialUnitSummaryMapper.convert(root)).thenReturn(rootSummary);
        when(spatialUnitSummaryMapper.convert(descendant)).thenReturn(descSummary);

        List<SpatialUnitSummaryDTO> result = spatialUnitService.getSpatialUnitOptionsFor(dto);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).getId());
        assertEquals(20L, result.get(1).getId());
    }

    @Test
    void getSpatialUnitOptionsFor_emptyRoots_skipsDescendantLookup() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        RecordingUnit ru = new RecordingUnit();
        fr.siamois.domain.models.actionunit.ActionUnit au = new fr.siamois.domain.models.actionunit.ActionUnit();
        au.setId(1L);
        au.setSpatialContext(Collections.emptySet());
        ru.setActionUnit(au);

        when(recordingUnitMapper.invertConvert(dto)).thenReturn(ru);
        when(actionUnitRepository.findById(1L)).thenReturn(Optional.of(au));

        List<SpatialUnitSummaryDTO> result = spatialUnitService.getSpatialUnitOptionsFor(dto);

        assertTrue(result.isEmpty());
        verify(spatialUnitRepository, never()).findDescendantsUpToDepth(any(Long[].class), anyInt());
    }

    // ------------------------------------------------------------------
    // searchSpatialUnits / countSearchResults / prepareSpecs branches
    // ------------------------------------------------------------------

    @Test
    void searchSpatialUnits_userMode_returnsMappedPage() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        when(spatialUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(p);
        when(spatialUnitMapper.convert(any(SpatialUnit.class))).thenReturn(spatialUnit1DTO);

        Page<SpatialUnitDTO> result = spatialUnitService.searchSpatialUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void searchSpatialUnits_rootOnlyWithoutFilters_runs() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        when(spatialUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(p);
        when(spatialUnitMapper.convert(any(SpatialUnit.class))).thenReturn(spatialUnit1DTO);

        Page<SpatialUnitDTO> result = spatialUnitService.searchSpatialUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void searchSpatialUnits_rootOnlyWithFiltersAndMatches_resolvesClosure() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(SpatialUnitSpec.NAME_FILTER, "x", FilterDTO.FilterType.CONTAINS);

        when(spatialUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(spatialUnit1, spatialUnit2));
        when(spatialUnitRepository.findAncestorClosure(any(Long[].class))).thenReturn(List.of(1L, 2L));
        when(spatialUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(p);
        when(spatialUnitMapper.convert(any(SpatialUnit.class))).thenReturn(spatialUnit1DTO);

        Page<SpatialUnitDTO> result = spatialUnitService.searchSpatialUnits(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(java.util.Set.of(1L, 2L), filters.getMatchIds());
    }

    @Test
    void searchSpatialUnits_rootOnlyWithFiltersNoMatches_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(SpatialUnitSpec.NAME_FILTER, "nope", FilterDTO.FilterType.CONTAINS);

        when(spatialUnitRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(spatialUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<SpatialUnitDTO> result = spatialUnitService.searchSpatialUnits(inst, filters, pageable);

        assertTrue(result.getContent().isEmpty());
        verify(spatialUnitRepository, never()).findAncestorClosure(any(Long[].class));
    }

    @Test
    void searchSpatialUnits_userModeWithCategoryFilter_runs() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        filters.add(SpatialUnitSpec.NAME_FILTER, "x", FilterDTO.FilterType.CONTAINS);
        filters.add(SpatialUnitSpec.CATEGORY_FILTER, List.of(5L, 6L), FilterDTO.FilterType.CONTAINS);

        when(spatialUnitRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<SpatialUnitDTO> result = spatialUnitService.searchSpatialUnits(inst, filters, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void countSearchResults_delegatesToRepository() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        when(spatialUnitRepository.count(any(Specification.class))).thenReturn(17L);

        assertEquals(17, spatialUnitService.countSearchResults(inst, filters));
    }

    // ------------------------------------------------------------------
    // findMatchingInInstitutionByName
    // ------------------------------------------------------------------

    @Test
    void findMatchingInInstitutionByName_returnsMappedDtos() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);

        when(spatialUnitRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(spatialUnit1)));
        when(spatialUnitMapper.convert(spatialUnit1)).thenReturn(spatialUnit1DTO);

        List<SpatialUnitDTO> result = spatialUnitService.findMatchingInInstitutionByName(inst, "q", 25);

        assertEquals(List.of(spatialUnit1DTO), result);
    }


}