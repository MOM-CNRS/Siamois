package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.specimen.Specimen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Requêtes natives specimen avec {@code ORDER BY} explicite (évite le tri JPA sur propriétés Java).
 */
public interface SpecimenRepositoryCustom {

    Page<Specimen> findAllByInstitutionAndRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            Long recordingUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            String orderByClause,
            Pageable pageable);
}
