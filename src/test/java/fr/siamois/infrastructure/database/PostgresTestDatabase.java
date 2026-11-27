package fr.siamois.infrastructure.database;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * A PostgreSQL database for the tests of what only the database does (triggers, partial indexes, recursive
 * queries): the other tests run without a database. One container for the whole test run, built by the real
 * changelog ({@code changelog/db.changelog.xml}), so a test also proves the changelog applies to an empty database.
 * <p>
 * Use it from a class annotated {@code @Testcontainers(disabledWithoutDocker = true)}: without Docker, the class is
 * skipped instead of failing.
 */
public final class PostgresTestDatabase {

    /** PostgreSQL 17 with PostGIS, which the initial schema needs (geometry columns). */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    private static PostgreSQLContainer<?> container;

    private PostgresTestDatabase() {
    }

    /** The database, started and migrated on first use. */
    public static synchronized PostgreSQLContainer<?> get() {
        if (container == null) {
            PostgreSQLContainer<?> started = new PostgreSQLContainer<>(IMAGE);
            started.start();
            migrate(started);
            container = started;
        }
        return container;
    }

    /** A new connection to the database, in autocommit mode. */
    public static Connection connect() throws SQLException {
        PostgreSQLContainer<?> db = get();
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    private static void migrate(PostgreSQLContainer<?> db) {
        try (Connection connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("changelog/db.changelog.xml",
                    new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception e) {
            throw new IllegalStateException("The changelog does not apply to an empty database", e);
        }
    }
}
