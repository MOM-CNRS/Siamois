package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.lazydatamodel.RecordingUnitScope;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class RecordingUnitTreeTableLazyModel extends BaseTreeTableLazyModel<RecordingUnit, Long> {

    private final transient RecordingUnitService recordingUnitService;
    private transient RecordingUnitScope scope;

    public RecordingUnitTreeTableLazyModel(RecordingUnitService recordingUnitService, RecordingUnitScope scope) {
        super(RecordingUnit::getId);
        this.recordingUnitService = recordingUnitService;
        this.scope = scope;
    }

    @Override
    protected TreeNode<RecordingUnit> buildTree() {
        TreeNode<RecordingUnit> rootNode = new DefaultTreeNode<>(null, null);

        // For now: load whole structure (service should ideally avoid N+1)
        // get roots then fetch children per node (easy but can be N+1)
        List<RecordingUnit> roots =  switch (scope.getType()) {
            case RU_IN_INSTITUTION ->
                    recordingUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case ACTION ->
                    recordingUnitService.findAllWithoutParentsByAction(scope.getActionId());
        };

        Set<Long> path = new HashSet<>();
        for (RecordingUnit root : roots) {
            if (root == null || root.getId() == null) continue;
            TreeNode<RecordingUnit> node = new DefaultTreeNode<>(root, rootNode);
            path.clear();
            path.add(root.getId());
            buildChildren(node, root, path);
        }

        return rootNode;
    }

    private void buildChildren(TreeNode<RecordingUnit> parentNode,
                               RecordingUnit parentUnit,
                               Set<Long> path) {

        // Option A: service fetch children by parent id
        List<RecordingUnit> children = recordingUnitService.findChildrenByParentAndInstitution(parentUnit.getId(), scope.getInstitutionId());

        for (RecordingUnit child : children) {
            if (child == null || child.getId() == null) continue;

            // Cycle guard (just in case data is dirty)
            if (path.contains(child.getId())) {
                continue;
            }

            TreeNode<RecordingUnit> childNode = new DefaultTreeNode<>(child, parentNode);

            path.add(child.getId());
            buildChildren(childNode, child, path);
            path.remove(child.getId());
        }
    }
}
