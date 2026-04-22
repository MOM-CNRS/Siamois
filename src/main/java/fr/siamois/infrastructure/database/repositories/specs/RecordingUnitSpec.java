package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import org.springframework.data.jpa.domain.Specification;

public class RecordingUnitSpec {

    public static final String FULL_IDENTIFIER = "fullIdentifier";

    public static Specification<RecordingUnit> recordingUnitInInstitution(long institutionId) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
        };
    }

    public static Specification<RecordingUnit> fullIdentifierStartsWith(String fullIdentifier) {
        return (root, query, criteriaBuilder) -> {
            if (fullIdentifier == null || fullIdentifier.isEmpty()) {
                return null;
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get(FULL_IDENTIFIER)), fullIdentifier.toLowerCase() + "%");
        };
    }

}
