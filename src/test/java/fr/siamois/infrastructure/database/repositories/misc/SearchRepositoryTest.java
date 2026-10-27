package fr.siamois.infrastructure.database.repositories.misc;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchRepositoryTest {

    @Mock
    private HikariDataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement statement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private Array sqlArray;

    @InjectMocks
    private SearchRepository searchRepository;

    @Test
    void searchSections_mapsRowsAndPassesTheParameters() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createArrayOf(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(sqlArray);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("entity_type")).thenReturn("recordingUnit");
        when(resultSet.getObject("entity_id", Long.class)).thenReturn(5L);
        when(resultSet.getString("label")).thenReturn("UE-5");
        when(resultSet.getString("subtitle")).thenReturn("P1");
        when(resultSet.getObject("project_id", Long.class)).thenReturn(9L);
        when(resultSet.getLong("total")).thenReturn(42L);

        List<GlobalSearchRow> rows = searchRepository.searchSections("ue", 7L, List.of(9L), null, null, 5);

        assertEquals(List.of(new GlobalSearchRow("recordingUnit", 5L, "UE-5", "P1", 9L, 42L)), rows);
        verify(statement).setString(1, "ue");
        verify(statement).setLong(2, 7L);
        verify(statement).setArray(3, sqlArray);
        verify(statement).setArray(4, null);
        verify(statement).setArray(5, null);
        verify(statement).setInt(6, 5);
    }

    @Test
    void searchSections_returnsNothingOnSqlError() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));

        assertTrue(searchRepository.searchSections("x", 1L, null, null, null, 5).isEmpty());
    }

    @Test
    void findProjectIdsByRef_returnsEveryMatchingProject() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getLong(1)).thenReturn(3L, 4L);

        assertEquals(List.of(3L, 4L), searchRepository.findProjectIdsByRef("abc", 7L));
        verify(statement).setLong(1, 7L);
        verify(statement).setString(2, "abc");
    }

    @Test
    void findProjectIdsByRef_returnsNothingOnSqlError() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));

        assertTrue(searchRepository.findProjectIdsByRef("abc", 7L).isEmpty());
    }
}
