package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class SpatialUnitTreeTableLazyModel extends BaseTreeTableLazyModel<SpatialUnit, Long> {

    private final SpatialUnitService spatialUnitService;
    private final Long institutionId;

    public SpatialUnitTreeTableLazyModel(SpatialUnitService spatialUnitService, Long institutionId) {
        super(SpatialUnit::getId);
        this.spatialUnitService = spatialUnitService;
        this.institutionId = institutionId;
    }

    @Override
    protected TreeNode<SpatialUnit> buildTree() {
        TreeNode<SpatialUnit> rootNode = new DefaultTreeNode<>(null, null);

        // For now: load whole structure (service should ideally avoid N+1)
        // Option A: get roots then fetch children per node (easy but can be N+1)
        List<SpatialUnit> roots = spatialUnitService.findAllWithoutParentsByInstitution(institutionId);

        Set<Long> path = new HashSet<>();
        for (SpatialUnit root : roots) {
            if (root == null || root.getId() == null) continue;
            TreeNode<SpatialUnit> node = new DefaultTreeNode<>(root, rootNode);
            path.clear();
            path.add(root.getId());
            buildChildren(node, root, path);
        }

        return rootNode;
    }

    private void buildChildren(TreeNode<SpatialUnit> parentNode,
                               SpatialUnit parentUnit,
                               Set<Long> path) {

        // Option A: service fetch children by parent id
        List<SpatialUnit> children = spatialUnitService.findChildrenByParentAndInstitution(parentUnit.getId(), institutionId);

        for (SpatialUnit child : children) {
            if (child == null || child.getId() == null) continue;

            // Cycle guard (just in case data is dirty)
            if (path.contains(child.getId())) {
                continue;
            }

            TreeNode<SpatialUnit> childNode = new DefaultTreeNode<>(child, parentNode);

            path.add(child.getId());
            buildChildren(childNode, child, path);
            path.remove(child.getId());
        }
    }
}
