package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;

public class RecordingUnitSpec {

    public static final String FULL_IDENTIFIER = "fullIdentifier";
    public static final String AUTHOR_FILTER = "author";
    public static final String ACTION_UNIT_FILTER = "actionUnit";
    public static final String SPATIAL_UNIT_FILTER = "spatialUnit";
    public static final String OPENING_DATE_FILTER = "openingDate";
    public static final String CLOSING_DATE_FILTER = "closingDate";

    public static Specification<RecordingUnit> recordingUnitInInstitution(long institutionId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    public static Specification<RecordingUnit> recordingUnitInActionUnit(long actionUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("actionUnit").get("id"), actionUnitId));
    }

    public static Specification<RecordingUnit> fullIdentifierStartsWith(String fullIdentifier) {
        return (root, query, criteriaBuilder) -> {
            if (fullIdentifier == null || fullIdentifier.isEmpty()) {
                return null;
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get(FULL_IDENTIFIER)), fullIdentifier.toLowerCase() + "%");
        };
    }

    public static Specification<RecordingUnit> entityFieldIdIn(String fieldName, List<Long> ids) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(fieldName).get("id")).value(ids);
    }

    public static Specification<RecordingUnit> dateFieldBetween(String fieldName, OffsetDateTime from, OffsetDateTime to) {
        return (root, query, criteriaBuilder) -> {
            if (from != null && to != null) {
                return criteriaBuilder.between(root.get(fieldName), from, to);
            } else if (from != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get(fieldName), from);
            } else if (to != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get(fieldName), to);
            }
            return null;
        };
    }

}
