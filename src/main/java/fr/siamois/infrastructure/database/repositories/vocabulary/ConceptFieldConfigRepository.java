package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.settings.ConceptFieldConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConceptFieldConfigRepository extends CrudRepository<ConceptFieldConfig, Long> {

    @Query(
            nativeQuery = true,
            value = "SELECT cfc.* FROM concept_field_config cfc " +
                    "WHERE cfc.fk_institution_id = :institutionId AND cfc.field_code = :fieldCode"
    )
    Optional<ConceptFieldConfig> findByFieldCodeForInstitution(Long institutionId, String fieldCode);

    @Query(
            nativeQuery = true,
            value = "SELECT cfc.* FROM concept_field_config cfc " +
                    "WHERE cfc.fk_user_id = :personId AND cfc.field_code = :fieldCode"
    )
    Optional<ConceptFieldConfig> findByFieldCodeForUser(Long personId, String fieldCode);


}
