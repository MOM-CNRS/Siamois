package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.specimen.Specimen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class SpecimenRepositoryImpl implements SpecimenRepositoryCustom {

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
    public static final String SELECT_S_RL_LABEL_VALUE_AS_C_LABEL = "SELECT s.*, rl.label_value AS c_label ";

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
        SpecimenFindSortSql.NativeOrderBy sort = SpecimenFindSortSql.NativeOrderBy.fromClause(orderByClause);

        Query countQuery = entityManager.createNativeQuery(
                RANKED_LABELS_CTE + "SELECT count(s) " + FROM_WHERE);
        bindFilterParams(countQuery, institutionId, recordingUnitId, fullIdentifier, categoryIds, global, langCode);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query dataQuery = entityManager.createNativeQuery(buildRankedSpecimenSelectSql(sort), Specimen.class);
        bindFilterParams(dataQuery, institutionId, recordingUnitId, fullIdentifier, categoryIds, global, langCode);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Specimen> content = dataQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Requête SELECT avec ORDER BY fixe (whitelist {@link SpecimenFindSortSql.NativeOrderBy}) — pas de SQL dynamique utilisateur.
     */
    private static String buildRankedSpecimenSelectSql(SpecimenFindSortSql.NativeOrderBy sort) {
        return switch (sort) {
            case DEFAULT, CREATION_TIME_DESC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY s.creation_time DESC, s.specimen_id ASC";
            case CREATION_TIME_ASC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY s.creation_time ASC, s.specimen_id ASC";
            case FULL_IDENTIFIER_ASC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY s.full_identifier ASC, s.specimen_id ASC";
            case FULL_IDENTIFIER_DESC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY s.full_identifier DESC, s.specimen_id ASC";
            case SPECIMEN_ID_ASC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY s.specimen_id ASC";
            case SPECIMEN_ID_DESC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY s.specimen_id DESC";
            case LABEL_ASC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY c_label ASC, s.specimen_id ASC";
            case LABEL_DESC -> RANKED_LABELS_CTE
                    + SELECT_S_RL_LABEL_VALUE_AS_C_LABEL
                    + FROM_WHERE
                    + " ORDER BY c_label DESC, s.specimen_id ASC";
        };
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
