package fr.siamois.infrastructure.database.initializer;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutocompleteInitializer implements DatabaseInitializer {

    private final HikariDataSource hikariDataSource;

    /**
     * Initialize the data mandatory for application's logic.
     */
    @Override
    public void initialize() throws DatabaseDataInitException {
        try {
            File autocomplete = ResourceUtils.getFile("classpath:pgplsql/concept_autocomplete.sql");
            String sql = Files.readString(autocomplete.toPath());
            try (Connection connection = hikariDataSource.getConnection()) {
                Statement statement = connection.createStatement();
                statement.executeLargeUpdate(sql);
                log.info("Autocomplete functions initialized successfully.");
            }
        } catch (Exception e) {
            throw new DatabaseDataInitException("Failed to initialize autocomplete data", e);
        }
    }
}
