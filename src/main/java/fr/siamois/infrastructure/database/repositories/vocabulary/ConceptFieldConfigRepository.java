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
    Optional<ConceptFieldConfig> findByFieldCodeForInstitution(Long institutionId, String fieldCode);

    @Query(
            nativeQuery = true,
            value = "SELECT cfc.* FROM concept_field_config cfc " +
                    "WHERE cfc.fk_user_id = :personId AND cfc.fk_institution_id = :institutionId AND cfc.field_code = :fieldCode " +
                    "LIMIT 1"
    )
    Optional<ConceptFieldConfig> findByFieldCodeForUser(Long personId, Long institutionId, String fieldCode);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO concept_field_config(fk_institution_id, field_code, fk_concept_id) " +
                    "VALUES (:institutionId, :fieldCode, :conceptId)"
    )
    void saveConceptForFieldOfInstitution(Long institutionId, String fieldCode, Long conceptId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO concept_field_config(fk_institution_id, fk_user_id, fk_concept_id, field_code) " +
                    "VALUES (:institutionId, :userId, :conceptId, :fieldCode)"
    )
    void saveConceptForFieldOfUser(Long institutionId, Long userId, String fieldCode, Long conceptId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "UPDATE concept_field_config " +
                    "SET fk_concept_id = :conceptId " +
                    "WHERE fk_institution_id = :institutionId AND field_code = :fieldCode AND fk_user_id IS NULL"
    )
    int updateConfigForFieldOfInstitution(Long institutionId, String fieldCode, Long conceptId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "UPDATE concept_field_config " +
                    "SET fk_concept_id = :conceptId " +
                    "WHERE fk_institution_id = :institutionId AND field_code = :fieldCode AND fk_user_id = :userId"
    )
    int updateConfigForFieldOfUser(Long institutionId, Long userId, String fieldCode, Long conceptId);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) > 0 FROM concept_field_config cfc " +
                    "WHERE cfc.fk_user_id = :personId AND " +
                    "cfc.fk_institution_id = :institutionId"
    )
    boolean hasUserConfig(Long personId, Long institutionId);

}
