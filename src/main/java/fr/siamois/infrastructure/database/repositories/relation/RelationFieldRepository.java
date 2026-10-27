package fr.siamois.infrastructure.database.repositories.relation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Reads a {@link RelationField}'s values without ever loading a whole collection: the first few
 * of each owner of a page, with each owner's total, in one query; or one owner's values, a page at
 * a time. Values are always in the order of their label (then id, so paging is stable).
 */
@Repository
public class RelationFieldRepository {

    private static final String OWNER_PARAM = "owner";


    /** One value of a relation, and — for a stratigraphic one — what the relation says of it. */
    public record Row(long ownerId, long targetId, @Nullable String label,
                      @Nullable String role, @Nullable Long conceptId, @Nullable String conceptExternalId,
                      @Nullable Boolean asynchronous, @Nullable Boolean conceptDirection, @Nullable Boolean uncertain) {
    }

    /** A value of one of several relations read together, with the name its relation goes by there. */
    public record KindedRow(String kind, Row row) {
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
                .setParameter(OWNER_PARAM, ownerId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        bindSearch(query, search);
        return new ArrayList<>(((List<?>) query.getResultList()).stream()
                .map(result -> toRow((Object[]) result))
                .toList());
    }

    /** How many values an owner has, those whose label contains {@code search}. */
    @Transactional(readOnly = true)
    public long count(RelationField relation, long ownerId, @Nullable String search) {
        Query query = entityManager.createNativeQuery("SELECT count(*) FROM (" + valuesSql(relation) + ") v " // NOSONAR S2077: SQL fragments are the constants of the RelationField enum; values are bound
                        + "WHERE v.owner_id = :owner" + searchClause(search))
                .setParameter(OWNER_PARAM, ownerId);
        bindSearch(query, search);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * One owner's values across several relations at once — each value says which relation it comes
     * from — from {@code offset}, at most {@code limit}, those whose label contains {@code search}.
     *
     * @param kinds     the relations to read, by the name each goes by; their order is the order of the
     *                  {@code kind} sort
     * @param byLabel   sorted by label (then relation) rather than by relation (then label)
     */
    @Transactional(readOnly = true)
    public List<KindedRow> pageAcross(Map<String, RelationField> kinds, long ownerId, int offset, int limit,
                                      @Nullable String search, boolean byLabel, boolean ascending) {
        if (kinds.isEmpty()) return List.of();
        String direction = ascending ? "ASC" : "DESC";
        String order = byLabel
                ? "lower(k.label) " + direction + " NULLS LAST, k.kind_order, k.target_id"
                : "k.kind_order " + direction + ", lower(k.label) NULLS LAST, k.target_id";
        Query query = entityManager.createNativeQuery("SELECT k.* FROM (" + acrossSql(kinds) + ") k " // NOSONAR S2077: SQL fragments are the constants of the RelationField enum and the kinds' fixed names; values are bound
                        + "WHERE k.owner_id = :owner" + searchClause(search, "k")
                        + " ORDER BY " + order)
                .setParameter(OWNER_PARAM, ownerId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        bindSearch(query, search);
        return new ArrayList<>(((List<?>) query.getResultList()).stream()
                .map(result -> {
                    Object[] cols = (Object[]) result;
                    return new KindedRow((String) cols[0], toRow(cols, 2));
                })
                .toList());
    }

    /** How many values an owner has across several relations, those whose label contains {@code search}. */
    @Transactional(readOnly = true)
    public long countAcross(Map<String, RelationField> kinds, long ownerId, @Nullable String search) {
        if (kinds.isEmpty()) return 0;
        Query query = entityManager.createNativeQuery("SELECT count(*) FROM (" + acrossSql(kinds) + ") k " // NOSONAR S2077: SQL fragments are the constants of the RelationField enum and the kinds' fixed names; values are bound
                        + "WHERE k.owner_id = :owner" + searchClause(search, "k"))
                .setParameter(OWNER_PARAM, ownerId);
        bindSearch(query, search);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** The values of each relation, side by side: the kind's name and rank, then the usual columns. */
    private static String acrossSql(Map<String, RelationField> kinds) {
        List<String> selects = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, RelationField> kind : kinds.entrySet()) {
            selects.add("SELECT '" + kind.getKey().replace("'", "''") + "' AS kind, " + rank++ + " AS kind_order, v.* FROM ("
                    + valuesSql(kind.getValue()) + ") v");
        }
        return String.join(" UNION ALL ", selects);
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
        return searchClause(search, "v");
    }

    private static String searchClause(@Nullable String search, String alias) {
        return search == null || search.isBlank() ? "" : " AND lower(" + alias + ".label) LIKE :search";
    }

    private static void bindSearch(Query query, @Nullable String search) {
        if (search != null && !search.isBlank()) {
            query.setParameter("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
    }

    private static Row toRow(Object[] cols) {
        return toRow(cols, 0);
    }

    /** The row held by the nine columns that start at {@code from}. */
    private static Row toRow(Object[] cols, int from) {
        return new Row(
                ((Number) cols[from]).longValue(),
                ((Number) cols[from + 1]).longValue(),
                (String) cols[from + 2],
                (String) cols[from + 3],
                cols[from + 4] == null ? null : ((Number) cols[from + 4]).longValue(),
                (String) cols[from + 5],
                (Boolean) cols[from + 6],
                (Boolean) cols[from + 7],
                (Boolean) cols[from + 8]);
    }
}
