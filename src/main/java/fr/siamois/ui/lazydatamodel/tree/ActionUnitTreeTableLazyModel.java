package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.lazydatamodel.scope.ActionUnitScope;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ActionUnitTreeTableLazyModel extends BaseTreeTableLazyModel<ActionUnitDTO, Long> {

    private final transient ActionUnitService actionUnitService;
    private transient ActionUnitScope scope;

    public ActionUnitTreeTableLazyModel(ActionUnitService actionUnitService, ActionUnitScope scope) {
        super(ActionUnitDTO::getId);
        this.actionUnitService = actionUnitService;
        this.scope = scope;
    }


    @Override
    protected List<ActionUnitDTO> fetchRoots() {
        return switch (scope.getType()) {
            case INSTITUTION -> actionUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case LINKED_TO_SPATIAL_UNIT -> actionUnitService.findBySpatialContext(
                    scope.getSpatialUnitId());
        };
    }

    @Override
    protected List<ActionUnitDTO> fetchChildren(ActionUnitDTO parentUnit) {
        if (parentUnit != null) {
            return actionUnitService.findChildrenByParentAndInstitution(parentUnit.getId(),
                    scope.getInstitutionId());
        }
        return fetchRoots();
    }

    @Override
    protected Boolean isLeaf(ActionUnitDTO node) {
        if (node != null) {
            return !actionUnitService.existsChildrenByParentAndInstitution(node.getId(), node.getCreatedByInstitution().getId());
        }
        return switch (scope.getType()) {
            case INSTITUTION -> !actionUnitService.existsRootChildrenByInstitution(scope.getInstitutionId());
            case LINKED_TO_SPATIAL_UNIT -> !actionUnitService.existsRootChildrenByRelatedSpatialUnit(scope.getSpatialUnitId());
        };

    }
}
