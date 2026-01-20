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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            when(statement.executeLargeUpdate(sql)).thenReturn(1L);

            // Act
            assertDoesNotThrow(() -> autocompleteInitializer.initialize());

            // Assert
            verify(statement, times(1)).executeLargeUpdate(sql);
            verify(dataSource, times(1)).getConnection();
        }
    }

    @Test
    void initialize_throwsDatabaseDataInitException_whenFileNotFound()  {
        // Arrange : getInputStream() lève une FileNotFoundException
        try (MockedConstruction<ClassPathResource> mocked =
                     Mockito.mockConstruction(ClassPathResource.class,
                             (mock, context) -> {
                                 when(mock.getInputStream())
                                         .thenThrow(new FileNotFoundException("not found"));
                             })) {

            // Act & Assert
            assertThrows(DatabaseDataInitException.class, () -> autocompleteInitializer.initialize());

            // On vérifie bien que la DB n’est jamais touchée si on ne peut pas lire la ressource
            verifyNoInteractions(dataSource);
        }
    }

}