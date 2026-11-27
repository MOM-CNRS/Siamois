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

    /** The hierarchy and stratigraphic relations that join two of the units bound to {@code :ids}, from the more recent (or parent) to the older. */
    private static final String LINKS_AMONG_SQL = "SELECT 'hierarchy' AS kind, h.fk_parent_id AS from_id, h.fk_child_id AS to_id, "
            + "CAST(NULL AS BIGINT) AS concept_id, CAST(NULL AS VARCHAR) AS concept_external_id, CAST(NULL AS BOOLEAN) AS uncertain "
            + "FROM recording_unit_hierarchy h WHERE h.fk_parent_id IN (:ids) AND h.fk_child_id IN (:ids) "
            + "UNION ALL "
            + "SELECT CASE WHEN d.fk_source_field_id = " + RelationField.Stratigraphy.SYNCHRONY_FIELD
            + " THEN 'synchronous' ELSE 'stratigraphy' END, l.source_id, l.target_id, "
            + "l.fk_qualifier_concept_id, c.external_id, l.uncertain "
            + "FROM relation_link l "
            + "JOIN relation_definition d ON d.relation_definition_id = l.fk_relation_definition_id "
            + "AND d.fk_source_field_id IN (" + RelationField.Stratigraphy.ORDER_SOURCE_FIELD + ", "
            + RelationField.Stratigraphy.SYNCHRONY_FIELD + ") "
            + "LEFT JOIN concept c ON c.concept_id = l.fk_qualifier_concept_id "
            + "WHERE l.source_kind = 'RECORDING_UNIT' AND l.target_kind = 'RECORDING_UNIT' "
            + "AND l.source_id IN (:ids) AND l.target_id IN (:ids)";


    /** One value of a relation, and — for a stratigraphic one — what the relation says of it. */
    public record Row(long ownerId, long targetId, @Nullable String label,
                      @Nullable String role, @Nullable Long conceptId, @Nullable String conceptExternalId,
                      @Nullable Boolean asynchronous, @Nullable Boolean conceptDirection, @Nullable Boolean uncertain,
                      @Nullable Long linkId) {

        public Row(long ownerId, long targetId, @Nullable String label, @Nullable String role, @Nullable Long conceptId,
                   @Nullable String conceptExternalId, @Nullable Boolean asynchronous, @Nullable Boolean conceptDirection,
                   @Nullable Boolean uncertain) {
            this(ownerId, targetId, label, role, conceptId, conceptExternalId, asynchronous, conceptDirection, uncertain, null);
        }
    }

    /** A value of one of several relations read together, with the name its relation goes by there. */
    public record KindedRow(String kind, Row row) {
    }

    /**
     * A relation between two units of a given set, read from the more recent (or parent) unit to the older (or child) one.
     * {@code kind} is {@code hierarchy}, {@code stratigraphy} or {@code synchronous}; the type and the uncertainty are those of a
     * stratigraphic relationship, null for the others.
     */
    public record LinkRow(String kind, long fromId, long toId, @Nullable Long conceptId, @Nullable String conceptExternalId,
                          @Nullable Boolean uncertain) {
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
    public Map<Long, Preview> previews(RelationSource relation, Collection<Long> ownerIds, int limit) {
        Map<Long, Preview> out = new LinkedHashMap<>();
        if (ownerIds == null || ownerIds.isEmpty()) return out;
        String sql = "SELECT owner_id, target_id, label, role, concept_id, concept_external_id, asynchronous, "
                + "concept_direction, uncertain, link_id, total FROM ("
                + "SELECT v.*, row_number() OVER (PARTITION BY v.owner_id ORDER BY lower(v.label) NULLS LAST, v.target_id) AS rn, "
                + "count(*) OVER (PARTITION BY v.owner_id) AS total FROM (" + valuesSql(relation) + ") v "
                + "WHERE v.owner_id IN (:owners)) ranked "
                + "WHERE rn <= :limit ORDER BY owner_id, rn";
        Query query = entityManager.createNativeQuery(sql) // NOSONAR S2077: SQL fragments are built from constants and numeric ids (RelationSource); values are bound
                .setParameter("owners", ownerIds)
                // One row per owner even for limit 0: that row is where its total is read from.
                .setParameter("limit", Math.max(limit, 1));
        Map<Long, List<Row>> rows = new LinkedHashMap<>();
        Map<Long, Long> totals = new LinkedHashMap<>();
        for (Object result : query.getResultList()) {
            Object[] cols = (Object[]) result;
            Row row = toRow(cols);
            totals.put(row.ownerId(), ((Number) cols[10]).longValue());
            if (limit > 0) rows.computeIfAbsent(row.ownerId(), k -> new ArrayList<>()).add(row);
        }
        totals.forEach((owner, total) -> out.put(owner, new Preview(rows.getOrDefault(owner, List.of()), total)));
        return out;
    }

    /**
     * The hierarchy and stratigraphic relations that join two units of {@code unitIds}. A stratigraphic one goes from its
     * second (more recent) unit to its first; a synchronous one from its first to its second.
     */
    @Transactional(readOnly = true)
    public List<LinkRow> linksAmong(Collection<Long> unitIds) {
        if (unitIds == null || unitIds.size() < 2) return List.of();
        Query query = entityManager.createNativeQuery(LINKS_AMONG_SQL).setParameter("ids", unitIds);
        return ((List<?>) query.getResultList()).stream().map(result -> {
            Object[] cols = (Object[]) result;
            return new LinkRow((String) cols[0], ((Number) cols[1]).longValue(), ((Number) cols[2]).longValue(),
                    cols[3] == null ? null : ((Number) cols[3]).longValue(), (String) cols[4], (Boolean) cols[5]);
        }).toList();
    }

    /** One owner's values from {@code offset}, at most {@code limit}, those whose label contains {@code search}. */
    @Transactional(readOnly = true)
    public List<Row> page(RelationSource relation, long ownerId, int offset, int limit,
                          @Nullable String search, boolean ascending) {
        String direction = ascending ? "ASC" : "DESC";
        Query query = entityManager.createNativeQuery("SELECT v.* FROM (" + valuesSql(relation) + ") v " // NOSONAR S2077: SQL fragments are built from constants and numeric ids (RelationSource); values are bound
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
    public long count(RelationSource relation, long ownerId, @Nullable String search) {
        Query query = entityManager.createNativeQuery("SELECT count(*) FROM (" + valuesSql(relation) + ") v " // NOSONAR S2077: SQL fragments are built from constants and numeric ids (RelationSource); values are bound
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
    public List<KindedRow> pageAcross(Map<String, ? extends RelationSource> kinds, long ownerId, int offset, int limit,
                                      @Nullable String search, boolean byLabel, boolean ascending) {
        if (kinds.isEmpty()) return List.of();
        String direction = ascending ? "ASC" : "DESC";
        String order = byLabel
                ? "lower(k.label) " + direction + " NULLS LAST, k.kind_order, k.target_id"
                : "k.kind_order " + direction + ", lower(k.label) NULLS LAST, k.target_id";
        Query query = entityManager.createNativeQuery("SELECT k.* FROM (" + acrossSql(kinds) + ") k " // NOSONAR S2077: SQL fragments are built from constants and numeric ids (RelationSource) and the kinds' fixed names; values are bound
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
    public long countAcross(Map<String, ? extends RelationSource> kinds, long ownerId, @Nullable String search) {
        if (kinds.isEmpty()) return 0;
        Query query = entityManager.createNativeQuery("SELECT count(*) FROM (" + acrossSql(kinds) + ") k " // NOSONAR S2077: SQL fragments are built from constants and numeric ids (RelationSource) and the kinds' fixed names; values are bound
                        + "WHERE k.owner_id = :owner" + searchClause(search, "k"))
                .setParameter(OWNER_PARAM, ownerId);
        bindSearch(query, search);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** The values of each relation, side by side: the kind's name and rank, then the usual columns. */
    private static String acrossSql(Map<String, ? extends RelationSource> kinds) {
        List<String> selects = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, ? extends RelationSource> kind : kinds.entrySet()) {
            selects.add("SELECT '" + kind.getKey().replace("'", "''") + "' AS kind, " + rank++ + " AS kind_order, v.* FROM ("
                    + valuesSql(kind.getValue()) + ") v");
        }
        return String.join(" UNION ALL ", selects);
    }

    private static String valuesSql(RelationSource relation) {
        return relation.valuesSql();
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

    /** The row held by the ten columns that start at {@code from}. */
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
                (Boolean) cols[from + 8],
                cols[from + 9] == null ? null : ((Number) cols[from + 9]).longValue());
    }
}
