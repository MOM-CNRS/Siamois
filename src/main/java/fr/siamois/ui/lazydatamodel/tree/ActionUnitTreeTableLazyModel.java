package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.lazydatamodel.scope.ActionUnitScope;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class ActionUnitTreeTableLazyModel extends BaseTreeTableLazyModel<ActionUnit, Long> {

    private final transient ActionUnitService actionUnitService;
    private transient ActionUnitScope scope;

    public ActionUnitTreeTableLazyModel(ActionUnitService actionUnitService, ActionUnitScope scope) {
        super(ActionUnit::getId);
        this.actionUnitService = actionUnitService;
        this.scope = scope;
    }

    @Override
        protected TreeNode<ActionUnit> buildTree() {
        TreeNode<ActionUnit> rootNode = new DefaultTreeNode<>(null, null);

        // For now: load whole structure (service should ideally avoid N+1)
        List<ActionUnit> roots = switch (scope.getType()) {
            case INSTITUTION ->
                    actionUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case LINKED_TO_SPATIAL_UNIT ->
                    actionUnitService.findBySpatialContextAndInstitution(
                            scope.getSpatialUnitId(),
                            scope.getInstitutionId());
        };

        Set<Long> path = new HashSet<>();
        for (ActionUnit root : roots) {
            if (root == null || root.getId() == null) continue;
            TreeNode<ActionUnit> node = new DefaultTreeNode<>(root, rootNode);
            path.clear();
            path.add(root.getId());
            buildChildren(node, root, path);
        }

        return rootNode;
    }

    private void buildChildren(TreeNode<ActionUnit> parentNode,
                               ActionUnit parentUnit,
                               Set<Long> path) {

        // Option A: service fetch children by parent id
        List<ActionUnit> children = actionUnitService.findChildrenByParentAndInstitution(parentUnit.getId(),
                scope.getInstitutionId());

        for (ActionUnit child : children) {
            if (child == null || child.getId() == null) continue;

            // Cycle guard (just in case data is dirty)
            if (path.contains(child.getId())) {
                continue;
            }

            TreeNode<ActionUnit> childNode = new DefaultTreeNode<>(child, parentNode);

            path.add(child.getId());
            buildChildren(childNode, child, path);
            path.remove(child.getId());
        }
    }
}
