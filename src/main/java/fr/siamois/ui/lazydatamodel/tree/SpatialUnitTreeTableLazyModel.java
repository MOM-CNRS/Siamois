package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.lazydatamodel.ActionUnitScope;
import fr.siamois.ui.lazydatamodel.SpatialUnitScope;
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
    private SpatialUnitScope scope;

    public SpatialUnitTreeTableLazyModel(SpatialUnitService spatialUnitService, SpatialUnitScope scope) {
        super(SpatialUnit::getId);
        this.spatialUnitService = spatialUnitService;
        this.scope = scope;
    }

    @Override
    protected TreeNode<SpatialUnit> buildTree() {
        TreeNode<SpatialUnit> rootNode = new DefaultTreeNode<>(null, null);

        List<SpatialUnit> roots = switch (scope.getType()) {
            case INSTITUTION ->
                    spatialUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case PARENTS_OF_SPATIAL_UNIT ->
                    spatialUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case CHILDREN_OF_SPATIAL_UNIT ->
                    spatialUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
        };

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

        // service fetch children by parent id
        List<SpatialUnit> children = spatialUnitService.findChildrenByParentAndInstitution(parentUnit.getId(), scope.getInstitutionId());

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
