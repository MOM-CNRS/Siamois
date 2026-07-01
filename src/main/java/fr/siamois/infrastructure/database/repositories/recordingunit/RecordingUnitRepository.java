package fr.siamois.infrastructure.database.repositories.recordingunit;


import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.infrastructure.database.projection.RecordingUnitCountsProjection;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RecordingUnitRepository extends CrudRepository<RecordingUnit, Long>, RevisionRepository<RecordingUnit, Long, Long>, JpaSpecificationExecutor<RecordingUnit> {

    @Query("SELECT COUNT(s) FROM Specimen s WHERE s.recordingUnit.id = :recordingUnitId")
    Long countSpecimensByRecordingUnitId(@Param("recordingUnitId") Long recordingUnitId);

    @Query("SELECT COUNT(r) FROM StratigraphicRelationship r WHERE r.unit1.id = :recordingUnitId OR r.unit2.id = :recordingUnitId")
    long countStratigraphicRelationshipsByRecordingUnitId(@Param("recordingUnitId") Long recordingUnitId);
    @Query("SELECT r.id as id, COUNT(s) as cnt FROM RecordingUnit r LEFT JOIN r.specimenList s WHERE r.id IN :ids GROUP BY r.id")
    List<Object[]> countSpecimensByIds(@Param("ids") List<Long> ids);

    @Query(value = """
        SELECT ru_id, COUNT(*) FROM (
            SELECT fk_recording_unit_1_id AS ru_id FROM stratigraphic_relationship WHERE fk_recording_unit_1_id IN (:ids)
            UNION ALL
            SELECT fk_recording_unit_2_id AS ru_id FROM stratigraphic_relationship WHERE fk_recording_unit_2_id IN (:ids)
        ) sub GROUP BY ru_id
        """, nativeQuery = true)
    List<Object[]> countRelationshipsByIds(@Param("ids") List<Long> ids);

    @Query(
            value = "UPDATE recording_unit SET fk_type = :type WHERE recording_unit.recording_unit_id IN (:ids)",
            nativeQuery = true
    )
    @Modifying
    int updateTypeByIds(@Param("type") Long type, @Param("ids") List<Long> ids);

    /**
     * @param spatialUnitId - The ID of the spatial unit
     * @return List of recording units
     */
    @Query(
            nativeQuery = true,
            value = "SELECT ru.* FROM recording_unit ru " +
                    "WHERE ru.fk_spatial_unit_id = :spatialUnitId"
    )
    List<RecordingUnit> findAllBySpatialUnitId(Long spatialUnitId);

    List<RecordingUnit> findAllByActionUnitId(Long actionUnitId);

    Page<RecordingUnit> findAllByActionUnitId(Long actionUnitId, Pageable pageable);

    long countByActionUnit_Id(Long actionUnitId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM recording_unit_contributors WHERE fk_recording_unit_id = :recordingUnitId")
    void deleteContributorLinksForRecordingUnit(@Param("recordingUnitId") Long recordingUnitId);
    List<RecordingUnit> findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(Long actionUnitId, String query, org.springframework.data.domain.Pageable pageable);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO stratigraphic_relationship(fk_recording_unit_1_id, fk_recording_unit_2_id, fk_relationship_concept_id) " +
                    "VALUES (:recordingUnitId1, :recordingUnitId2, :conceptId)"
    )
    void saveStratigraphicRelationship(Long recordingUnitId1, Long recordingUnitId2, Long conceptId);

    /**
     * Returns the maximum identifier given to a recording unit in the context of an action unit
     *
     * @return The max identifier
     */
    @Query(
            nativeQuery = true,
            value = "SELECT MAX(ru.identifier) " +
                    "FROM recording_unit ru where ru.fk_action_unit_id = :actionUnitId"
    )
    Integer findMaxUsedIdentifierByAction(Long actionUnitId);


    @Query(
            nativeQuery = true,
            value = "SELECT ru.* FROM recording_unit ru " +
                    "WHERE ru.fk_ark_id IS NULL AND ru.fk_institution_id = :institutionId"
    )
    List<RecordingUnit> findAllWithoutArkOfInstitution(Long institutionId);

    long countByCreatedByInstitutionId(Long institutionId);

    Optional<RecordingUnit> findByIdentifierAndCreatedByInstitution(Integer identifier, Institution institution);

    @Query(
            value = "SELECT ru.* " +
                    "FROM recording_unit ru " +
                    "JOIN institution i ON ru.fk_institution_id = i.institution_id " +
                    "WHERE ru.full_identifier = :fullIdentifier " +
                    "AND i.identifier = :institutionIdentifier",
            nativeQuery = true
    )
    Optional<RecordingUnit> findByFullIdentifierAndInstitutionIdentifier(
            @Param("fullIdentifier") String fullIdentifier,
            @Param("institutionIdentifier") String institutionIdentifier
    );

    @Query(
            value = "SELECT ru.* " +
                    "FROM recording_unit ru " +
                    "JOIN institution i ON ru.fk_institution_id = i.institution_id " +
                    "WHERE ru.full_identifier = :fullIdentifier " +
                    "AND i.institution_id = :institutionId",
            nativeQuery = true
    )
    Optional<RecordingUnit> findByFullIdentifierAndInstitutionId(
            @Param("fullIdentifier") String fullIdentifier,
            @Param("institutionId") Long institutionId
    );

    Optional<RecordingUnit> findByFullIdentifier(@NonNull String fullIdentifier);

    @Query(
            value = "SELECT ru.* FROM recording_unit ru " +
                    "JOIN institution i ON ru.fk_institution_id = i.institution_id " +
                    "WHERE ru.full_identifier = :fullIdentifier " +
                    "AND i.institution_id IN :institutionIds " +
                    "LIMIT 1",
            nativeQuery = true
    )
    Optional<RecordingUnit> findFirstByFullIdentifierAndInstitutionIdIn(
            @Param("fullIdentifier") String fullIdentifier,
            @Param("institutionIds") Set<Long> institutionIds
    );

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
                    "    ru.*, " +
                    "    rl.label_value AS c_label " +
                    "FROM recording_unit ru " +
                    "LEFT JOIN concept c ON ru.fk_type = c.concept_id " +
                    "LEFT JOIN recording_unit_hierarchy ruh ON ru.recording_unit_id = ruh.fk_child_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE ruh.fk_parent_id = :parentId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(ru.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR ru.fk_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(ru.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
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
                    "    ru.*, " +
                    "    rl.label_value AS c_label " +
                    "FROM recording_unit ru " +
                    "LEFT JOIN concept c ON ru.fk_type = c.concept_id " +
                    "LEFT JOIN recording_unit_hierarchy ruh ON ru.recording_unit_id = ruh.fk_child_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE ruh.fk_parent_id = :parentId " +
                    "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(ru.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR ru.fk_type IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(ru.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) "
    )
    Page<RecordingUnit> findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(@Param("parentId") Long parentId,
                                                                                                         @Param("fullIdentifier") String fullIdentifier,
                                                                                                         @Param("categoryIds") Long[] categoryIds,
                                                                                                         @Param("global") String global,
                                                                                                         @Param("langCode") String langCode,
                                                                                                         Pageable pageable);

    @Query(
            value = "SELECT ru.* " +
                    "FROM recording_unit ru " +
                    "JOIN action_unit au ON ru.fk_action_unit_id = au.action_unit_id " +
                    "WHERE ru.full_identifier = :fullIdentifier " +
                    "AND ru.fk_institution_id = :institutionId " +
                    "AND au.full_identifier = :actionUnitFullIdentifier",
            nativeQuery = true
    )
    Optional<RecordingUnit> findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier(
            @Param("fullIdentifier") String fullIdentifier,
            @Param("institutionId") Long institutionId,
            @Param("actionUnitFullIdentifier") String actionUnitFullIdentifier
    );


    @Query(
            value = "SELECT COUNT(*) FROM recording_unit WHERE fk_spatial_unit_id = :spatialUnitId",
            nativeQuery = true
    )
    Integer countBySpatialContext(@Param("spatialUnitId") Long spatialUnitId);

    @Query(
            value = "SELECT COUNT(*) FROM recording_unit WHERE fk_action_unit_id = :actionUnitId",
            nativeQuery = true
    )
    Integer countByActionContext(@Param("actionUnitId") Long actionUnitId);

    @Query("select ru.actionUnit.id, count(ru) from RecordingUnit ru where ru.actionUnit.id in :ids group by ru.actionUnit.id")
    List<Object[]> countRecordingUnitsGroupedByActionUnitIds(@Param("ids") List<Long> ids);

    @Query(value = """
    SELECT ru.*
    FROM recording_unit ru
    WHERE ru.fk_institution_id = :institutionId
      AND NOT EXISTS (
          SELECT 1
          FROM recording_unit_hierarchy h
          WHERE h.fk_child_id = ru.recording_unit_id
      )
    ORDER BY ru.creation_time DESC, ru.recording_unit_id DESC
    """, nativeQuery = true)
    List<RecordingUnit> findRootsByInstitution(@Param("institutionId") Long institutionId);

    @Query(value = """
    SELECT ru.*
    FROM recording_unit ru
    JOIN recording_unit_hierarchy h
      ON h.fk_child_id = ru.recording_unit_id
    WHERE ru.fk_institution_id = :institutionId
      AND h.fk_parent_id = :parentId
    ORDER BY ru.creation_time DESC, ru.recording_unit_id DESC
    """, nativeQuery = true)
    List<RecordingUnit> findChildrenByParentAndInstitution(@Param("parentId") Long parentId,
                                                           @Param("institutionId") Long institutionId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT su.* FROM recording_unit su " +
                    "JOIN recording_unit_hierarchy sh ON sh.fk_parent_id = su.recording_unit_id " +
                    "WHERE sh.fk_child_id = :recordingUnitId"
    )
    Set<RecordingUnit> findParentsOf(Long recordingUnitId);


    @Query(value = """
    SELECT ru.*
    FROM recording_unit ru
    WHERE ru.fk_action_unit_id = :actionId
      AND NOT EXISTS (
          SELECT 1
          FROM recording_unit_hierarchy h
          WHERE h.fk_child_id = ru.recording_unit_id
      )
    ORDER BY ru.creation_time DESC, ru.recording_unit_id DESC
    """, nativeQuery = true)
    List<RecordingUnit> findRootsByAction(@Param("actionId") Long actionId);


    @NonNull
    List<RecordingUnit> findByFullIdentifierAndActionUnitId(String fullIdentifier, Long actionUnitId);

    @Query(value = """
    SELECT COUNT(1) > 0
    FROM recording_unit ru
    JOIN recording_unit_hierarchy h ON h.fk_child_id = ru.recording_unit_id
    WHERE ru.fk_institution_id = :institutionId
      AND h.fk_parent_id = :parentId
    """, nativeQuery = true)
    boolean existsChildrenByParentAndInstitution(@Param("parentId") Long parentId,
                                                 @Param("institutionId") Long institutionId);

    @Query(value = """
    SELECT COUNT(1) > 0
    FROM recording_unit ru
    WHERE ru.fk_institution_id = :institutionId
      AND NOT EXISTS (
          SELECT 1
          FROM recording_unit_hierarchy h
          WHERE h.fk_child_id = ru.recording_unit_id
      )
    """, nativeQuery = true)
    boolean existsRootChildrenByInstitution(@Param("institutionId") Long institutionId);

    @Query(value = """
    SELECT COUNT(1) > 0
    FROM recording_unit ru
    WHERE ru.fk_action_unit_id = :actionId
      AND NOT EXISTS (
          SELECT 1
          FROM recording_unit_hierarchy h
          WHERE h.fk_child_id = ru.recording_unit_id
      )
    """, nativeQuery = true)
    boolean existsRootChildrenByAction(Long actionId);

    Page<RecordingUnit> findByCreatedByInstitutionId(Long institutionId, Pageable pageable);

    Optional<RecordingUnit> findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(
            Long actionUnitId,
            OffsetDateTime createdAt);

    Optional<RecordingUnit> findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(
            Long actionUnitId,
            OffsetDateTime createdAt);

    Optional<RecordingUnit> findFirstByActionUnitIdOrderByCreationTimeAsc(Long actionUnitId);  // oldest

    Optional<RecordingUnit> findFirstByActionUnitIdOrderByCreationTimeDesc(Long actionUnitId);

    @Query(nativeQuery = true, value = """
    SELECT ru.*
    FROM recording_unit ru
    JOIN recording_unit_hierarchy ruh ON ru.recording_unit_id = ruh.fk_child_id
    WHERE ruh.fk_parent_id = :parentRecordingUnitId
""")
    List<RecordingUnit> findChildrensOf(Long parentRecordingUnitId);

    @Query(value = """
            WITH RECURSIVE ascend(id) AS (
                SELECT seed FROM unnest(CAST(:seedIds AS BIGINT[])) AS seed
                UNION
                SELECT h.fk_parent_id
                FROM recording_unit_hierarchy h JOIN ascend a ON h.fk_child_id = a.id
            )
            SELECT id FROM ascend
            """, nativeQuery = true)
    List<Long> findAncestorClosure(@Param("seedIds") Long[] seedIds);

}
