package fr.siamois.domain.services.spatialunit;


import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
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
        SpatialUnit root = new SpatialUnit();
        root.setId(1L);
        root.setName("Root");

        SpatialUnit child1 = new SpatialUnit();
        child1.setId(2L);
        child1.setName("Child 1");

        SpatialUnit child2 = new SpatialUnit();
        child2.setId(3L);
        child2.setName("Child 2");

        Map<SpatialUnit, List<SpatialUnit>> mockMap = new HashMap<>();
        mockMap.put(root, List.of(child1, child2));

        // Institution mock
        Institution mockInstitution = new Institution();
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
        when(spatialUnitService.findRootsOf(any(Institution.class))).thenReturn(List.of(root));
        when(spatialUnitService.findDirectChildrensOf(root)).thenReturn(List.of(child1, child2));

        // Act
        TreeNode<SpatialUnit> tree = spatialUnitTreeService.buildTree();

        // Assert
        assertNotNull(tree);
        assertEquals(1, tree.getChildren().size()); // one root spatial unit
        TreeNode<SpatialUnit> rootNode = tree.getChildren().get(0);
        assertEquals("Root", rootNode.getData().getName());
        assertEquals(2, rootNode.getChildren().size());

        Set<String> childNames = new HashSet<>();
        for (TreeNode<SpatialUnit> child : rootNode.getChildren()) {
            childNames.add(child.getData().getName());
        }
        assertTrue(childNames.contains("Child 1"));
        assertTrue(childNames.contains("Child 2"));
    }

    @Test
    void testBuildTree_MultipleRoots() {
        // Setup test data
        SpatialUnit root1 = new SpatialUnit();
        root1.setId(1L);
        root1.setName("Root1");

        SpatialUnit root2 = new SpatialUnit();
        root2.setId(2L);
        root2.setName("Root2");

        SpatialUnit child = new SpatialUnit();
        child.setId(3L);
        child.setName("Child");

        Map<SpatialUnit, List<SpatialUnit>> mockMap = new HashMap<>();
        mockMap.put(root1, List.of(child));
        mockMap.put(root2, List.of());

        // Institution mock
        Institution mockInstitution = new Institution();
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
        when(spatialUnitService.findRootsOf(any(Institution.class))).thenReturn(List.of(root1, root2));


        // Act
        TreeNode<SpatialUnit> tree = spatialUnitTreeService.buildTree();

        // Assert
        assertNotNull(tree);
        assertEquals(2, tree.getChildren().size());

        Set<String> rootNames = new HashSet<>();
        for (TreeNode<SpatialUnit> node : tree.getChildren()) {
            rootNames.add(node.getData().getName());
        }

        assertTrue(rootNames.contains("Root1"));
        assertTrue(rootNames.contains("Root2"));
    }

    @Test
    void testBuildTree_EmptyMap() {
        Institution mockInstitution = new Institution();
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);

        TreeNode<SpatialUnit> tree = spatialUnitTreeService.buildTree();

        assertNotNull(tree);
        assertEquals(0, tree.getChildren().size());
    }


}