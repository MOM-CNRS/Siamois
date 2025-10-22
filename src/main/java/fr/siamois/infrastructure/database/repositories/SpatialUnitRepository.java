package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SpatialUnitRepository extends JpaRepository<SpatialUnit, Long>, RevisionRepository<SpatialUnit, Long, Long> {

    @Query(
            value = "SELECT COUNT(*) FROM spatial_hierarchy WHERE fk_parent_id = :parentId",
            nativeQuery = true
    )
    long countChildrenByParentId(@Param("parentId") Long parentId);

    @Query(
            value = "SELECT COUNT(*) FROM spatial_hierarchy WHERE fk_child_id = :childId",
            nativeQuery = true
    )
    long countParentsByChildId(@Param("childId") Long childId);



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
                    "    su.*, " +
                    "    p.name as p_name, " +
                    "    p.lastname as p_lastname, " +
                    "    rl.label_value AS c_label " +
                    "FROM spatial_unit su " +
                    "LEFT JOIN person p ON su.fk_author_id = p.person_id " +
                    "LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE su.fk_institution_id = :institutionId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR su.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR su.fk_concept_category_id IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
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
                    "SELECT count(su.*) " +
                    "FROM spatial_unit su " +
                    "LEFT JOIN person p ON su.fk_author_id = p.person_id " +
                    "LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id " +
                    "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE su.fk_institution_id = :institutionId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR su.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR su.fk_concept_category_id IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  " +
                    "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))" +
                    "                                     OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')))"
    )
    Page<SpatialUnit> findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(@Param("institutionId") Long institutionId,
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
                    "    su.*, " +
                    "    p.name as p_name, " +
                    "    p.lastname as p_lastname, " +
                    "    rl.label_value AS c_label " +
                    "FROM spatial_unit su " +
                    "         LEFT JOIN person p ON su.fk_author_id = p.person_id " +
                    "         LEFT JOIN spatial_hierarchy sh ON su.spatial_unit_id = sh.fk_child_id " +
                    "         LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id " +
                    "         LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE sh.fk_parent_id = :parentId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR su.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR su.fk_concept_category_id IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')) OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')))",
            countQuery = "WITH ranked_labels AS ( " +
                    "SELECT " +
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
                    "    count(su.*) " +
                    "FROM spatial_unit su " +
                    "         LEFT JOIN spatial_hierarchy sh ON su.spatial_unit_id = sh.fk_child_id " +
                    "         LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id " +
                    "         LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE sh.fk_parent_id = :parentId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR su.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR su.fk_concept_category_id IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')) OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')))"
    )
    Page<SpatialUnit> findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(@Param("parentId") Long parentId,
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
                    "    su.*, " +
                    "    p.name as p_name, " +
                    "    p.lastname as p_lastname, " +
                    "    rl.label_value AS c_label " +
                    "FROM spatial_unit su " +
                    "         LEFT JOIN person p ON su.fk_author_id = p.person_id " +
                    "         LEFT JOIN spatial_hierarchy sh ON su.spatial_unit_id = sh.fk_parent_id " +
                    "         LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id " +
                    "         LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE sh.fk_child_id = :childId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR su.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR su.fk_concept_category_id IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')) OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')))",
            countQuery = "WITH ranked_labels AS ( " +
                    "SELECT " +
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
                    "    count(su.*) " +
                    "FROM spatial_unit su " +
                    "         LEFT JOIN spatial_hierarchy sh ON su.spatial_unit_id = sh.fk_parent_id " +
                    "         LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id " +
                    "         LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 " +
                    "WHERE sh.fk_child_id = :parentId " +
                    "  AND (CAST(:name AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:name AS TEXT), '%'))) " +
                    "  AND (CAST(:personIds AS BIGINT[]) IS NULL OR su.fk_author_id IN (:personIds)) " +
                    "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR su.fk_concept_category_id IN (:categoryIds)) " +
                    "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(su.name) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')) OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%')))"
    )
    Page<SpatialUnit> findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(@Param("childId") Long childId,
                                                                                            @Param("name") String name,
                                                                                            @Param("categoryIds") Long[] categoryIds,
                                                                                            @Param("personIds") Long[] personIds,
                                                                                            @Param("global") String global,
                                                                                            @Param("langCode") String langCode,
                                                                                            Pageable pageable);

    @Query(
            nativeQuery = true,
            value = "SELECT su.* FROM spatial_unit su WHERE su.fk_institution_id = :institutionId"
    )
    List<SpatialUnit> findAllOfInstitution(Long institutionId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO spatial_hierarchy(fk_parent_id, fk_child_id) " +
                    "VALUES (:parentId, :childId)"
    )
    void addParentToSpatialUnit(Long childId, Long parentId);


    @Query(
            nativeQuery = true,
            value = "SELECT su.* FROM spatial_unit su " +
                    "WHERE UPPER(su.name) = UPPER(:spatialUnitName) AND su.fk_institution_id = :institutionId"
    )
    Optional<SpatialUnit> findByNameAndInstitution(String spatialUnitName, Long institutionId);

    Optional<SpatialUnit> findByArk(@NotNull Ark ark);

    List<SpatialUnit> findAllByArkIsNullAndCreatedByInstitution(@NotNull Institution createdByInstitution);

    long countByCreatedByInstitution(Institution institution);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT su.* FROM spatial_unit su " +
                    "JOIN spatial_hierarchy sh ON sh.fk_child_id = su.spatial_unit_id " +
                    "WHERE sh.fk_parent_id = :spatialUnitId"
    )
    Set<SpatialUnit> findChildrensOf(Long spatialUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT su.* FROM spatial_unit su " +
                    "JOIN spatial_hierarchy sh ON sh.fk_parent_id = su.spatial_unit_id " +
                    "WHERE sh.fk_child_id = :spatialUnitId"
    )
    Set<SpatialUnit> findParentsOf(Long spatialUnitId);
}

