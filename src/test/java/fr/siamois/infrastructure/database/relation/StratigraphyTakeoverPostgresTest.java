package fr.siamois.infrastructure.database.relation;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stratigraphy takeover (changelog 2026-11-26-2, rules of docs/reprise-stratigraphie.md, §6), on PostgreSQL: a
 * database migrated up to the changeSets of the takeover, old relationships covering each case, then the takeover.
 * Its own container, since the shared one is already migrated whole. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class StratigraphyTakeoverPostgresTest {

    private static final String TAKEOVER_FILE = "2026-11-26-2.changelog.xml";

    @Container
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    private static final long[] UNITS = new long[9];
    private static long recouvre;
    private static long recouverte;
    private static long contemporain;
    private static long sur;
    private static long sous;
    private static long adosse;

    @BeforeAll
    static void migrateThenTakeOver() throws Exception {
        try (Connection c = connect()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (Liquibase liquibase = new Liquibase("changelog/db.changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                List<ChangeSet> unrun = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression());
                int before = (int) unrun.stream().takeWhile(cs -> !cs.getFilePath().endsWith(TAKEOVER_FILE)).count();
                liquibase.update(before, new Contexts(), new LabelExpression());
                createOldStratigraphy(c);
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    @Test
    void everyOldRelationshipButTheHierarchyOnesIsTakenOver() throws SQLException {
        assertThat(count("SELECT count(*) FROM relation_link")).isEqualTo(8);
        // 2026-11-27-1 then drops the old table and its history
        assertThat(count("SELECT count(*) FROM pg_tables WHERE tablename IN "
                + "('stratigraphic_relationship', 'stratigraphic_relationship_aud')")).isZero();
    }

    @Test
    void anOrderLinkGoesFromThePosteriorUnitWithThePosteriorReadingOfItsPair() throws SQLException {
        // Stored as "unit 1 est recouverte par unit 2", or as "unit 2 recouvre unit 1": both become unit 2 recouvre unit 1.
        assertThat(qualifierOfOrder(2, 1)).isEqualTo(recouvre);
        assertThat(qualifierOfOrder(3, 2)).isEqualTo(recouvre);
        // The old shared list: "sous", read from the anterior unit, becomes "sur" from the posterior one.
        assertThat(qualifierOfOrder(8, 7)).isEqualTo(sur);
        // A concept with no pair is kept as it was.
        assertThat(qualifierOfOrder(8, 2)).isEqualTo(adosse);
    }

    @Test
    void conceptsThatOnlyRestateTheRelationAreDropped() throws SQLException {
        assertThat(qualifierOfOrder(4, 3)).isNull();
        assertThat(count("SELECT count(*) FROM relation_link l JOIN relation_definition d "
                + "ON d.relation_definition_id = l.fk_relation_definition_id WHERE d.fk_source_field_id = -330 "
                + "AND l.source_id = " + UNITS[6] + " AND l.target_id = " + UNITS[7]
                + " AND l.fk_qualifier_concept_id IS NULL")).isEqualTo(1);
    }

    @Test
    void aSynchronousPairIsOrderedAndKeepsItsQualifier() throws SQLException {
        assertThat(count("SELECT count(*) FROM relation_link l JOIN relation_definition d "
                + "ON d.relation_definition_id = l.fk_relation_definition_id WHERE d.fk_source_field_id = -330 "
                + "AND l.source_id = " + UNITS[5] + " AND l.target_id = " + UNITS[6]
                + " AND l.fk_qualifier_concept_id = " + contemporain)).isEqualTo(1);
    }

    @Test
    void theUncertaintyAndTheProjectAreKept() throws SQLException {
        assertThat(count("SELECT count(*) FROM relation_link WHERE uncertain AND source_id = " + UNITS[8]
                + " AND target_id = " + UNITS[1])).isEqualTo(1);
        assertThat(count("SELECT count(DISTINCT fk_action_unit_id) FROM relation_link")).isEqualTo(1);
    }

    @Test
    void theMirrorPairsAreFoundByTheMeaningOfTheLabels() throws SQLException {
        assertThat(count("SELECT count(*) FROM relation_rule_mirror_concept WHERE fk_concept_id = " + recouvre
                + " AND fk_mirror_concept_id = " + recouverte)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM relation_rule_mirror_concept WHERE fk_concept_id = " + sur
                + " AND fk_mirror_concept_id = " + sous)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM relation_rule_mirror_concept")).isEqualTo(2);
    }

    @Test
    void theHelpersAreGone() throws SQLException {
        assertThat(count("SELECT count(*) FROM pg_proc WHERE proname LIKE 'tmp_siamois_strati%'")).isZero();
    }

    private static Long qualifierOfOrder(int posterior, int anterior) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT l.fk_qualifier_concept_id FROM relation_link l JOIN relation_definition d "
                        + "ON d.relation_definition_id = l.fk_relation_definition_id WHERE d.fk_source_field_id = -328 "
                        + "AND l.source_id = " + UNITS[posterior] + " AND l.target_id = " + UNITS[anterior])) {
            assertThat(r.next()).as("order link %s -> %s", posterior, anterior).isTrue();
            long id = r.getLong(1);
            return r.wasNull() ? null : id;
        }
    }

    /** Old relationships, unit 1 the anterior one, one per case of the takeover rules. */
    private static void createOldStratigraphy(Connection c) throws SQLException {
        long person = id(c, "INSERT INTO person (username, password) VALUES ('t', 'x') RETURNING person_id");
        long institution = id(c, "INSERT INTO institution (creation_date, identifier, institution_name) "
                + "VALUES (now(), 't', 't') RETURNING institution_id");
        long project = id(c, "INSERT INTO action_unit (fk_created_by, fk_institution_id, validation_status, "
                + "full_identifier, identifier, name) VALUES (" + person + ", " + institution
                + ", 'INCOMPLETE', 'T', 'T', 't') RETURNING action_unit_id");
        for (int i = 1; i < UNITS.length; i++) {
            UNITS[i] = id(c, "INSERT INTO recording_unit (sync_revision, fk_created_by, fk_institution_id, "
                    + "fk_action_unit_id, fk_author_id, validation_status, full_identifier) VALUES (0, " + person + ", "
                    + institution + ", " + project + ", " + person + ", 'INCOMPLETE', 'UE " + i + "') RETURNING recording_unit_id");
        }
        long type = id(c, "INSERT INTO vocabulary_type (label) VALUES ('t') RETURNING vocabulary_type_id");
        long instance = id(c, "INSERT INTO vocabulary_instance (base_uri) VALUES ('https://example.test') RETURNING vocabulary_instance_id");
        long vocabulary = id(c, "INSERT INTO vocabulary (fk_type_id, fk_vocabulary_instance_id, external_id) VALUES ("
                + type + ", " + instance + ", 'v') RETURNING vocabulary_id");
        long anteriorTop = top(c, vocabulary, "SIARU.STRATI.ANTERIOR");
        long posteriorTop = top(c, vocabulary, "SIARU.STRATI.POSTERIOR");
        long synchronousTop = top(c, vocabulary, "SIARU.STRATI.SYNCHRONOUS");
        long legacyTop = top(c, vocabulary, "SIARU.STRATI");
        recouvre = child(c, vocabulary, anteriorTop, "recouvre");
        recouverte = child(c, vocabulary, posteriorTop, "Est recouverte par");
        contemporain = child(c, vocabulary, synchronousTop, "contemporain de");
        long synchroneAvec = child(c, vocabulary, legacyTop, "synchrone avec");
        long anterieurA = child(c, vocabulary, legacyTop, "Antérieur à");
        long regroupe = child(c, vocabulary, legacyTop, "regroupe");
        sur = child(c, vocabulary, legacyTop, "sur");
        sous = child(c, vocabulary, legacyTop, "sous");
        adosse = child(c, vocabulary, legacyTop, "Adossé");

        old(c, 1, 2, recouverte, true, true, false);
        old(c, 2, 3, recouvre, false, true, false);
        old(c, 3, 4, anterieurA, true, true, false);
        old(c, 4, 5, regroupe, true, true, false);
        old(c, 5, 6, contemporain, true, false, false);
        old(c, 7, 6, synchroneAvec, true, false, false);
        old(c, 7, 8, sous, true, true, false);
        old(c, 1, 8, null, null, true, true);
        old(c, 2, 8, adosse, true, true, false);
    }

    private static void old(Connection c, int unit1, int unit2, Long concept, Boolean direction, boolean asynchronous,
                            boolean uncertain) throws SQLException {
        execute(c, "INSERT INTO stratigraphic_relationship (fk_recording_unit_1_id, fk_recording_unit_2_id, "
                + "fk_relationship_concept_id, concept_direction, asynchronous, uncertain) VALUES (" + UNITS[unit1] + ", "
                + UNITS[unit2] + ", " + concept + ", " + direction + ", " + asynchronous + ", " + uncertain + ")");
    }

    private static long top(Connection c, long vocabulary, String fieldCode) throws SQLException {
        long concept = id(c, "INSERT INTO concept (fk_vocabulary_id) VALUES (" + vocabulary + ") RETURNING concept_id");
        execute(c, "INSERT INTO concept_field_config (field_code, fk_concept_id, fk_vocabulary_id) VALUES ('"
                + fieldCode + "', " + concept + ", " + vocabulary + ")");
        return concept;
    }

    private static long child(Connection c, long vocabulary, long parent, String label) throws SQLException {
        long concept = id(c, "INSERT INTO concept (fk_vocabulary_id) VALUES (" + vocabulary + ") RETURNING concept_id");
        execute(c, "INSERT INTO concept_hierarchy (fk_parent_concept_id, fk_child_concept_id) VALUES (" + parent + ", "
                + concept + ")");
        execute(c, "INSERT INTO concept_label (label_type, label, lang_code, fk_concept_id) VALUES (0, '"
                + label.replace("'", "''") + "', 'fr', " + concept + ")");
        return concept;
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = connect()) {
            return id(c, sql);
        }
    }

    private static long id(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }
}
