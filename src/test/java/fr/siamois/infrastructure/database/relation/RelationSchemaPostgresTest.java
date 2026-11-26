package fr.siamois.infrastructure.database.relation;

import fr.siamois.infrastructure.database.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the database itself guarantees for the configurable relations (changelog 2026-11-26-1): the links of a
 * deleted record go with it, a simple link has no duplicate even between concurrent writes, and the checks and
 * unique indexes of the definitions and rule sets. Runs on PostgreSQL in Docker; skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class RelationSchemaPostgresTest {

    private static long institution;
    private static long person;
    private static long linkProject;
    private static long simpleDefinition;
    private static long complexDefinition;

    private long studyA;
    private long studyB;
    private long studyC;

    @BeforeAll
    static void createFixtures() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            person = insert(c, "INSERT INTO person (username, password) VALUES ('relation-test', 'x') RETURNING person_id");
            institution = insert(c, "INSERT INTO institution (creation_date, identifier, institution_name) "
                    + "VALUES (now(), 'relation-test', 'Relation test') RETURNING institution_id");
            linkProject = insert(c, "INSERT INTO action_unit (fk_created_by, fk_institution_id, validation_status, "
                    + "full_identifier, identifier, name) VALUES (" + person + ", " + institution
                    + ", 'INCOMPLETE', 'LINKS', 'LINKS', 'links') RETURNING action_unit_id");
            simpleDefinition = definition(c, "SIMPLE");
            complexDefinition = definition(c, "COMPLEX");
        }
    }

    @BeforeEach
    void createStudies() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            studyA = study(c);
            studyB = study(c);
            studyC = study(c);
        }
    }

    @Test
    void deletingARecordRemovesItsLinksAtBothEnds() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            link(c, simpleDefinition, studyA, studyB);
            link(c, simpleDefinition, studyC, studyA);
            link(c, simpleDefinition, studyB, studyC);

            execute(c, "DELETE FROM study WHERE study_id = " + studyA);

            assertThat(count(c, "SELECT count(*) FROM relation_link WHERE (source_kind = 'STUDY' AND source_id = "
                    + studyA + ") OR (target_kind = 'STUDY' AND target_id = " + studyA + ")")).isZero();
            assertThat(count(c, "SELECT count(*) FROM relation_link WHERE source_id = " + studyB
                    + " AND target_id = " + studyC)).isEqualTo(1);
        }
    }

    @Test
    void deletingARecordOfAnotherKindWithTheSameIdKeepsTheLinks() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            link(c, simpleDefinition, studyA, studyB);
            long place = insert(c, "INSERT INTO spatial_unit (fk_created_by, fk_institution_id, validation_status, name) "
                    + "VALUES (" + person + ", " + institution + ", 'INCOMPLETE', 'place') RETURNING spatial_unit_id");
            // The link names its ends by kind: a place with the same id as a study is another record.
            execute(c, "UPDATE relation_link SET source_id = " + place + " WHERE source_kind = 'STUDY' AND source_id = "
                    + studyA + " AND target_id = " + studyB);

            execute(c, "DELETE FROM spatial_unit WHERE spatial_unit_id = " + place);

            assertThat(count(c, "SELECT count(*) FROM relation_link WHERE source_kind = 'STUDY' AND source_id = "
                    + place)).isEqualTo(1);
        }
    }

    @Test
    void aSimpleLinkCannotBeStoredTwice() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            link(c, simpleDefinition, studyA, studyB);

            assertThatThrownBy(() -> link(c, simpleDefinition, studyA, studyB))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_relation_link_simple");
        }
    }

    @Test
    void aComplexRelationMayLinkTheSamePairSeveralTimes() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            link(c, complexDefinition, studyA, studyB);
            link(c, complexDefinition, studyA, studyB);

            assertThat(count(c, "SELECT count(*) FROM relation_link WHERE fk_relation_definition_id = "
                    + complexDefinition + " AND source_id = " + studyA + " AND target_id = " + studyB)).isEqualTo(2);
        }
    }

    @Test
    void twoConcurrentWritesOfTheSameSimpleLinkKeepOnlyOne() throws Exception {
        try (Connection first = PostgresTestDatabase.connect(); Connection second = PostgresTestDatabase.connect()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            link(first, simpleDefinition, studyA, studyB);

            // The second insert waits on the first transaction's index entry, then fails once it commits.
            CompletableFuture<Void> concurrent = CompletableFuture.runAsync(() -> {
                try {
                    link(second, simpleDefinition, studyA, studyB);
                    second.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(300);
            assertThat(concurrent).isNotDone();
            first.commit();

            assertThatThrownBy(() -> concurrent.get(10, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                    .rootCause().hasMessageContaining("uk_relation_link_simple");
            second.rollback();
            assertThat(count(first, "SELECT count(*) FROM relation_link WHERE fk_relation_definition_id = "
                    + simpleDefinition + " AND source_id = " + studyA + " AND target_id = " + studyB)).isEqualTo(1);
        }
    }

    @Test
    void aRecordCannotBeLinkedToItself() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            assertThatThrownBy(() -> link(c, complexDefinition, studyA, studyA))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_relation_link_not_self");
        }
    }

    @Test
    void aSymmetricDefinitionUsesOneFieldForBothSides() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            long source = field(c);
            long target = field(c);

            assertThatThrownBy(() -> execute(c, "INSERT INTO relation_definition "
                    + "(fk_source_field_id, fk_target_field_id, kind, is_symmetric) VALUES ("
                    + source + ", " + target + ", 'SIMPLE', true)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_relation_definition_symmetric_fields");
            execute(c, "INSERT INTO relation_definition (fk_source_field_id, fk_target_field_id, kind, is_symmetric) "
                    + "VALUES (" + source + ", " + source + ", 'SIMPLE', true)");
        }
    }

    @Test
    void aDefinitionHasOneRuleSetPerScope() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            long definition = definition(c, "SIMPLE");
            long project = insert(c, "INSERT INTO action_unit (fk_created_by, fk_institution_id, validation_status, "
                    + "full_identifier, identifier, name) VALUES (" + person + ", " + institution
                    + ", 'INCOMPLETE', 'REL-" + definition + "', 'REL-" + definition + "', 'p') RETURNING action_unit_id");

            execute(c, rule(definition, "NULL", "NULL"));
            execute(c, rule(definition, String.valueOf(institution), "NULL"));
            execute(c, rule(definition, String.valueOf(institution), String.valueOf(project)));

            assertThatThrownBy(() -> execute(c, rule(definition, "NULL", "NULL")))
                    .hasMessageContaining("uk_relation_rule_platform");
            assertThatThrownBy(() -> execute(c, rule(definition, String.valueOf(institution), "NULL")))
                    .hasMessageContaining("uk_relation_rule_institution");
            assertThatThrownBy(() -> execute(c, rule(definition, String.valueOf(institution), String.valueOf(project))))
                    .hasMessageContaining("uk_relation_rule_project");
            assertThatThrownBy(() -> execute(c, rule(definition, "NULL", String.valueOf(project))))
                    .hasMessageContaining("ck_relation_rule_project_has_institution");
        }
    }

    @Test
    void deletingAProjectRemovesItsRuleSet() throws SQLException {
        try (Connection c = PostgresTestDatabase.connect()) {
            long definition = definition(c, "SIMPLE");
            long project = insert(c, "INSERT INTO action_unit (fk_created_by, fk_institution_id, validation_status, "
                    + "full_identifier, identifier, name) VALUES (" + person + ", " + institution
                    + ", 'INCOMPLETE', 'DEL-" + definition + "', 'DEL-" + definition + "', 'p') RETURNING action_unit_id");
            execute(c, rule(definition, String.valueOf(institution), String.valueOf(project)));

            execute(c, "DELETE FROM action_unit WHERE action_unit_id = " + project);

            assertThat(count(c, "SELECT count(*) FROM relation_rule WHERE fk_relation_definition_id = " + definition))
                    .isZero();
        }
    }

    private static String rule(long definition, String institutionId, String projectId) {
        return "INSERT INTO relation_rule (fk_relation_definition_id, fk_institution_id, fk_action_unit_id) VALUES ("
                + definition + ", " + institutionId + ", " + projectId + ")";
    }

    private static long definition(Connection c, String kind) throws SQLException {
        return insert(c, "INSERT INTO relation_definition (fk_source_field_id, fk_target_field_id, kind) VALUES ("
                + field(c) + ", " + field(c) + ", '" + kind + "') RETURNING relation_definition_id");
    }

    private static long field(Connection c) throws SQLException {
        return insert(c, "INSERT INTO custom_field (custom_field_id, answer_type, label) "
                + "VALUES (nextval('custom_field_id_seq'), 'RELATION', 'relation') RETURNING custom_field_id");
    }

    private long study(Connection c) throws SQLException {
        return insert(c, "INSERT INTO study (fk_created_by, fk_institution_id, validation_status, identifier) VALUES ("
                + person + ", " + institution + ", 'INCOMPLETE', 'S') RETURNING study_id");
    }

    private static void link(Connection c, long definition, long source, long target) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("INSERT INTO relation_link "
                + "(fk_relation_definition_id, fk_action_unit_id, source_kind, source_id, target_kind, target_id, simple) "
                + "SELECT d.relation_definition_id, " + linkProject + ", 'STUDY', ?, 'STUDY', ?, d.kind = 'SIMPLE' "
                + "FROM relation_definition d WHERE d.relation_definition_id = ?")) {
            statement.setLong(1, source);
            statement.setLong(2, target);
            statement.setLong(3, definition);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static long insert(Connection c, String sql) throws SQLException {
        try (Statement statement = c.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement statement = c.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        return insert(c, sql);
    }
}
