package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationLink;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The lookups a link write needs, over the per-kind ends of {@code relation_link}. The property and column
 * names come from {@link RelationEntityKind} only, never from a request, so the queries are built from
 * constants and bind every id.
 */
@Repository
public class RelationLinkQueries {

    @PersistenceContext
    private EntityManager entityManager;

    /** The links of the given definitions between two entities, in either direction. */
    @SuppressWarnings("unchecked")
    public List<RelationLink> linksBetween(Collection<RelationDefinition> definitions,
                                           RelationEntityKind kindA, long idA, RelationEntityKind kindB, long idB) {
        StringBuilder jpql = new StringBuilder("select l from RelationLink l where l.definition in :definitions and ((")
                .append(end("source", kindA)).append(" = :a and ").append(end("target", kindB)).append(" = :b)");
        if (kindB.canBeSource() && kindA != null) {
            jpql.append(" or (").append(end("source", kindB)).append(" = :b and ").append(end("target", kindA)).append(" = :a)");
        }
        jpql.append(')');
        Query query = entityManager.createQuery(jpql.toString())
                .setParameter("definitions", definitions)
                .setParameter("a", idA)
                .setParameter("b", idB);
        return query.getResultList();
    }

    /** Every link of an entity, under any definition, from either end. */
    @SuppressWarnings("unchecked")
    public List<RelationLink> linksOfEntity(RelationEntityKind kind, long id) {
        StringBuilder jpql = new StringBuilder("select l from RelationLink l where ")
                .append(end("target", kind)).append(" = :id");
        if (kind.canBeSource()) {
            jpql.append(" or ").append(end("source", kind)).append(" = :id");
        }
        return entityManager.createQuery(jpql.toString()).setParameter("id", id).getResultList();
    }

    /** The links of the given definitions (by code) whose source end is one of {@code ids}, entities of {@code kind}. */
    @SuppressWarnings("unchecked")
    public List<RelationLink> linksFromSources(Collection<String> definitionCodes, RelationEntityKind kind, Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return entityManager.createQuery("select l from RelationLink l join fetch l.definition d where d.code in :codes and "
                        + end("source", kind) + " in :ids")
                .setParameter("codes", definitionCodes)
                .setParameter("ids", ids)
                .getResultList();
    }

    /** The boolean answers of a field of the links' own form, by link id (a link without an answer is absent). */
    public Map<Long, Boolean> booleanAnswers(Collection<Long> linkIds, long fieldId) {
        Map<Long, Boolean> out = new HashMap<>();
        if (linkIds.isEmpty()) return out;
        List<?> rows = entityManager.createQuery("select fca.relationLink.id, a.value from CustomFieldAnswerBoolean a "
                        + "join a.formConfigAnswer fca where a.customField.id = :field and fca.relationLink.id in :ids")
                .setParameter("field", fieldId)
                .setParameter("ids", linkIds)
                .getResultList();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            if (cols[1] != null) out.put(((Number) cols[0]).longValue(), (Boolean) cols[1]);
        }
        return out;
    }

    /** How many links of one definition an entity has as the source end. */
    public long countAsSource(RelationDefinition definition, RelationEntityKind kind, long id) {
        return (Long) entityManager.createQuery("select count(l) from RelationLink l where l.definition = :definition and "
                        + end("source", kind) + " = :id")
                .setParameter("definition", definition)
                .setParameter("id", id)
                .getSingleResult();
    }

    /** All the links of an entity under one definition, from either end. */
    @SuppressWarnings("unchecked")
    public List<RelationLink> linksOf(RelationDefinition definition, RelationEntityKind kind, long id) {
        StringBuilder jpql = new StringBuilder("select l from RelationLink l where l.definition = :definition and (")
                .append(end("target", kind)).append(" = :id");
        if (kind.canBeSource()) {
            jpql.append(" or ").append(end("source", kind)).append(" = :id");
        }
        jpql.append(')');
        return entityManager.createQuery(jpql.toString())
                .setParameter("definition", definition)
                .setParameter("id", id)
                .getResultList();
    }

    /**
     * Whether {@code toId} can be reached from {@code fromId} by following links source → target, within the given
     * definitions (a recursive walk that stops on a node it already saw, so an existing cycle does not loop).
     */
    public boolean reaches(Collection<Long> definitionIds, RelationEntityKind kind, long fromId, long toId) {
        String suffix = kind.columnSuffix();
        String sql = "WITH RECURSIVE walk(id) AS (SELECT CAST(:from AS bigint) "
                + "UNION SELECT l.fk_target_" + suffix + "_id FROM relation_link l JOIN walk w ON l.fk_source_" + suffix + "_id = w.id "
                + "WHERE l.fk_relation_definition_id IN (:definitions) AND l.fk_target_" + suffix + "_id IS NOT NULL) "
                + "SELECT EXISTS (SELECT 1 FROM walk WHERE id = :to)";
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("from", fromId)
                .setParameter("to", toId)
                .setParameter("definitions", definitionIds)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private static String end(String side, RelationEntityKind kind) {
        return "l." + side + kind.propertySuffix() + ".id";
    }
}
