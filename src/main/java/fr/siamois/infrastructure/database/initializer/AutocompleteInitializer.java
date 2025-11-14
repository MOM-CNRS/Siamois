package fr.siamois.infrastructure.database.initializer;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
            Resource resource = new ClassPathResource("pgplsql/concept_autocomplete.sql");

            String sql;
            try (InputStream in = resource.getInputStream()) {
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            try (Connection connection = hikariDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeLargeUpdate(sql);
                log.info("Autocomplete functions initialized successfully.");
            }
        } catch (Exception e) {
            throw new DatabaseDataInitException("Failed to initialize autocomplete data", e);
        }
    }
}
