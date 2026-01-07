package fr.siamois.ui.lazydatamodel.scope;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActionUnitScope {

    public enum Type {
        INSTITUTION,
        LINKED_TO_SPATIAL_UNIT
    }

    private final Type type;

    private final Long institutionId;

    // only used when type == LINKED_TO_SPATIAL_UNIT
    private final Long spatialUnitId;

    public static ActionUnitScope forInstitution(Long institutionId) {
        return ActionUnitScope.builder()
                .type(Type.INSTITUTION)
                .institutionId(institutionId)
                .build();
    }

    public static ActionUnitScope forSpatialUnit(Long institutionId, Long spatialUnitId) {
        return ActionUnitScope.builder()
                .type(Type.LINKED_TO_SPATIAL_UNIT)
                .institutionId(institutionId)
                .spatialUnitId(spatialUnitId)
                .build();
    }
}
