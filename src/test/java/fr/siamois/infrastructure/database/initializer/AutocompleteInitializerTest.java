package fr.siamois.infrastructure.database.initializer;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutocompleteInitializerTest {

    @Mock
    private HikariDataSource dataSource;

    @InjectMocks
    private AutocompleteInitializer autocompleteInitializer;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Test
    void initialize_success_whenFilePresentAndSqlExecutes() throws Exception {
        // Arrange
        String sql = "SELECT 1;";
        File temp = Files.createTempFile("concept_autocomplete", ".sql").toFile();
        Files.writeString(temp.toPath(), sql);

        try (MockedStatic<ResourceUtils> utilities = Mockito.mockStatic(ResourceUtils.class)) {
            utilities.when(() -> ResourceUtils.getFile("classpath:pgplsql/concept_autocomplete.sql"))
                    .thenReturn(temp);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeLargeUpdate(sql)).thenReturn(1L);

            // Act
            assertDoesNotThrow(() -> autocompleteInitializer.initialize());

            // Assert
            verify(statement, times(1)).executeLargeUpdate(sql);
        }
    }

    @Test
    void initialize_throwsDatabaseDataInitException_whenFileNotFound() throws Exception {
        // Arrange
        try (MockedStatic<ResourceUtils> utilities = Mockito.mockStatic(ResourceUtils.class)) {
            utilities.when(() -> ResourceUtils.getFile(anyString()))
                    .thenThrow(new FileNotFoundException("not found"));

            // Act & Assert
            assertThrows(DatabaseDataInitException.class, () -> autocompleteInitializer.initialize());

            // No interaction with datasource should occur
            verifyNoInteractions(dataSource);
        }
    }

}