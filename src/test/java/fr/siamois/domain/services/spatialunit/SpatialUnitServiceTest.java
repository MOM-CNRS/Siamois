package fr.siamois.domain.services.spatialunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
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
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @InjectMocks
    private SpatialUnitService spatialUnitService;

    SpatialUnit spatialUnit1;

    SpatialUnit spatialUnit2;

    Page<SpatialUnit> p ;
    Pageable pageable;


    @BeforeEach
    void setUp() {
        spatialUnit1 = new SpatialUnit();
        spatialUnit2 = new SpatialUnit();
        spatialUnit1.setId(1L);
        spatialUnit2.setId(2L);
        p = new PageImpl<>(List.of(spatialUnit1, spatialUnit2));
        pageable = PageRequest.of(0, 10);


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
        Page<SpatialUnit> actualResult = spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], new Long[2],"null", "fr", pageable
        );

        // Assert
        assertEquals(spatialUnit1, actualResult.getContent().get(0));
        assertEquals(spatialUnit2, actualResult.getContent().get(1));
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
        Page<SpatialUnit> actualResult = spatialUnitService.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit1, "null", new Long[2], new Long[2],"null", "fr", pageable);


        // Assert
        // Assert
        assertEquals(spatialUnit1, actualResult.getContent().get(0));
        assertEquals(spatialUnit2, actualResult.getContent().get(1));

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
                        spatialUnit1, "null", new Long[2],new Long[2], "null", "fr", pageable)
        );

        assertEquals("Database error", exception.getMessage());

    }

    @Test
    void testFindById_Success() {

        // Arrange
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(1L);
        when(spatialUnitRepository.findById(1L)).thenReturn(Optional.of(spatialUnit));

        // Act
        SpatialUnit actualResult = spatialUnitService.findById(1);

        // Assert
        assertEquals(spatialUnit, actualResult);
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
        Page<SpatialUnit> actualResult = spatialUnitService.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                spatialUnit1, "null", new Long[2], new Long[2], "null", "fr", pageable);

        // Assert
        assertEquals(spatialUnit1, actualResult.getContent().get(0));
        assertEquals(spatialUnit2, actualResult.getContent().get(1));

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
                        spatialUnit1, "null", new Long[2], new Long[2], "null", "fr", pageable)
        );

        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void findAllOfInstitution_Success() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);
        when(spatialUnitRepository.findAllOfInstitution(institution.getId())).thenReturn(List.of(spatialUnit1, spatialUnit2));

        // Act
        List<SpatialUnit> actualResult = spatialUnitService.findAllOfInstitution(institution.getId());

        // Assert
        assertEquals(List.of(spatialUnit1, spatialUnit2), actualResult);
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
        Person person = new Person();
        person.setId(1L);
        Institution i = new Institution();
        i.setId(1L);
        UserInfo userInfo = new UserInfo(i ,person, "fr");
        String name = "SpatialUnitName";
        Concept type = new Concept();
        List<SpatialUnit> parents = List.of(spatialUnit1);
        SpatialUnit unit = new SpatialUnit();
        unit.setName(name);
        unit.setCategory(type);
        unit.setParents(new HashSet<>(parents));

        List<SpatialUnit> children = List.of(spatialUnit2);
        unit.setChildren(new HashSet<>(children));




        when(institutionService.createOrGetSettingsOf(userInfo.getInstitution())).thenReturn(new InstitutionSettings());
        when(spatialUnitRepository.findByNameAndInstitution(name, userInfo.getInstitution().getId())).thenReturn(Optional.empty());
        when(conceptService.saveOrGetConcept(type)).thenReturn(type);
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenReturn(spatialUnit1);
        when(institutionService.findById(anyLong())).thenReturn(i);
        when(personService.findById(anyLong())).thenReturn(person);
        when(spatialUnitRepository.findById(anyLong())).thenReturn(Optional.of(spatialUnit1));

        // Act
        SpatialUnit result = spatialUnitService.save(userInfo, unit);

        // Assert
        assertNotNull(result);
        assertEquals(spatialUnit1, result);

    }

    @Test
    void save_SpatialUnitAlreadyExistsException() {
        // Arrange
        UserInfo userInfo = new UserInfo(new Institution(), new Person(), "fr");

        String name = "SpatialUnitName";
        Concept type = new Concept();
        List<SpatialUnit> parents = List.of(spatialUnit1);
        SpatialUnit unit = new SpatialUnit();
        unit.setName(name);
        unit.setCategory(type);
        unit.setParents(new HashSet<>(parents));

        when(spatialUnitRepository.findByNameAndInstitution(name, userInfo.getInstitution().getId())).thenReturn(Optional.of(spatialUnit1));

        // Act & Assert
        SpatialUnitAlreadyExistsException exception = assertThrows(
                SpatialUnitAlreadyExistsException.class,
                () -> spatialUnitService.save(userInfo, unit)
        );

        assertEquals("Spatial Unit with name SpatialUnitName already exist in institution null", exception.getMessage());
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
        SpatialUnit spatialUnit = new SpatialUnit();
        when(spatialUnitRepository.save(spatialUnit)).thenReturn(spatialUnit);

        // Act
        ArkEntity result = spatialUnitService.save(spatialUnit);

        // Assert
        assertNotNull(result);
        assertEquals(spatialUnit, result);
        verify(spatialUnitRepository, times(1)).save(spatialUnit);
    }

    @Test
    void countByInstitution_success() {
        when(spatialUnitRepository.countByCreatedByInstitution(any(Institution.class))).thenReturn(3L);
        assertEquals(3, spatialUnitService.countByInstitution(new Institution()));
    }

    @Test
    void testFindAll_Success() {
        // Arrange
        spatialUnit1 = new SpatialUnit();
        spatialUnit2 = new SpatialUnit();
        when(spatialUnitRepository.findAll()).thenReturn(List.of(spatialUnit1, spatialUnit2));

        // Act
        List<SpatialUnit> actualResult = spatialUnitService.findAll();

        // Assert
        assertNotNull(actualResult);
        assertEquals(2, actualResult.size());
        assertTrue(actualResult.contains(spatialUnit1));
        assertTrue(actualResult.contains(spatialUnit2));
        verify(spatialUnitRepository, times(1)).findAll();
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
        SpatialUnit su = new SpatialUnit();
        su.setId(1L);

        when(spatialUnitRepository.countParentsByChildId(1L)).thenReturn(1L);

        long result = spatialUnitService.countParentsByChild(su);

        assertEquals(1L, result);
    }

    @Test
    void test_findRootsOf() {
        SpatialUnit su1 = new SpatialUnit();
        su1.setId(1L);

        SpatialUnit su2 = new SpatialUnit();
        su2.setId(2L);

        SpatialUnit su3 = new SpatialUnit();
        su3.setId(3L);

        Institution institution = new Institution();
        institution.setId(1L);

        when(spatialUnitRepository.findAllOfInstitution(institution.getId())).thenReturn(List.of(su1,su2,su3));
        when(spatialUnitRepository.countParentsByChildId(su1.getId())).thenReturn(0L);
        when(spatialUnitRepository.countParentsByChildId(su2.getId())).thenReturn(1L);
        when(spatialUnitRepository.countParentsByChildId(su3.getId())).thenReturn(1L);

        List<SpatialUnit> roots = spatialUnitService.findRootsOf(institution.getId());

        assertThat(roots)
                .hasSize(1)
                .containsExactlyInAnyOrder(su1);
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

        List<SpatialUnit> result = spatialUnitService.findDirectChildrensOf(su1.getId());

        assertThat(result)
                .hasSize(2)
                .containsExactlyInAnyOrder(su2,su3);
    }


    @Test
    void returnsTrue_whenUserIsInstitutionManager() {
        Person person = new Person();
        person.setId(1L);
        Institution i = new Institution();
        i.setId(1L);
        UserInfo user = new UserInfo(i ,person, "fr");

        when(permissionService.isInstitutionManager(user)).thenReturn(true);


        assertTrue(spatialUnitService.hasCreatePermission(user));
    }

    @Test
    void returnsTrue_whenUserIsActionManager() {
        Person person = new Person();
        person.setId(1L);
        Institution i = new Institution();
        i.setId(1L);
        UserInfo user = new UserInfo(i ,person, "fr");
        when(permissionService.isInstitutionManager(user)).thenReturn(false);
        when(permissionService.isActionManager(user)).thenReturn(true);

        assertTrue(spatialUnitService.hasCreatePermission(user));
    }

    @Test
    void returnsFalse_whenUserHasNoPermissions() {
        Person person = new Person();
        person.setId(1L);
        Institution i = new Institution();
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
        List<SpatialUnit> result = spatialUnitService.findDirectParentsOf(id);

        // then
        assertThat(result).containsExactlyInAnyOrder(parent1, parent2);
        verify(spatialUnitRepository).findParentsOf(id);
        verifyNoMoreInteractions(spatialUnitRepository);
    }

    @Test
    void shouldReturnEmptyListWhenNoParentsFound() {
        // given
        Long id = 2L;
        when(spatialUnitRepository.findParentsOf(id)).thenReturn(Set.of());

        // when
        List<SpatialUnit> result = spatialUnitService.findDirectParentsOf(id);

        // then
        assertThat(result).isEmpty();
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

    private static SpatialUnit su(long id) {
        SpatialUnit su = mock(SpatialUnit.class);
        when(su.getId()).thenReturn(id);
        return su;
    }

    @Test
    void whenNoActionUnit_thenReturnsEmpty() {
        // given
        RecordingUnit unit = mock(RecordingUnit.class);
        when(unit.getActionUnit()).thenReturn(null);

        // when
        List<SpatialUnit> result = spatialUnitService.getSpatialUnitOptionsFor(unit);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(spatialUnitRepository);
    }

    @Test
    void whenActionUnitExists_thenReturnsRootsAndDescendants() {
        // given
        SpatialUnit root1 = su(1L);
        SpatialUnit root2 = su(2L);
        SpatialUnit desc1 = su(3L);
        SpatialUnit desc2 = su(4L);

        ActionUnit actionUnit = mock(ActionUnit.class);
        when(actionUnit.getSpatialContext()).thenReturn(new HashSet<>(Set.of(root1, root2)));

        RecordingUnit unit = mock(RecordingUnit.class);
        when(unit.getActionUnit()).thenReturn(actionUnit);

        when(spatialUnitRepository.findDescendantsUpToDepth(any(), anyInt()))
                .thenReturn(List.of(desc1, desc2));

        // when
        List<SpatialUnit> result = spatialUnitService.getSpatialUnitOptionsFor(unit);

        // then
        assertThat(result)
                .containsExactlyInAnyOrder(root1, root2, desc1, desc2); // order irrelevant

        verify(spatialUnitRepository)
                .findDescendantsUpToDepth(any(Long[].class), eq(10));
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


}