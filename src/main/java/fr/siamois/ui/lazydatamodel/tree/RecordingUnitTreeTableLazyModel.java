package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.lazydatamodel.scope.RecordingUnitScope;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.List;

@Getter
@Setter
public class RecordingUnitTreeTableLazyModel extends BaseTreeTableLazyModel<RecordingUnitDTO, Long> {

    private final transient RecordingUnitService recordingUnitService;
    private transient RecordingUnitScope scope;

    public RecordingUnitTreeTableLazyModel(RecordingUnitService recordingUnitService, RecordingUnitScope scope) {
        super(RecordingUnitDTO::getId);
        this.recordingUnitService = recordingUnitService;
        this.scope = scope;
    }

    @Override
    protected List<RecordingUnitDTO> fetchRoots() {
        return switch (scope.getType()) {
            case RU_IN_INSTITUTION ->
                    recordingUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case ACTION ->
                    recordingUnitService.findAllWithoutParentsByAction(scope.getActionId());
        };
    }


    @Override
    protected List<RecordingUnitDTO> fetchChildren(RecordingUnitDTO parentUnit) {
        if(parentUnit != null) {
            return recordingUnitService.findChildrenByParentAndInstitution(parentUnit.getId(), parentUnit.getCreatedByInstitution().getId());
        }
        return fetchRoots();
    }

    @Override
    protected Boolean isLeaf(RecordingUnitDTO node) {
        if(node != null) {
            return !recordingUnitService.existsChildrenByParentAndInstitution(node.getId(), node.getCreatedByInstitution().getId());
        }
        // If no parent, it depends on the scope
        return switch (scope.getType()) {
            case RU_IN_INSTITUTION ->
                    !recordingUnitService.existsRootChildrenByInstitution(scope.getInstitutionId());
            case ACTION ->
                    !recordingUnitService.existsRootChildrenByAction(scope.getActionId());
        };
    }
}
