package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.document.Document;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;

public class DocumentSpec {

    public static final String GLOBAL_FILTER = "global";
    public static final String IDENTIFIER_FILTER = "identifier";
    public static final String ACTION_UNIT_FILTER = "actionUnit";

    private DocumentSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static Specification<Document> belongsToInstitution(long institutionId) {
        return (root, query, cb) -> cb.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<Document> belongsToActionUnit(long actionUnitId) {
        return (root, query, cb) -> cb.equal(root.get(ACTION_UNIT_FILTER).get("id"), actionUnitId);
    }

    /** The documents no migration could attach to a project: to be repaired by hand. */
    @NonNull
    public static Specification<Document> withoutProject() {
        return (root, query, cb) -> cb.isNull(root.get(ACTION_UNIT_FILTER));
    }

    @NonNull
    public static Specification<Document> isInActionUnit(Collection<Long> actionUnitIds) {
        return (root, query, cb) -> cb.in(root.get(ACTION_UNIT_FILTER).get("id")).value(actionUnitIds);
    }

    @NonNull
    public static Specification<Document> identifierContaining(@Nullable String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank()) return null;
            return cb.like(cb.lower(root.get(IDENTIFIER_FILTER)), "%" + value.toLowerCase() + "%");
        };
    }

    @NonNull
    public static Specification<Document> linkedToRecordingUnit(long recordingUnitId) {
        return linkedTo("recordingUnits", recordingUnitId);
    }

    @NonNull
    public static Specification<Document> linkedToFind(long specimenId) {
        return linkedTo("finds", specimenId);
    }

    @NonNull
    public static Specification<Document> linkedToPlace(long spatialUnitId) {
        return linkedTo("places", spatialUnitId);
    }

    @NonNull
    public static Specification<Document> linkedToPhase(long phaseId) {
        return linkedTo("phases", phaseId);
    }

    @NonNull
    public static Specification<Document> linkedToContainer(long containerId) {
        return linkedTo("containers", containerId);
    }

    private static Specification<Document> linkedTo(String collection, long targetId) {
        return (root, query, cb) -> {
            if (query != null) query.distinct(true);
            return cb.equal(root.join(collection, JoinType.INNER).get("id"), targetId);
        };
    }

    public static Specification<Document> idIn(Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }
}
