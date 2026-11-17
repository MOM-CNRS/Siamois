package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.specimen.Specimen;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

public class SpecimenSpec {

    public static final String ACTION_UNIT_FILTER = "actionUnit";
    public static final String RECORDING_UNIT_FILTER = "recordingUnit";
    public static final String FULL_IDENTIFIER_FILTER = "fullIdentifier";

    private SpecimenSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static List<String> allColumns() {
        return List.of(
                FULL_IDENTIFIER_FILTER
        );
    }

    @NonNull
    public static Specification<Specimen> fullIdentifierContaining(@Nullable String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank())
                return null;
            return cb.like(cb.lower(root.get(FULL_IDENTIFIER_FILTER)), "%" + value.toLowerCase() + "%");
        };
    }

    @NonNull
    public static Specification<Specimen> specimenInInstitution(long institutionId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<Specimen> specimenInActionUnit(long actionUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(ACTION_UNIT_FILTER).get("id"), actionUnitId));
    }

    @NonNull
    public static Specification<Specimen> isInActionUnit(List<Long> ids) {
        return (root, query, cb) -> cb.in(root.get(ACTION_UNIT_FILTER).get("id")).value(ids);
    }

    @NonNull
    public static Specification<Specimen> specimenInRecordingUnit(long recordingUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(RECORDING_UNIT_FILTER).get("id"), recordingUnitId));
    }

    @NonNull
    public static Specification<Specimen> isInRecordingUnit(List<Long> ids) {
        return (root, query, cb) -> cb.in(root.get(RECORDING_UNIT_FILTER).get("id")).value(ids);
    }

    @NonNull
    public static Specification<Specimen> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get("parents")));
    }

    @NonNull
    public static Specification<Specimen> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

    @NonNull
    public static Specification<Specimen> recordingUnitInRecordingUnit(long recordingUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(RECORDING_UNIT_FILTER).get("id"), recordingUnitId));
    }

    /**
     * Finds whose own geometry is set.
     *
     * @return the specification
     */
    @NonNull
    public static Specification<Specimen> hasGeometry() {
        return (root, query, cb) -> cb.isNotNull(root.get("geom"));
    }

    /**
     * Finds with a non-blank answer to the additional text field {@code fieldId} (a geometry field's WKT
     * or EWKT, for one). An {@code EXISTS} subquery, compatible with every sort.
     *
     * @param fieldId the additional field
     * @return the specification
     */
    @NonNull
    public static Specification<Specimen> hasTextAnswerTo(long fieldId) {
        return (root, query, cb) -> {
            Subquery<Integer> answers = query.subquery(Integer.class);
            Root<CustomFieldAnswerText> answer = answers.from(CustomFieldAnswerText.class);
            answers.select(cb.literal(1)).where(
                    cb.equal(answer.get("formConfigAnswer").get("specimen"), root),
                    cb.equal(answer.get("customField").get("id"), fieldId),
                    cb.notEqual(cb.trim(answer.get("value")), ""));
            return cb.exists(answers);
        };
    }
}
