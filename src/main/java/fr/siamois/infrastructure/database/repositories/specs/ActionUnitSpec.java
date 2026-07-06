package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.permissions.PermissionScopeType;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;
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

    /**
     * Action units the person is allowed to display through the profile permission
     * system: an INSTANCE- or ORGANISATION-scoped profile holding
     * {@link PermissionConstants#ORGANIZATION_ACCESS} (the latter restricted to the
     * unit's institution), or any PROJECT-scoped profile assigned on the unit itself.
     */
    @NonNull
    public static Specification<ActionUnit> visibleToPerson(Long personId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<PersonProfileAssignment> assignment = subquery.from(PersonProfileAssignment.class);
            Join<PersonProfileAssignment, Profile> profile = assignment.join("profile");
            Join<Profile, Permission> permission = profile.join("permissions", JoinType.LEFT);

            subquery.select(cb.literal(1L)).where(
                    cb.equal(assignment.get("person").get("id"), personId),
                    cb.or(
                            cb.and(
                                    cb.equal(profile.get("scope"), PermissionScopeType.INSTANCE),
                                    cb.equal(permission.get("code"), PermissionConstants.ORGANIZATION_ACCESS)),
                            cb.and(
                                    cb.equal(profile.get("scope"), PermissionScopeType.ORGANISATION),
                                    cb.equal(profile.get("institution"), root.get("createdByInstitution")),
                                    cb.equal(permission.get("code"), PermissionConstants.ORGANIZATION_ACCESS)),
                            cb.and(
                                    cb.equal(profile.get("scope"), PermissionScopeType.PROJECT),
                                    cb.equal(profile.get("actionUnit"), root))
                    ));
            return cb.exists(subquery);
        };
    }

    @NonNull
    public static Specification<ActionUnit> actionUnitInSpatialUnit(long spatialUnitId) {
        return (root, query, cb) ->
                cb.equal(root.get(SPATIAL_UNIT_FILTER).get("id"), spatialUnitId);
    }

    @NonNull
    public static Specification<ActionUnit> isInSpatialUnit(List<Long> ids) {
        return (root, query, cb) -> cb.in(root.get(SPATIAL_UNIT_FILTER).get("id")).value(ids);
    }

}
