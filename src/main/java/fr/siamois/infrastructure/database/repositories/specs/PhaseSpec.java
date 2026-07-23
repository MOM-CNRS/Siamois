package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.phase.Phase;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;

public class PhaseSpec {

    public static final String GLOBAL_FILTER = "global";
    public static final String NAME_FILTER = "name";

    private PhaseSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static Specification<Phase> belongsToInstitution(long institutionId) {
        return (root, query, cb) -> cb.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<Phase> belongsToActionUnit(long actionUnitId) {
        return (root, query, cb) -> cb.equal(root.get("actionUnit").get("id"), actionUnitId);
    }

    @NonNull
    public static Specification<Phase> identifierContaining(@Nullable String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank()) return null;
            return cb.like(cb.lower(root.get("identifier")), "%" + value.toLowerCase() + "%");
        };
    }

    public static Specification<Phase> idIn(Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }
}
