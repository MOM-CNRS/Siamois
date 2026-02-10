package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.lazydatamodel.scope.ActionUnitScope;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
    protected List<ActionUnit> fetchRoots() {
        return switch (scope.getType()) {
            case INSTITUTION ->
                    actionUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case LINKED_TO_SPATIAL_UNIT ->
                    actionUnitService.findBySpatialContextAndInstitution(
                            scope.getSpatialUnitId(),
                            scope.getInstitutionId());
        };
    }

    @Override
    protected List<ActionUnit> fetchChildren(ActionUnit parentUnit) {
        return actionUnitService.findChildrenByParentAndInstitution(parentUnit.getId(),
                scope.getInstitutionId());
    }

    @Override
    protected Boolean isLeaf(ActionUnit node) {
        return !actionUnitService.existsChildrenByParentAndInstitution(node.getId(), node.getCreatedByInstitution().getId());
    }

}
