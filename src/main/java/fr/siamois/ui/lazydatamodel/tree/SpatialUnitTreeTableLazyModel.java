package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.lazydatamodel.scope.SpatialUnitScope;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class SpatialUnitTreeTableLazyModel extends BaseTreeTableLazyModel<SpatialUnit, Long> {

    private final transient SpatialUnitService spatialUnitService;
    private transient SpatialUnitScope scope;

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
                    spatialUnitService.findRootsOf(scope.getInstitutionId());
            case CHILDREN_OF_SPATIAL_UNIT ->
                    spatialUnitService.findDirectChildrensOf(scope.getSpatialUnitId());
            case PARENTS_OF_SPATIAL_UNIT ->
                    spatialUnitService.findDirectParentsOf(scope.getSpatialUnitId());
        };

        Set<Long> path = new HashSet<>();
        for (SpatialUnit root : roots) {
            if (root == null || root.getId() == null) continue;

            TreeNode<SpatialUnit> node = new DefaultTreeNode<>(root, rootNode);
            registerNode(root, node); // IMPORTANT: index it

            path.clear();
            path.add(root.getId());
            buildChildren(node, root, path);
        }

        return rootNode;
    }

    private void buildChildren(TreeNode<SpatialUnit> parentNode,
                               SpatialUnit parentUnit,
                               Set<Long> path) {

        List<SpatialUnit> children = spatialUnitService.findDirectChildrensOf(parentUnit.getId());

        for (SpatialUnit child : children) {
            Long id = (child == null) ? null : child.getId();

            if (id == null || path.contains(id)) {
                continue;
            }

            Hibernate.initialize(child.getChildren());
            Hibernate.initialize(child.getParents());
            Hibernate.initialize(child.getRelatedActionUnitList());

            TreeNode<SpatialUnit> childNode = new DefaultTreeNode<>(child, parentNode);
            registerNode(child, childNode);

            path.add(id);
            buildChildren(childNode, child, path);
            path.remove(id);
        }

    }
}
