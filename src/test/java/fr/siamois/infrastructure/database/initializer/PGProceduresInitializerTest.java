package fr.siamois.infrastructure.database.initializer;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PGProceduresInitializerTest {

    @Mock
    private HikariDataSource dataSource;

    @InjectMocks
    private PGProceduresInitializer pgProceduresInitializer;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;


    @Test
    void initialize_success_whenFilePresentAndSqlExecutes() throws Exception {
        // Arrange
        String sql = "SELECT 1;";
        byte[] sqlBytes = sql.getBytes(StandardCharsets.UTF_8);

        // On mocke chaque new ClassPathResource(...)
        try (MockedConstruction<ClassPathResource> mocked =
                     Mockito.mockConstruction(ClassPathResource.class,
                             (mock, context) -> {
                                 // Quand le code appelle resource.getInputStream(), on renvoie notre SQL
                                 when(mock.getInputStream())
                                         .thenReturn(new ByteArrayInputStream(sqlBytes));
                             })) {

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeLargeUpdate(any())).thenReturn(1L);

            // Act
            assertDoesNotThrow(() -> pgProceduresInitializer.initialize());

            // Assert
            verify(statement, times(1)).executeLargeUpdate(any());
            verify(dataSource, times(1)).getConnection();
        }
    }

    @Test
    void initialize_throwsDatabaseDataInitException_whenResourceIsUnreadable() {
        // Arrange
        // This mock construction will be activated if the resolver creates ClassPathResource instances.
        try (MockedConstruction<ClassPathResource> mocked =
                     Mockito.mockConstruction(ClassPathResource.class,
                             (mock, context) -> {
                                 // For any ClassPathResource created, make getInputStream throw an exception.
                                 when(mock.getInputStream()).thenThrow(new IOException("Cannot read file"));
                             })) {

            // Act & Assert
            assertThrows(
                    DatabaseDataInitException.class,
                    () -> pgProceduresInitializer.initialize()
            );
        }
    }

    @Test
    void initialize_throwsDatabaseDataInitException_whenDbConnectionFails() throws Exception {
        // Arrange
        // Mock resource loading to be successful, so we can test the DB part
        try (MockedConstruction<ClassPathResource> mocked =
                     Mockito.mockConstruction(ClassPathResource.class,
                             (mock, context) -> {
                                 when(mock.getInputStream()).thenReturn(new ByteArrayInputStream("SELECT 1;".getBytes()));
                             })) {

            // Mock DB connection to fail
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

            // Act & Assert
            DatabaseDataInitException exception = assertThrows(
                    DatabaseDataInitException.class,
                    () -> pgProceduresInitializer.initialize()
            );

            assertEquals("Failed to initialize PGSQL script", exception.getMessage());
            assertTrue(exception.getCause() instanceof SQLException);
            assertEquals("Connection failed", exception.getCause().getMessage());
        }
    }
}