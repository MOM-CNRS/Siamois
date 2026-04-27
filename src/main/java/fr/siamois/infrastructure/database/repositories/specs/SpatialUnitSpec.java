package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

public class SpatialUnitSpec {

    public static final String NAME_FILTER = "name";
    public static final String CATEGORY_FILTER = "category";

    @NonNull
    public static Specification<SpatialUnit> belongsToInstitution(long institutionId) {
        return (root, query, criteriaBuilder) ->  criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<SpatialUnit> nameContaining(@Nullable String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.isEmpty()) {
                return null;
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<SpatialUnit> categoryIsIn(List<Long> conceptIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(SpatialUnitSpec.CATEGORY_FILTER).get("id")).value(conceptIds);
    }

    public static Specification<SpatialUnit> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get("parents")));
    }

    public static Specification<SpatialUnit> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }
}
