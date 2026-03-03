package fr.siamois.domain.services.spatialunit;


import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.TreeNode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialUnitTreeServiceTest {

    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @InjectMocks
    private SpatialUnitTreeService spatialUnitTreeService;


    @Test
    void testBuildTree_SingleRootWithChildren() {
        // Setup test data
        SpatialUnitDTO root = new SpatialUnitDTO();
        root.setId(1L);
        root.setName("Root");

        SpatialUnitDTO child1 = new SpatialUnitDTO();
        child1.setId(2L);
        child1.setName("Child 1");

        SpatialUnitDTO child2 = new SpatialUnitDTO();
        child2.setId(3L);
        child2.setName("Child 2");

        Map<SpatialUnitDTO, List<SpatialUnitDTO>> mockMap = new HashMap<>();
        mockMap.put(root, List.of(child1, child2));

        // Institution mock
        InstitutionDTO mockInstitution = new InstitutionDTO();
        mockInstitution.setId(1L);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
            when(spatialUnitService.findRootsOf(any(Long.class))).thenReturn(List.of(root));
        when(spatialUnitService.findDirectChildrensOf(root.getId())).thenReturn(List.of(child1, child2));

        // Act
        TreeNode<SpatialUnitDTO> tree = spatialUnitTreeService.buildTree();

        // Assert
        assertNotNull(tree);
        assertEquals(1, tree.getChildren().size()); // one root spatial unit
        TreeNode<SpatialUnitDTO> rootNode = tree.getChildren().get(0);
        assertEquals("Root", rootNode.getData().getName());
        assertEquals(2, rootNode.getChildren().size());

        Set<String> childNames = new HashSet<>();
        for (TreeNode<SpatialUnitDTO> child : rootNode.getChildren()) {
            childNames.add(child.getData().getName());
        }
        assertTrue(childNames.contains("Child 1"));
        assertTrue(childNames.contains("Child 2"));
    }

    @Test
    void testBuildTree_MultipleRoots() {
        // Setup test data
        SpatialUnitDTO root1 = new SpatialUnitDTO();
        root1.setId(1L);
        root1.setName("Root1");

        SpatialUnitDTO root2 = new SpatialUnitDTO();
        root2.setId(2L);
        root2.setName("Root2");

        SpatialUnitDTO child = new SpatialUnitDTO();
        child.setId(3L);
        child.setName("Child");

        Map<SpatialUnitDTO, List<SpatialUnitDTO>> mockMap = new HashMap<>();
        mockMap.put(root1, List.of(child));
        mockMap.put(root2, List.of());

        // Institution mock
        InstitutionDTO mockInstitution = new InstitutionDTO();
        mockInstitution.setId(1L);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
        when(spatialUnitService.findRootsOf(any(Long.class))).thenReturn(List.of(root1, root2));


        // Act
        TreeNode<SpatialUnitDTO> tree = spatialUnitTreeService.buildTree();

        // Assert
        assertNotNull(tree);
        assertEquals(2, tree.getChildren().size());

        Set<String> rootNames = new HashSet<>();
        for (TreeNode<SpatialUnitDTO> node : tree.getChildren()) {
            rootNames.add(node.getData().getName());
        }

        assertTrue(rootNames.contains("Root1"));
        assertTrue(rootNames.contains("Root2"));
    }

    @Test
    void testBuildTree_EmptyMap() {
        InstitutionDTO mockInstitution = new InstitutionDTO();
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);

        TreeNode<SpatialUnitDTO> tree = spatialUnitTreeService.buildTree();

        assertNotNull(tree);
        assertEquals(0, tree.getChildren().size());
    }


}