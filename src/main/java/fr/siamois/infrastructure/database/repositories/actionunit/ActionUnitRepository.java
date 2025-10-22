package fr.siamois.infrastructure.database.repositories.actionunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.institution.Institution;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ActionUnitRepository extends CrudRepository<ActionUnit, Long>, RevisionRepository<ActionUnit, Long, Long> {

    Optional<ActionUnit> findByFullIdentifier(String fullIdentifier);

    Optional<ActionUnit> findByArk(Ark ark);

    List<ActionUnit> findAllByArkIsNullAndCreatedByInstitution(@NotNull Institution createdByInstitution);

    @Query(
            value = "SELECT COUNT(*) FROM action_unit_spatial_context WHERE fk_spatial_unit_id = :spatialUnitId",
            nativeQuery = true
    )
    Integer countBySpatialContext(@Param("spatialUnitId") Long spatialUnitId);

    long countByCreatedByInstitution(Institution institution);

    @Query(
            nativeQuery = true,
            value = "WITH ranked_labels AS ( " +
                    "    SELECT " +
                    "        l.fk_concept_id, " +
                    "        l.label_value, " +
                    "        l.lang_code, " +
                    "        ROW_NUMBER() OVER ( " +
                    "            PARTITION BY l.fk_concept_id " +
                    "            ORDER BY  " +
                    "                CASE  " +
                    "                    WHEN l.lang_code = :langCode THEN 1 " +
                    "                    WHEN l.lang_code = 'en' THEN 2 " +
                    "                    ELSE 3 " +
                    "                END " +
                    "        ) AS rank " +
                    "    FROM label l " +
                    ") " +
                    "SELECT " +
                    "    au.*, " +
                    "    p.name as p_name, " +
                    "    p.lastname as p_lastname, " +
                    "    rl.label_value AS c_label " +
                    "FROM action_unit au " +
                    "LEFT JOIN person p ON au.fk_author_id = p.person_id " +
                    "LEFT JOIN concept c ON au.fk_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE au.fk_institution_id = :institutionId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR au.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR au.fk_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ",
            countQuery = "WITH ranked_labels AS ( " +
                    "    SELECT " +
                    "        l.fk_concept_id, " +
                    "        l.label_value, " +
                    "        l.lang_code, " +
                    "        ROW_NUMBER() OVER ( " +
                    "            PARTITION BY l.fk_concept_id " +
                    "            ORDER BY  " +
                    "                CASE  " +
                    "                    WHEN l.lang_code = :langCode THEN 1 " +
                    "                    WHEN l.lang_code = 'en' THEN 2 " +
                    "                    ELSE 3 " +
                    "                END " +
                    "        ) AS rank " +
                    "    FROM label l " +
                    ") " +
                    "SELECT " +
                    "    count(au) " +
                    "FROM action_unit au " +
                    "LEFT JOIN person p ON au.fk_author_id = p.person_id " +
                    "LEFT JOIN concept c ON au.fk_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE au.fk_institution_id = :institutionId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR au.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR au.fk_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )
    Page<ActionUnit> findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(@Param("institutionId") Long institutionId,
                                                                                                 @Param("name") String name,
                                                                                                 @Param("categoryIds") Long[] categoryIds,
                                                                                                 @Param("personIds") Long[] personIds,
                                                                                                 @Param("global") String global,
                                                                                                 @Param("langCode") String langCode,
                                                                                                 Pageable pageable);

    @Query(
            nativeQuery = true,
            value = "WITH ranked_labels AS ( " +
                    "    SELECT " +
                    "        l.fk_concept_id, " +
                    "        l.label_value, " +
                    "        l.lang_code, " +
                    "        ROW_NUMBER() OVER ( " +
                    "            PARTITION BY l.fk_concept_id " +
                    "            ORDER BY  " +
                    "                CASE  " +
                    "                    WHEN l.lang_code = :langCode THEN 1 " +
                    "                    WHEN l.lang_code = 'en' THEN 2 " +
                    "                    ELSE 3 " +
                    "                END " +
                    "        ) AS rank " +
                    "    FROM label l " +
                    ") " +
                    "SELECT " +
                    "    au.*, " +
                    "    p.name as p_name, " +
                    "    p.lastname as p_lastname, " +
                    "    rl.label_value AS c_label " +
                    "FROM action_unit au " +
                    "LEFT JOIN action_unit_spatial_context ausc ON au.action_unit_id = ausc.fk_action_unit_id " +
                    "LEFT JOIN person p ON au.fk_author_id = p.person_id " +
                    "LEFT JOIN concept c ON au.fk_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE au.fk_institution_id = :institutionId " +
                    "  AND ausc.fk_spatial_unit_id = :spatialUnitId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR au.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR au.fk_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ",
            countQuery = "WITH ranked_labels AS ( " +
                    "    SELECT " +
                    "        l.fk_concept_id, " +
                    "        l.label_value, " +
                    "        l.lang_code, " +
                    "        ROW_NUMBER() OVER ( " +
                    "            PARTITION BY l.fk_concept_id " +
                    "            ORDER BY  " +
                    "                CASE  " +
                    "                    WHEN l.lang_code = :langCode THEN 1 " +
                    "                    WHEN l.lang_code = 'en' THEN 2 " +
                    "                    ELSE 3 " +
                    "                END " +
                    "        ) AS rank " +
                    "    FROM label l " +
                    ") " +
                    "SELECT " +
                    "    count(au) " +
                    "FROM action_unit au " +
                    "LEFT JOIN action_unit_spatial_context ausc ON au.action_unit_id = ausc.fk_action_unit_id " +
                    "LEFT JOIN person p ON au.fk_author_id = p.person_id " +
                    "LEFT JOIN concept c ON au.fk_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE au.fk_institution_id = :institutionId " +
                    "  AND ausc.fk_spatial_unit_id = :spatialUnitId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR au.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR au.fk_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(au.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )

    Page<ActionUnit> findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
            @Param("institutionId") Long institutionId,
            @Param("spatialUnitId") Long spatialUnitId,
            @Param("name") String name,
            @Param("categoryIds") Long[] categoryIds,
            @Param("personIds") Long[] personIds,
            @Param("global") String global,
            @Param("langCode") String langCode,
            Pageable pageable);

    Set<ActionUnit> findByCreatedByInstitution(Institution createdByInstitution);
    Optional<ActionUnit> findByNameAndCreatedByInstitution(String name, Institution institution);
    Optional<ActionUnit> findByIdentifierAndCreatedByInstitution(String identifier, Institution institution);
}
