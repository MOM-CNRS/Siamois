package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationSide;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * The native queries of the configurable relations: what the write checks and the checks of a restricted rule set
 * ask of {@code relation_link} (docs/modele-relations.md, §4). Every query reads one definition, and the ends are
 * designated by kind and id, as the links store them.
 */
@Repository
public class RelationLinkQueries {

    /** "RELN" in the high bits, the project id in the low ones, apart from the other advisory locks of the code. */
    private static final long LOCK_NAMESPACE = 0x5245_4C4E_0000_0000L;

    private static final String DEFINITION = "definition";
    private static final String PROJECTS = "projects";

    /**
     * Is (:to_kind, :to_id) reachable from (:from_kind, :from_id) along the links of :definition? UNION visits each
     * record once, so the walk ends whatever the links. With :certain_only, uncertain links are not followed.
     */
    private static final String REACHES_SQL = """
            WITH RECURSIVE reached(kind, id) AS (
                SELECT CAST(:from_kind AS varchar(32)), CAST(:from_id AS bigint)
              UNION
                SELECT l.target_kind, l.target_id
                FROM reached r
                JOIN relation_link l ON l.source_kind = r.kind AND l.source_id = r.id
                WHERE l.fk_relation_definition_id = :definition
                  AND (NOT :certain_only OR NOT l.uncertain)
            )
            SELECT EXISTS (SELECT 1 FROM reached WHERE kind = :to_kind AND id = :to_id)
            """;

