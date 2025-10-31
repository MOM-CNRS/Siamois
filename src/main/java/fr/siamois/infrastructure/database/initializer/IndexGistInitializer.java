package fr.siamois.infrastructure.database.initializer;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Service
@Order(7)
public class IndexGistInitializer implements DatabaseInitializer {

    private final HikariDataSource hikariDataSource;

    public IndexGistInitializer(HikariDataSource hikariDataSource) {
        this.hikariDataSource = hikariDataSource;
    }

    @Override
    public void initialize() throws DatabaseDataInitException {
        try (Connection connection = hikariDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.addBatch("CREATE INDEX IF NOT EXISTS users_username_trgm ON person USING gist (username gist_trgm_ops);");
            statement.addBatch("CREATE INDEX IF NOT EXISTS users_email_trgm ON person USING gist (mail gist_trgm_ops);");
            statement.addBatch("CREATE INDEX IF NOT EXISTS concept_label_trgm ON concept_label USING gist(label gist_trgm_ops)");
            statement.executeBatch();
            log.info("GIST indexes created successfully");
        } catch (SQLException e) {
            log.error("Error while creating GIST indexes", e);
            throw new DatabaseDataInitException("Error while creating GIST indexes", e);
        }
    }
}
