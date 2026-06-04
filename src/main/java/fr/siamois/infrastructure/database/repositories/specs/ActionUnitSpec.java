package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

public class ActionUnitSpec {

    public static final String GLOBAL_FILTER = "global";
    public static final String NAME_FILTER = "name";
    public static final String ID_FILTER = "id";
    public static final String SPATIAL_UNIT_FILTER = "mainLocation";

    private ActionUnitSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static Specification<ActionUnit> belongsToInstitution(long institutionId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId));
    }

    @NonNull
    public static Specification<ActionUnit> nameContaining(@Nullable String name) {
        return ((root, query, criteriaBuilder) -> {
            if (name == null || name.isBlank())
                return null;
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        });
    }

    public static Specification<ActionUnit> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get("parents")));
    }

    public static Specification<ActionUnit> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

    @NonNull
    public static Specification<ActionUnit> actionUnitInSpatialUnit(long spatialUnitId) {
        return (root, query, cb) ->
                cb.equal(root.get("mainLocation").get("id"), spatialUnitId);
    }

    @NonNull
    public static Specification<ActionUnit> isInSpatialUnit(List<Long> ids) {
        return (root, query, cb) -> cb.in(root.get(SPATIAL_UNIT_FILTER).get("id")).value(ids);
    }

}