    /** The links of :definition in :projects that lie on a cycle: those whose target leads back to their source. */
    private static final String LINKS_IN_CYCLES_SQL = """
            WITH RECURSIVE walk(link_id, start_kind, start_id, kind, id) AS (
                SELECT l.relation_link_id, l.source_kind, l.source_id, l.target_kind, l.target_id
                FROM relation_link l
                WHERE l.fk_relation_definition_id = :definition AND l.fk_action_unit_id IN (:projects)
              UNION
                SELECT w.link_id, w.start_kind, w.start_id, l.target_kind, l.target_id
                FROM walk w
                JOIN relation_link l ON l.source_kind = w.kind AND l.source_id = w.id
                WHERE l.fk_relation_definition_id = :definition
            )
            SELECT DISTINCT link_id FROM walk WHERE kind = start_kind AND id = start_id ORDER BY link_id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Serializes, until the end of the transaction, the writes that a check guards (cycle, exclusion, single
     * choice) in one project: two concurrent writes could each pass the check alone and break it together.
     */
    public void lockProject(long projectId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key) IS NOT NULL")
                .setParameter("key", LOCK_NAMESPACE | (projectId & 0xFFFF_FFFFL))
                .getSingleResult();
    }

    /** Whether a path of links of the definition leads from one record to the other. */
    public boolean reaches(long definitionId, RelationEndKind fromKind, long fromId, RelationEndKind toKind, long toId,
                           boolean certainOnly) {
        Object result = entityManager.createNativeQuery(REACHES_SQL)
                .setParameter("from_kind", fromKind.name())
                .setParameter("from_id", fromId)
                .setParameter("to_kind", toKind.name())
                .setParameter("to_id", toId)
                .setParameter(DEFINITION, definitionId)
                .setParameter("certain_only", certainOnly)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    /** Whether one of the definitions already links the two records, in either direction. */
    public boolean linkedEitherWay(Collection<Long> definitionIds, RelationEndKind aKind, long aId,
                                   RelationEndKind bKind, long bId) {
        if (definitionIds.isEmpty()) return false;
        Object result = entityManager.createNativeQuery("""
                        SELECT EXISTS (SELECT 1 FROM relation_link l
                        WHERE l.fk_relation_definition_id IN (:definitions)
                          AND ((l.source_kind = :a_kind AND l.source_id = :a_id AND l.target_kind = :b_kind AND l.target_id = :b_id)
                            OR (l.source_kind = :b_kind AND l.source_id = :b_id AND l.target_kind = :a_kind AND l.target_id = :a_id)))
                        """)
                .setParameter("definitions", definitionIds)
                .setParameter("a_kind", aKind.name()).setParameter("a_id", aId)
                .setParameter("b_kind", bKind.name()).setParameter("b_id", bId)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    /** Whether the definition already links the source to the target, in that direction. */
    public boolean linked(long definitionId, RelationEndKind sourceKind, long sourceId,
                          RelationEndKind targetKind, long targetId) {
        Object result = entityManager.createNativeQuery("""
                        SELECT EXISTS (SELECT 1 FROM relation_link l WHERE l.fk_relation_definition_id = :definition
                          AND l.source_kind = :source_kind AND l.source_id = :source_id
                          AND l.target_kind = :target_kind AND l.target_id = :target_id)
                        """)
                .setParameter(DEFINITION, definitionId)
                .setParameter("source_kind", sourceKind.name()).setParameter("source_id", sourceId)
                .setParameter("target_kind", targetKind.name()).setParameter("target_id", targetId)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private static final String COUNT_AT_EITHER_END_SQL = """
            SELECT count(*) FROM relation_link l WHERE l.fk_relation_definition_id = :definition
              AND ((l.source_kind = :kind AND l.source_id = :id) OR (l.target_kind = :kind AND l.target_id = :id))""";
    private static final String COUNT_AT_SOURCE_SQL = """
            SELECT count(*) FROM relation_link l WHERE l.fk_relation_definition_id = :definition
              AND l.source_kind = :kind AND l.source_id = :id""";
    private static final String COUNT_AT_TARGET_SQL = """
            SELECT count(*) FROM relation_link l WHERE l.fk_relation_definition_id = :definition
              AND l.target_kind = :kind AND l.target_id = :id""";

    /** The projects of an institution that have no rule set of their own. */
    private static final String PROJECTS_READING_INSTITUTION_SQL = """
            SELECT DISTINCT l.fk_action_unit_id FROM relation_link l
            JOIN action_unit au ON au.action_unit_id = l.fk_action_unit_id
            WHERE l.fk_relation_definition_id = :definition
              AND NOT EXISTS (SELECT 1 FROM relation_rule rp WHERE rp.fk_relation_definition_id = :definition
                AND rp.fk_action_unit_id = au.action_unit_id)
              AND au.fk_institution_id = :institution""";
    /** The projects with no rule set of their own nor of their institution. */
    private static final String PROJECTS_READING_PLATFORM_SQL = """
            SELECT DISTINCT l.fk_action_unit_id FROM relation_link l
            JOIN action_unit au ON au.action_unit_id = l.fk_action_unit_id
            WHERE l.fk_relation_definition_id = :definition
              AND NOT EXISTS (SELECT 1 FROM relation_rule rp WHERE rp.fk_relation_definition_id = :definition
                AND rp.fk_action_unit_id = au.action_unit_id)
              AND NOT EXISTS (SELECT 1 FROM relation_rule ri WHERE ri.fk_relation_definition_id = :definition
                AND ri.fk_institution_id = au.fk_institution_id AND ri.fk_action_unit_id IS NULL)""";

    /**
     * How many links of the definition a record has on one side; {@code bothSides} counts it at either end, as a
     * symmetric relation does.
     */
    public long countAt(long definitionId, RelationSide side, boolean bothSides, RelationEndKind kind, long id) {
        String sql;
        if (bothSides) {
            sql = COUNT_AT_EITHER_END_SQL;
        } else if (side == RelationSide.SOURCE) {
            sql = COUNT_AT_SOURCE_SQL;
        } else {
            sql = COUNT_AT_TARGET_SQL;
        }
        Number count = (Number) entityManager.createNativeQuery(sql)
                .setParameter(DEFINITION, definitionId)
                .setParameter("kind", kind.name()).setParameter("id", id)
                .getSingleResult();
        return count.longValue();
    }

    /**
     * The projects that have links of the definition and read the rule set of a scope: the project itself; or the
     * projects of the institution with no rule set of their own; or, for the platform default, the projects with
     * no rule set of their own nor of their institution.
     */
    @SuppressWarnings("unchecked")
    public List<Long> projectsReading(long definitionId, Long institutionId, Long projectId) {
        if (projectId != null) {
            return ((List<Number>) entityManager.createNativeQuery("""
                            SELECT DISTINCT l.fk_action_unit_id FROM relation_link l
                            WHERE l.fk_relation_definition_id = :definition AND l.fk_action_unit_id = :project
                            """)
                    .setParameter(DEFINITION, definitionId).setParameter("project", projectId)
                    .getResultList()).stream().map(Number::longValue).toList();
        }
        var query = entityManager.createNativeQuery(institutionId != null
                        ? PROJECTS_READING_INSTITUTION_SQL : PROJECTS_READING_PLATFORM_SQL)
                .setParameter(DEFINITION, definitionId);
        if (institutionId != null) query.setParameter("institution", institutionId);
        return ((List<Number>) query.getResultList()).stream().map(Number::longValue).toList();
    }

    /** The links of the definition in the projects that lie on a cycle. */
    @SuppressWarnings("unchecked")
    public List<Long> linksInCycles(long definitionId, Collection<Long> projectIds) {
        if (projectIds.isEmpty()) return List.of();
        return ((List<Number>) entityManager.createNativeQuery(LINKS_IN_CYCLES_SQL)
                .setParameter(DEFINITION, definitionId).setParameter(PROJECTS, projectIds)
                .getResultList()).stream().map(Number::longValue).toList();
    }

    /** The links of the definition in the projects whose pair the other definition also links, either way. */
    @SuppressWarnings("unchecked")
    public List<Long> linksSharingAPairWith(long definitionId, long otherDefinitionId, Collection<Long> projectIds) {
        if (projectIds.isEmpty()) return List.of();
        return ((List<Number>) entityManager.createNativeQuery("""
                        SELECT DISTINCT l.relation_link_id FROM relation_link l
                        JOIN relation_link o ON o.fk_relation_definition_id = :other
                         AND ((o.source_kind = l.source_kind AND o.source_id = l.source_id
                               AND o.target_kind = l.target_kind AND o.target_id = l.target_id)
                           OR (o.source_kind = l.target_kind AND o.source_id = l.target_id
                               AND o.target_kind = l.source_kind AND o.target_id = l.source_id))
                        WHERE l.fk_relation_definition_id = :definition AND l.fk_action_unit_id IN (:projects)
                        ORDER BY l.relation_link_id
                        """)
                .setParameter(DEFINITION, definitionId).setParameter("other", otherDefinitionId)
                .setParameter(PROJECTS, projectIds)
                .getResultList()).stream().map(Number::longValue).toList();
    }
}
