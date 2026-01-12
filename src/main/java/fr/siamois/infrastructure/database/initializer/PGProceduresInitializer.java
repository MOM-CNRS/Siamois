package fr.siamois.infrastructure.database.initializer;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PGProceduresInitializer implements DatabaseInitializer {

    private final HikariDataSource hikariDataSource;

    /**
     * Initialize the data mandatory for application's logic.
     */
    @Override
    public void initialize() throws DatabaseDataInitException {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("pgplsql/*.sql");
            List<String> procedures = new ArrayList<>();
            int succesCount = 0;

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    procedures.add(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
            }

            try (Connection connection = hikariDataSource.getConnection()) {
                for (String procedure : procedures) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeLargeUpdate(procedure);
                        succesCount++;
                    }
                }
            }
            log.info("Successfully loaded {} procedures", succesCount);
        } catch (Exception e) {
            throw new DatabaseDataInitException("Failed to initialize autocomplete data", e);
        }
    }
}
