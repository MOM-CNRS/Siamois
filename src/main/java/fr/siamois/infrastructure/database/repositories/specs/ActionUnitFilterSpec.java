package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Map;

/**
 * JPA {@link Specification} builders for {@link ProjectListFilter} (plan §3 phase 3 per-column
 * filters). Kept separate from {@link ActionUnitSpec}: that class is shared with the JSF lazy
 * table, and 25 more static filter builders would bloat a class other code already depends on.
 *
 * <p>Every {@code @ManyToMany} target ({@code periods}, {@code subjects}) uses an EXISTS
 * subquery, never {@code join} + {@code distinct} — a {@code distinct} join changes the row shape
 * the count query sees too, and silently inflates {@code totalCount} on a multi-valued match
 * ({@code findAll(spec, pageable)} issues that count query itself). Follows the correlated-subquery
 * pattern already used in {@link ActionUnitSpec#withEditPermission}, not {@code Subquery#correlate},
 * to stay consistent with that file.</p>
 */
public final class ActionUnitFilterSpec {

    private ActionUnitFilterSpec() {}

    @NonNull
    public static Specification<ActionUnit> fromFilter(@NonNull ProjectListFilter filter) {
        Specification<ActionUnit> spec = Specification.where(null);
        for (Map.Entry<String, String> e : filter.containsFilters().entrySet()) {
            spec = spec.and(textContains(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, java.util.List<Long>> e : filter.conceptOneInFilters().entrySet()) {
            spec = spec.and(conceptOneIn(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, java.util.List<Long>> e : filter.conceptManyInFilters().entrySet()) {
            spec = spec.and(conceptManyIn(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, java.util.List<Long>> e : filter.spatialOneInFilters().entrySet()) {
            spec = spec.and(spatialOneIn(e.getKey(), e.getValue()));
        }
        if (filter.idIn() != null) {
            java.util.Set<Long> ids = filter.idIn();
            spec = spec.and((root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids));
        }
        for (Map.Entry<String, ProjectListFilter.NumericRange> e : filter.numericRangeFilters().entrySet()) {
            spec = spec.and(numericRange(e.getKey(), e.getValue()));
        }
        return spec;
    }

    /**
     * Case-insensitive {@code LIKE %value%} on a direct string property — {@code name},
     * {@code fullIdentifier}, {@code oaCode}, {@code scientificManager}.
     */
    @NonNull
    static Specification<ActionUnit> textContains(@NonNull String property, @NonNull String value) {
        return (root, query, cb) ->
                cb.like(cb.lower(cb.coalesce(root.get(property), "")), "%" + value.toLowerCase() + "%");
    }

    /**
     * {@code root.<property>.id IN (ids)} for a {@code @ManyToOne} concept — {@code status}.
     */
    @NonNull
    static Specification<ActionUnit> conceptOneIn(@NonNull String property, @NonNull Collection<Long> ids) {
        return (root, query, cb) -> ids.isEmpty() ? cb.conjunction() : root.get(property).get("id").in(ids);
    }

    /**
     * {@code root.<property>.id IN (ids)} for a {@code @ManyToOne} spatial unit — {@code mainLocation}.
     */
    @NonNull
    static Specification<ActionUnit> spatialOneIn(@NonNull String property, @NonNull Collection<Long> ids) {
        return (root, query, cb) -> ids.isEmpty() ? cb.conjunction() : root.get(property).get("id").in(ids);
    }

    /**
     * EXISTS a matching row in the {@code @ManyToMany} collection — {@code periods}, {@code subjects}
     * (concepts), and {@code spatialContext} (places): only the target's {@code id} is read.
     */
    @NonNull
    static Specification<ActionUnit> conceptManyIn(@NonNull String property, @NonNull Collection<Long> ids) {
        return (root, query, cb) -> {
            if (ids.isEmpty()) return cb.conjunction();
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<ActionUnit> subRoot = subquery.from(ActionUnit.class);
            Join<ActionUnit, Object> concept = subRoot.join(property);
            subquery.select(cb.literal(1L)).where(
                    cb.equal(subRoot.get("id"), root.get("id")),
                    concept.get("id").in(ids));
            return cb.exists(subquery);
        };
    }

    @NonNull
    static Specification<ActionUnit> numericRange(@NonNull String property, @NonNull ProjectListFilter.NumericRange range) {
        return (root, query, cb) -> {
            if (range.from() == null && range.to() == null) return cb.conjunction();
            if (range.from() != null && range.to() != null) {
                return cb.between(root.get(property), range.from(), range.to());
            }
            if (range.from() != null) return cb.ge(root.get(property), range.from());
            return cb.le(root.get(property), range.to());
        };
    }
}
