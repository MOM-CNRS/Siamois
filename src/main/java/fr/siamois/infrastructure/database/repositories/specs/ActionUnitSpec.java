package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;

public class ActionUnitSpec {

    public static final String GLOBAL_FILTER = "global";
    public static final String NAME_FILTER = "name";
    public static final String ID_FILTER = "id";

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

    /**
     * Action units whose owning institution is one of the given ids.
     */
    @NonNull
    public static Specification<ActionUnit> institutionIdIn(@Nullable Collection<Long> institutionIds) {
        return (root, query, cb) -> {
            if (institutionIds == null || institutionIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("createdByInstitution").get("id").in(institutionIds);
        };
    }

    /**
     * Case-insensitive match on name, identifier, or full identifier (OR).
     */
    @NonNull
    public static Specification<ActionUnit> projectSearch(@Nullable String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("identifier"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("fullIdentifier"), "")), pattern)
            );
        };
    }

}
