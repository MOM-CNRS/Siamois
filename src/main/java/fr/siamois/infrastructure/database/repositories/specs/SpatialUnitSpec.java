package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

public class SpatialUnitSpec {

    public static final String NAME_FILTER = "name";
    public static final String CATEGORY_FILTER = "category";
    public static final String ID_FILTER = "id";
    public static final String PARENT_FILTER = "parents";
    /** Synthetic sort key: not a real JPA path, resolved via {@link #orderByActionsCount(Sort.Direction)}. */
    public static final String ACTIONS_COUNT_SORT = "actionsCount";
    /** Synthetic sort key: not a real JPA path, resolved via {@link #orderByRecordingUnitCount(Sort.Direction)}. */
    public static final String RECORDING_UNIT_COUNT_SORT = "recordingUnitCount";

    private SpatialUnitSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

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

    @NonNull
    public static Specification<SpatialUnit> categoryIsIn(List<Long> conceptIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(SpatialUnitSpec.CATEGORY_FILTER).get("id")).value(conceptIds);
    }

    @NonNull
    public static Specification<SpatialUnit> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get(PARENT_FILTER)));
    }

    @NonNull
    public static Specification<SpatialUnit> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }


    @NonNull
    public static Specification<SpatialUnit> spatialUnitInSpatialUnit(Long id) {
        return (root, query, criteriaBuilder) -> {
            Join<SpatialUnit, SpatialUnit> parentsJoin = root.join(PARENT_FILTER);
            return criteriaBuilder.equal(parentsJoin.get("id"), id);
        };
    }

    @NonNull
    public static Specification<SpatialUnit> isChildOf(List<Long> parentIds) {
        return (root, query, cb) -> {
            Join<SpatialUnit, SpatialUnit> parentsJoin = root.join(PARENT_FILTER);
            return cb.in(parentsJoin.get("id")).value(parentIds);
        };
    }

    /**
     * Orders by the number of action units related to the spatial unit, via {@code cb.size(...)}
     * on the mapped {@code relatedActionUnitList} collection — no subquery needed. Not a real
     * filtering predicate: returns a neutral conjunction, the ordering is applied as a side effect
     * on {@code query}. Callers must strip this synthetic sort key from the {@code Pageable}/
     * {@code Sort} passed to the repository, since {@code actionsCount} is not a real JPA-mapped path.
     */
    @NonNull
    public static Specification<SpatialUnit> orderByActionsCount(Sort.Direction direction) {
        return (root, query, cb) -> {
            var countExpr = cb.size(root.get("relatedActionUnitList"));
            query.orderBy(direction == Sort.Direction.ASC ? cb.asc(countExpr) : cb.desc(countExpr));
            return cb.conjunction();
        };
    }

    /**
     * Orders by the number of recording units attached to the spatial unit, via {@code cb.size(...)}
     * on the mapped {@code recordingUnitList} collection — no subquery needed. Same caveats as
     * {@link #orderByActionsCount(Sort.Direction)}.
     */
    @NonNull
    public static Specification<SpatialUnit> orderByRecordingUnitCount(Sort.Direction direction) {
        return (root, query, cb) -> {
            var countExpr = cb.size(root.get("recordingUnitList"));
            query.orderBy(direction == Sort.Direction.ASC ? cb.asc(countExpr) : cb.desc(countExpr));
            return cb.conjunction();
        };
    }
}
