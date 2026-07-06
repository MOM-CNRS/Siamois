package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.specimen.Specimen;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface SpecimenRepository extends JpaRepository<Specimen, Long>, RevisionRepository<Specimen, Long, Long>, JpaSpecificationExecutor<Specimen>, SpecimenRepositoryCustom {
    List<Specimen> findAllByArkIsNullAndCreatedByInstitution(@NotNull Institution createdByInstitution);

    <T> Optional<T> findById(Long id, Class<T> type);

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


    @Query(
            value = "SELECT s.* " +
                    "FROM specimen s " +
                    "JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id " +
                    "JOIN action_unit au ON ru.fk_action_unit_id = au.action_unit_id " +
                    "WHERE s.full_identifier = :fullIdentifier " +
                    "AND s.fk_institution_id = :institutionId " +
                    "AND ru.full_identifier = :recordingUnitFullIdentifier " +
                    "AND au.full_identifier = :actionUnitFullIdentifier",
            nativeQuery = true
    )
    Optional<Specimen> findByFullIdentifierAndInstitutionIdAndRecordingUnitFullIdentifierAndActionUnitFullIdentifier(
            @Param("fullIdentifier") String fullIdentifier,
            @Param("institutionId") Long institutionId,
            @Param("recordingUnitFullIdentifier") String recordingUnitFullIdentifier,
            @Param("actionUnitFullIdentifier") String actionUnitFullIdentifier
    );

    /** Narrow projection so the bulk existing-specimen lookup below never has to touch the (lazy) recordingUnit association per row. */
    interface ExistingSpecimenKey {
        String getFullIdentifier();
        String getRecordingUnitFullIdentifier();
    }

    /**
     * Bulk variant of {@link #findByFullIdentifierAndInstitutionIdAndRecordingUnitFullIdentifierAndActionUnitFullIdentifier} —
     * one query per distinct (institution, action unit) rather than one per distinct recording unit, so it stays a
     * handful of queries even when there are thousands of recording units each with a single specimen.
     *
     * @param institutionId institution to scope the lookup to
     * @param actionUnitFullIdentifier full identifier of the action unit to scope the lookup to
     * @return the full identifier of every specimen in that (institution, action unit), paired with its recording unit's full identifier
     */
    @Query(
            value = "SELECT s.full_identifier AS fullIdentifier, ru.full_identifier AS recordingUnitFullIdentifier " +
                    "FROM specimen s " +
                    "JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id " +
                    "JOIN action_unit au ON ru.fk_action_unit_id = au.action_unit_id " +
                    "WHERE s.fk_institution_id = :institutionId " +
                    "AND au.full_identifier = :actionUnitFullIdentifier",
            nativeQuery = true
    )
    List<ExistingSpecimenKey> findAllKeysByInstitutionIdAndActionUnitFullIdentifier(
            @Param("institutionId") Long institutionId,
            @Param("actionUnitFullIdentifier") String actionUnitFullIdentifier
    );


    @Query(
            value = "SELECT s.* FROM specimen s " +
                    "JOIN institution i ON s.fk_institution_id = i.institution_id " +
                    "WHERE s.full_identifier = :fullIdentifier " +
                    "AND i.institution_id IN :institutionIds " +
                    "LIMIT 1",
            nativeQuery = true
    )
    Optional<Specimen> findFirstByFullIdentifierAndInstitutionIdIn(
            @Param("fullIdentifier") String fullIdentifier,
            @Param("institutionIds") Set<Long> institutionIds
    );

    @Query(
            value = "SELECT COUNT(*) FROM specimen s "+
                    "LEFT JOIN recording_unit ru ON s.fk_recording_unit_id = ru.recording_unit_id "+
                    "WHERE ru.fk_action_unit_id = :actionUnitId",
            nativeQuery = true
    )
    Integer countByActionContext(@Param("actionUnitId") Long actionUnitId);

    Optional<Specimen> findFirstByRecordingUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(Long institutionId, OffsetDateTime createdAt);

    Optional<Specimen> findFirstByRecordingUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(Long institutionId, OffsetDateTime createdAt);

    Optional<Specimen> findFirstByRecordingUnitIdOrderByCreationTimeAsc(Long institutionId);  // oldest

    Optional<Specimen> findFirstByRecordingUnitIdOrderByCreationTimeDesc(Long institutionId);

    @Query("SELECT s FROM Specimen s WHERE s.recordingUnit.actionUnit.id = :actionUnitId")
    List<Specimen> findFirst10ByActionUnitId(@Param("actionUnitId") Long actionUnitId);

    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM specimen_movement WHERE fk_specimen_id = :specimenId")
    long countMovementsBySpecimenId(@Param("specimenId") long specimenId);

    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM specimen_group_attribution WHERE fk_specimen_id = :specimenId")
    long countGroupAttributionsBySpecimenId(@Param("specimenId") long specimenId);
}

