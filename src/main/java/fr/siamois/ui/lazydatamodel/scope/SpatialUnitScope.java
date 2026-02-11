package fr.siamois.ui.lazydatamodel.scope;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpatialUnitScope {

    public enum Type {
        INSTITUTION,
        CHILDREN_OF_SPATIAL_UNIT
    }

    private final Type type;

    private final Long institutionId;

    // only used when type == CHILDREN_OF_SPATIAL_UNIT
    private final Long spatialUnitId;

    public static SpatialUnitScope forInstitution(Long institutionId) {
        return SpatialUnitScope.builder()
                .type(Type.INSTITUTION)
                .institutionId(institutionId)
                .build();
    }
}
