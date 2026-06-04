package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.specimen.Specimen;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.util.List;

public class SpecimenSpec {

    public static final String ACTION_UNIT_FILTER = "actionUnit";
    public static final String RECORDING_UNIT_FILTER = "recordingUnit";

    private SpecimenSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static List<String> allColumns() {
        return List.of(

        );
    }

    @NonNull
    public static Specification<Specimen> specimenInInstitution(long institutionId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<Specimen> specimenInActionUnit(long actionUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("actionUnit").get("id"), actionUnitId));
    }

    @NonNull
    public static Specification<Specimen> isInActionUnit(List<Long> ids) {
        return (root, query, cb) -> cb.in(root.get(ACTION_UNIT_FILTER).get("id")).value(ids);
    }

    @NonNull
    public static Specification<Specimen> specimenInRecordingUnit(long recordingUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("recordingUnit").get("id"), recordingUnitId));
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
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("recordingUnit").get("id"), recordingUnitId));
    }


}
