package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.List;

public class SpecimenSpec {

    private SpecimenSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static List<String> allColumns() {
        return List.of(

        );
    }

    @NonNull
    public static Specification<RecordingUnit> specimenInInstitution(long institutionId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<RecordingUnit> specimenInActionUnit(long actionUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("actionUnit").get("id"), actionUnitId));
    }

    @NonNull
    public static Specification<RecordingUnit> recordingUnitInRecordingUnit(long recordingUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("recordingUnit").get("id"), recordingUnitId));
    }


}
