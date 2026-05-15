package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.specimen.Specimen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.regex.Pattern;

public class SpecimenRepositoryImpl implements SpecimenRepositoryCustom {

    private static final Pattern SAFE_ORDER_BY = Pattern.compile(
            "^((s\\.(creation_time|full_identifier|specimen_id)|c_label) (ASC|DESC))(, (s\\.(creation_time|full_identifier|specimen_id)|c_label) (ASC|DESC))*$",
            Pattern.CASE_INSENSITIVE);

    private static final String RANKED_LABELS_CTE = "WITH ranked_labels AS ( "
            + "    SELECT "
            + "        l.fk_concept_id, "
            + "        l.label_value, "
            + "        l.lang_code, "
            + "        ROW_NUMBER() OVER ( "
            + "            PARTITION BY l.fk_concept_id "
            + "            ORDER BY  "
            + "                CASE  "
            + "                    WHEN l.lang_code = :langCode THEN 1 "
            + "                    WHEN l.lang_code = 'en' THEN 2 "
            + "                    ELSE 3 "
            + "                END "
            + "        ) AS rank "
            + "    FROM label l "
            + ") ";

    private static final String FROM_WHERE = "FROM specimen s "
            + "LEFT JOIN concept c ON s.fk_specimen_type = c.concept_id "
            + "LEFT JOIN ranked_labels rl ON c.concept_id = rl.fk_concept_id AND rl.rank = 1 "
            + "WHERE s.fk_institution_id = :institutionId "
            + "  AND s.fk_recording_unit_id = :recordingUnitId "
            + "  AND (CAST(:fullIdentifier AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:fullIdentifier AS TEXT), '%'))) "
            + "  AND (CAST(:categoryIds AS BIGINT[]) IS NULL OR s.fk_specimen_type IN (:categoryIds)) "
            + "  AND (CAST(:global AS TEXT) IS NULL OR LOWER(s.full_identifier) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))  "
            + "                                     OR LOWER(rl.label_value) LIKE LOWER(CONCAT('%', CAST(:global AS TEXT), '%'))) ";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Specimen> findAllByInstitutionAndRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
            Long institutionId,
            Long recordingUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode,
            String orderByClause,
            Pageable pageable) {
        String orderBy = validateOrderBy(orderByClause);

        Query countQuery = entityManager.createNativeQuery(
                RANKED_LABELS_CTE + "SELECT count(s) " + FROM_WHERE);
        bindFilterParams(countQuery, institutionId, recordingUnitId, fullIdentifier, categoryIds, global, langCode);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query dataQuery = entityManager.createNativeQuery(
                RANKED_LABELS_CTE
                        + "SELECT s.*, rl.label_value AS c_label "
                        + FROM_WHERE
                        + " ORDER BY " + orderBy,
                Specimen.class);
        bindFilterParams(dataQuery, institutionId, recordingUnitId, fullIdentifier, categoryIds, global, langCode);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Specimen> content = dataQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private static String validateOrderBy(String orderByClause) {
        if (orderByClause == null || !SAFE_ORDER_BY.matcher(orderByClause.trim()).matches()) {
            return SpecimenFindSortSql.fromApiSortParam(null);
        }
        return orderByClause.trim();
    }

    private static void bindFilterParams(
            Query query,
            Long institutionId,
            Long recordingUnitId,
            String fullIdentifier,
            Long[] categoryIds,
            String global,
            String langCode) {
        query.setParameter("institutionId", institutionId);
        query.setParameter("recordingUnitId", recordingUnitId);
        query.setParameter("fullIdentifier", fullIdentifier);
        query.setParameter("categoryIds", categoryIds);
        query.setParameter("global", global);
        query.setParameter("langCode", langCode);
    }
}
