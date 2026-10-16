package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.study.Study;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

public class StudySpec {

    public static final String IDENTIFIER_FILTER = "identifier";
    public static final String ACTION_UNIT_FILTER = "actionUnit";

    private StudySpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static Specification<Study> belongsToInstitution(long institutionId) {
        return (root, query, cb) -> cb.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<Study> identifierContaining(@Nullable String identifier) {
        return (root, query, cb) -> {
            if (identifier == null || identifier.isBlank()) return null;
            return cb.like(cb.lower(root.get(IDENTIFIER_FILTER)), "%" + identifier.toLowerCase() + "%");
        };
    }

    @NonNull
    public static Specification<Study> isInActionUnit(List<Long> actionUnitIds) {
        return (root, query, cb) -> cb.in(root.get(ACTION_UNIT_FILTER).get("id")).value(actionUnitIds);
    }
}
