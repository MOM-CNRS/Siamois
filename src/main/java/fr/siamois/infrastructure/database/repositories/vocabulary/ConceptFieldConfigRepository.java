package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.settings.ConceptFieldConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptFieldConfigRepository extends CrudRepository<ConceptFieldConfig, Long> {

    @Query("SELECT cfc FROM ConceptFieldConfig cfc " +
            "WHERE cfc.institution.id = :institutionId " +
            "AND cfc.fieldCode = :fieldCode " +
            "AND cfc.actionUnit IS NULL " +
            "ORDER BY cfc.id ASC")
    List<ConceptFieldConfig> findAllByFieldCodeForInstitutionOrdered(Long institutionId, String fieldCode);

    /**
     * Institution-level field config. Tolerates historical duplicates by returning the oldest row.
     */
    default Optional<ConceptFieldConfig> findOneByFieldCodeForInstitution(Long institutionId, String fieldCode) {
        List<ConceptFieldConfig> matches = findAllByFieldCodeForInstitutionOrdered(institutionId, fieldCode);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }


    @Query("SELECT DISTINCT cfc.fieldCode " +
            "FROM ConceptFieldConfig cfc " +
            "WHERE cfc.institution.id = :institutionId " +
            "ORDER BY cfc.fieldCode")
    List<String> findDistinctFieldCodesForInstitution(Long institutionId);

    Optional<ConceptFieldConfig> findOneByFieldCodeAndActionUnitId(String fieldCode, Long actionUnitId);
}
