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
            "AND cfc.actionUnit IS NULL")
    Optional<ConceptFieldConfig> findOneByFieldCodeForInstitution(Long institutionId, String fieldCode);


    @Query("SELECT DISTINCT cfc " +
            "FROM ConceptFieldConfig cfc " +
            "WHERE cfc.institution.id = :institutionId " +
            "ORDER BY cfc.fieldCode")
    List<String> findDistinctFieldCodesForInstitution(Long institutionId);

    Optional<ConceptFieldConfig> findOneByFieldCodeAndActionUnitId(String fieldCode, Long actionUnitId);
}
