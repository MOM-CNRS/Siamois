package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.lazydatamodel.scope.RecordingUnitScope;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.List;

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
    protected List<RecordingUnit> fetchRoots() {
        return switch (scope.getType()) {
            case RU_IN_INSTITUTION ->
                    recordingUnitService.findAllWithoutParentsByInstitution(scope.getInstitutionId());
            case ACTION ->
                    recordingUnitService.findAllWithoutParentsByAction(scope.getActionId());
        };
    }

    @Override
    protected List<RecordingUnit> fetchChildren(RecordingUnit parentUnit) {
        return recordingUnitService.findChildrenByParentAndInstitution(parentUnit.getId(), parentUnit.getCreatedByInstitution().getId());
    }

    @Override
    protected void initializeAssociations(RecordingUnit child) {
        Hibernate.initialize(child.getChildren());
        Hibernate.initialize(child.getParents());
        Hibernate.initialize(child.getSpecimenList());
        Hibernate.initialize(child.getDocuments());
        Hibernate.initialize(child.getRelationshipsAsUnit2());
        Hibernate.initialize(child.getRelationshipsAsUnit1());
    }

    @Override
    protected Boolean isLeaf(RecordingUnit node) {
        return !recordingUnitService.existsChildrenByParentAndInstitution(node.getId(), node.getCreatedByInstitution().getId());
    }
}
