package fr.siamois.infrastructure.database.repositories.specimen;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.specimen.Specimen;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecimenRepository extends CrudRepository<Specimen, Long>, RevisionRepository<Specimen, Long, Long> {
    List<Specimen> findAllByArkIsNullAndCreatedByInstitution(@NotNull Institution createdByInstitution);

    long countByCreatedByInstitution(Institution institution);

    /**
     * Returns the maximum identifier given to a SPECIMEN in the context of an RECORDING UNIT
     *
     * @return The max identifier
     */
    @Query(
            nativeQuery = true,
            value = "SELECT MAX(s.identifier) " +
                    "FROM specimen s where s.fk_recording_unit_id = :recordingUnitId"
    )
    Integer findMaxUsedIdentifierByRecordingUnit(Long recordingUnitId);

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
                    "    s.*, " +
                    "    rl.label_value AS c_label " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE s.fk_institution_id = :institutionId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ",
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
                    "    count(s) " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE s.fk_institution_id = :institutionId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )
    Page<Specimen> findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            @Param("institutionId") Long institutionId,
            @Param("fullIdentifier") String fullIdentifier,
            @Param("categoryIds") Long[] categoryIds,
            @Param("global") String global,
            @Param("langCode") String langCode,
            Pageable pageable);

    @Query(
            value = "UPDATE specimen SET fk_specimen_type = :type WHERE specimen.specimen_id IN (:ids)",
            nativeQuery = true
    )
    @Modifying
    int updateTypeByIds(@Param("type") Long type, @Param("ids") List<Long> ids);

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
                    "    s.*, " +
                    "    rl.label_value AS c_label " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE s.fk_institution_id = :institutionId " +
                    "  AND s.fk_recording_unit_id = :recordingUnitId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ",
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
                    "    count(s) " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE s.fk_institution_id = :institutionId " +
                    "  AND s.fk_recording_unit_id = :recordingUnitId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )
    Page<Specimen> findAllByInstitutionAndByRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            @Param("institutionId") Long institutionId,
            @Param("recordingUnitId") Long recordingUnitId,
            @Param("fullIdentifier") String fullIdentifier,
            @Param("categoryIds") Long[] categoryIds,
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
                    "    s.*, " +
                    "    rl.label_value AS c_label " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_spatial_unit_id = :spatialUnitId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ",
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
                    "    count(s) " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_spatial_unit_id = :spatialUnitId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )
    Page<Specimen> findAllBySpatialUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            @Param("spatialUnitId") Long spatialUnitId,
            @Param("fullIdentifier") String fullIdentifier,
            @Param("categoryIds") Long[] categoryIds,
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
                    "    s.*, " +
                    "    rl.label_value AS c_label " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_action_unit_id = :actionUnitId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ",
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
                    "    count(s) " +
                    "FROM specimen s " +
                    "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_action_unit_id = :actionUnitId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )
    Page<Specimen> findAllByActionUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            @Param("actionUnitId") Long actionUnitId,
            @Param("fullIdentifier") String fullIdentifier,
            @Param("categoryIds") Long[] categoryIds,
            @Param("global") String global,
            @Param("langCode") String langCode,
            Pageable pageable);


    Optional<Specimen> findByFullIdentifierAndCreatedByInstitution(String i, Institution createdInstitution);

    Optional<Specimen> findByFullIdentifier(@NotNull String fullIdentifier);

    @Query(
            value = "SELECT COUNT(*) FROM specimen s "+
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_spatial_unit_id = :spatialUnitId",
            nativeQuery = true
    )
    Integer countBySpatialContext(@Param("spatialUnitId") Long spatialUnitId);

    @Query(
            value = "SELECT COUNT(*) FROM specimen s "+
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_action_unit_id = :actionUnitId",
            nativeQuery = true
    )
    Integer countByActionContext(@Param("actionUnitId") Long actionUnitId);
}

