package fr.siamois.infrastructure.database.repositories.relation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a {@link RelationField}'s values without ever loading a whole collection: the first few
 * of each owner of a page, with each owner's total, in one query; or one owner's values, a page at
 * a time. Values are always in the order of their label (then id, so paging is stable).
 */
@Repository
public class RelationFieldRepository {

    /** One value of a relation, and — for a stratigraphic one — what the relation says of it. */
    public record Row(long ownerId, long targetId, @Nullable String label,
                      @Nullable String role, @Nullable Long conceptId, @Nullable String conceptExternalId,
                      @Nullable Boolean asynchronous, @Nullable Boolean conceptDirection, @Nullable Boolean uncertain) {
    }

    /** An owner's first values, and how many it has in all. */
    public record Preview(List<Row> rows, long total) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * The first {@code limit} values of each owner, and each owner's total. An owner with no value
     * is absent from the result. {@code limit} 0 still counts: only the totals come back.
     */
    @Transactional(readOnly = true)
    public Map<Long, Preview> previews(RelationField relation, Collection<Long> ownerIds, int limit) {
        Map<Long, Preview> out = new LinkedHashMap<>();
        if (ownerIds == null || ownerIds.isEmpty()) return out;
        String sql = "SELECT owner_id, target_id, label, role, concept_id, concept_external_id, asynchronous, "
                + "concept_direction, uncertain, total FROM ("
                + "SELECT v.*, row_number() OVER (PARTITION BY v.owner_id ORDER BY lower(v.label) NULLS LAST, v.target_id) AS rn, "
                + "count(*) OVER (PARTITION BY v.owner_id) AS total FROM (" + valuesSql(relation) + ") v "
                + "WHERE v.owner_id IN (:owners)) ranked "
                + "WHERE rn <= :limit ORDER BY owner_id, rn";
        Query query = entityManager.createNativeQuery(sql) // NOSONAR S2077: SQL fragments are the constants of the RelationField enum; values are bound
                .setParameter("owners", ownerIds)
                // One row per owner even for limit 0: that row is where its total is read from.
                .setParameter("limit", Math.max(limit, 1));
        Map<Long, List<Row>> rows = new LinkedHashMap<>();
        Map<Long, Long> totals = new LinkedHashMap<>();
        for (Object result : query.getResultList()) {
            Object[] cols = (Object[]) result;
            Row row = toRow(cols);
            totals.put(row.ownerId(), ((Number) cols[9]).longValue());
            if (limit > 0) rows.computeIfAbsent(row.ownerId(), k -> new ArrayList<>()).add(row);
        }
        totals.forEach((owner, total) -> out.put(owner, new Preview(rows.getOrDefault(owner, List.of()), total)));
        return out;
    }

    /** One owner's values from {@code offset}, at most {@code limit}, those whose label contains {@code search}. */
    @Transactional(readOnly = true)
    public List<Row> page(RelationField relation, long ownerId, int offset, int limit,
                          @Nullable String search, boolean ascending) {
        String direction = ascending ? "ASC" : "DESC";
        Query query = entityManager.createNativeQuery("SELECT v.* FROM (" + valuesSql(relation) + ") v " // NOSONAR S2077: SQL fragments are the constants of the RelationField enum; values are bound
                        + "WHERE v.owner_id = :owner" + searchClause(search)
                        + " ORDER BY lower(v.label) " + direction + " NULLS LAST, v.target_id " + direction)
                .setParameter("owner", ownerId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        bindSearch(query, search);
        List<Row> out = new ArrayList<>();
        for (Object result : query.getResultList()) {
            out.add(toRow((Object[]) result));
        }
        return out;
    }

    /** How many values an owner has, those whose label contains {@code search}. */
    @Transactional(readOnly = true)
    public long count(RelationField relation, long ownerId, @Nullable String search) {
        Query query = entityManager.createNativeQuery("SELECT count(*) FROM (" + valuesSql(relation) + ") v " // NOSONAR S2077: SQL fragments are the constants of the RelationField enum; values are bound
                        + "WHERE v.owner_id = :owner" + searchClause(search))
                .setParameter("owner", ownerId);
        bindSearch(query, search);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** Every (owner, target) pair of the relation, with its target's label and the relation's own columns. */
    private static String valuesSql(RelationField relation) {
        return "SELECT p.owner_id, p.target_id, " + relation.targetLabel() + " AS label, p.role, p.concept_id, "
                + "c.external_id AS concept_external_id, p.asynchronous, p.concept_direction, p.uncertain "
                + "FROM (" + relation.pairsSql() + ") p "
                + "JOIN " + relation.targetTable() + " t ON t." + relation.targetKey() + " = p.target_id "
                + "LEFT JOIN concept c ON c.concept_id = p.concept_id";
    }

    private static String searchClause(@Nullable String search) {
        return search == null || search.isBlank() ? "" : " AND lower(v.label) LIKE :search";
    }

    private static void bindSearch(Query query, @Nullable String search) {
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    private static Row toRow(Object[] cols) {
        return new Row(
                ((Number) cols[0]).longValue(),
                ((Number) cols[1]).longValue(),
                (String) cols[2],
                (String) cols[3],
                cols[4] == null ? null : ((Number) cols[4]).longValue(),
                (String) cols[5],
                (Boolean) cols[6],
                (Boolean) cols[7],
                (Boolean) cols[8]);
    }
}
