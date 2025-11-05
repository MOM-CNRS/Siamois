package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.settings.ConceptFieldConfig;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConceptFieldConfigRepository extends CrudRepository<ConceptFieldConfig, Long> {

    @Query(
            nativeQuery = true,
            value = "SELECT cfc.* FROM concept_field_config cfc " +
                    "WHERE cfc.fk_institution_id = :institutionId AND cfc.field_code = :fieldCode AND cfc.fk_user_id IS NULL " +
                    "LIMIT 1"
    )
    Optional<ConceptFieldConfig> findOneByFieldCodeForInstitution(Long institutionId, String fieldCode);

    @Query(
            nativeQuery = true,
            value = "SELECT cfc.* FROM concept_field_config cfc " +
                    "WHERE cfc.fk_user_id = :personId AND cfc.fk_institution_id = :institutionId AND cfc.field_code = :fieldCode " +
                    "LIMIT 1"
    )
    Optional<ConceptFieldConfig> findByFieldCodeForUser(Long personId, Long institutionId, String fieldCode);

}
