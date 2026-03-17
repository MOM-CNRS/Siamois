package fr.siamois.domain.services.spatialunit;


import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
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
        SpatialUnitSummaryDTO root = new SpatialUnitSummaryDTO();
        root.setId(1L);
        root.setName("Root");

        SpatialUnitSummaryDTO child1 = new SpatialUnitSummaryDTO();
        child1.setId(2L);
        child1.setName("Child 1");

        SpatialUnitSummaryDTO child2 = new SpatialUnitSummaryDTO();
        child2.setId(3L);
        child2.setName("Child 2");

        Map<SpatialUnitSummaryDTO, List<SpatialUnitSummaryDTO>> mockMap = new HashMap<>();
        mockMap.put(root, List.of(child1, child2));

        // Institution mock
        InstitutionDTO mockInstitution = new InstitutionDTO();
        mockInstitution.setId(1L);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
            when(spatialUnitService.findSummaryRootsOf(any(Long.class))).thenReturn(List.of(root));
        when(spatialUnitService.findDirectChildrensSummaryOf(root.getId())).thenReturn(List.of(child1, child2));

        // Act
        TreeNode<SpatialUnitSummaryDTO> tree = spatialUnitTreeService.buildTree();

        // Assert
        assertNotNull(tree);
        assertEquals(1, tree.getChildren().size()); // one root spatial unit
        TreeNode<SpatialUnitSummaryDTO> rootNode = tree.getChildren().get(0);
        assertEquals("Root", rootNode.getData().getName());
        assertEquals(2, rootNode.getChildren().size());

        Set<String> childNames = new HashSet<>();
        for (TreeNode<SpatialUnitSummaryDTO> child : rootNode.getChildren()) {
            childNames.add(child.getData().getName());
        }
        assertTrue(childNames.contains("Child 1"));
        assertTrue(childNames.contains("Child 2"));
    }

    @Test
    void testBuildTree_MultipleRoots() {
        // Setup test data
        SpatialUnitSummaryDTO root1 = new SpatialUnitSummaryDTO();
        root1.setId(1L);
        root1.setName("Root1");

        SpatialUnitSummaryDTO root2 = new SpatialUnitSummaryDTO();
        root2.setId(2L);
        root2.setName("Root2");

        SpatialUnitSummaryDTO child = new SpatialUnitSummaryDTO();
        child.setId(3L);
        child.setName("Child");

        Map<SpatialUnitSummaryDTO, List<SpatialUnitSummaryDTO>> mockMap = new HashMap<>();
        mockMap.put(root1, List.of(child));
        mockMap.put(root2, List.of());

        // Institution mock
        InstitutionDTO mockInstitution = new InstitutionDTO();
        mockInstitution.setId(1L);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
        when(spatialUnitService.findSummaryRootsOf(any(Long.class))).thenReturn(List.of(root1, root2));


        // Act
        TreeNode<SpatialUnitSummaryDTO> tree = spatialUnitTreeService.buildTree();

        // Assert
        assertNotNull(tree);
        assertEquals(2, tree.getChildren().size());

        Set<String> rootNames = new HashSet<>();
        for (TreeNode<SpatialUnitSummaryDTO> node : tree.getChildren()) {
            rootNames.add(node.getData().getName());
        }

        assertTrue(rootNames.contains("Root1"));
        assertTrue(rootNames.contains("Root2"));
    }

    @Test
    void testBuildTree_EmptyMap() {
        InstitutionDTO mockInstitution = new InstitutionDTO();
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);

        TreeNode<SpatialUnitSummaryDTO> tree = spatialUnitTreeService.buildTree();

        assertNotNull(tree);
        assertEquals(0, tree.getChildren().size());
    }


}