package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;

public class RecordingUnitSpec {

    public static final String FULL_IDENTIFIER = "fullIdentifier";
    public static final String AUTHOR_FILTER = "author";
    public static final String MATRIX_FILTER = "matrixColor";
    public static final String ACTION_UNIT_FILTER = "actionUnit";
    public static final String SPATIAL_UNIT_FILTER = "spatialUnit";
    public static final String OPENING_DATE_FILTER = "openingDate";
    public static final String CLOSING_DATE_FILTER = "closingDate";
    public static final String CONTRIBUTORS_FILTER = "contributors";
    public static final String TYPE_FILTER = "type";


    public static List<String> allColumns() {
        return List.of(
                FULL_IDENTIFIER,
                AUTHOR_FILTER,
                MATRIX_FILTER,
                ACTION_UNIT_FILTER,
                SPATIAL_UNIT_FILTER,
                OPENING_DATE_FILTER,
                CLOSING_DATE_FILTER,
                CONTRIBUTORS_FILTER,
                TYPE_FILTER
        );
    }

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

    public static Specification<RecordingUnit> authorIsIn(List<Long> personsIds) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.in(root.get(RecordingUnitSpec.AUTHOR_FILTER).get("id")).value(personsIds);
        };
    }

    public static Specification<RecordingUnit> matrixContains(String matrixInput) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.like(criteriaBuilder.lower(root.get(RecordingUnitSpec.MATRIX_FILTER)), matrixInput.toLowerCase() + "%");
        };
    }

    public static Specification<RecordingUnit> isInSpatialUnit(List<Long> spatialUnitIds) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.in(root.get(RecordingUnitSpec.SPATIAL_UNIT_FILTER).get("id")).value(spatialUnitIds);
        };
    }

    public static Specification<RecordingUnit> isInActionUnit(List<Long> actionUnitIds) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.in(root.get(RecordingUnitSpec.ACTION_UNIT_FILTER).get("id")).value(actionUnitIds);
        };
    }

    public static Specification<RecordingUnit> isInContributors(List<Long> personIds) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.in(root.get(RecordingUnitSpec.CONTRIBUTORS_FILTER).get("id")).value(personIds);
        };
    }

    public static Specification<RecordingUnit> typeIsIn(List<Long> conceptIds) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.in(root.get(RecordingUnitSpec.TYPE_FILTER).get("id")).value(conceptIds);
        };
    }

    public static Specification<RecordingUnit> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get("parents")));
    }

    public static Specification<RecordingUnit> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

}
