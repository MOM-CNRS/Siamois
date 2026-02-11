package fr.siamois.ui.lazydatamodel.tree;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.lazydatamodel.scope.SpatialUnitScope;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.List;

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
    protected List<SpatialUnit> fetchRoots() {
        return switch (scope.getType()) {
            case INSTITUTION -> spatialUnitService.findRootsOf(scope.getInstitutionId());
            case CHILDREN_OF_SPATIAL_UNIT -> spatialUnitService.findDirectChildrensOf(scope.getSpatialUnitId());
        };
    }

    @Override
    protected List<SpatialUnit> fetchChildren(SpatialUnit parentUnit) {
        if(parentUnit != null) {
            return spatialUnitService.findDirectChildrensOf(parentUnit.getId());
        }
         return fetchRoots();
    }

    @Override
    protected void initializeAssociations(SpatialUnit child) {
        Hibernate.initialize(child.getChildren());
        Hibernate.initialize(child.getParents());
        Hibernate.initialize(child.getRelatedActionUnitList());
    }

    @Override
    protected Boolean isLeaf(SpatialUnit node) {
        if(node!=null){
            return !spatialUnitService.existsChildrenByParentAndInstitution(node.getId(), node.getCreatedByInstitution().getId());
        }
        // If no parent, it depends on the scope
        return switch (scope.getType()) {
            case INSTITUTION ->
                    !spatialUnitService.existsRootChildrenByInstitution(scope.getInstitutionId());
            case CHILDREN_OF_SPATIAL_UNIT ->
                    !spatialUnitService.existsRootChildrenByParent(scope.getSpatialUnitId());
        };
    }



}
