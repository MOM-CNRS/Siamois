package fr.siamois.infrastructure.database.repositories.misc;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SearchResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @InjectMocks
    private SearchRepository searchRepository;

    private InstitutionDTO institution;
    private PersonDTO person;

    @BeforeEach
    void setUp() {
        institution = new InstitutionDTO();
        institution.setId(7L);
        person = new PersonDTO();
        person.setId(13L);
    }

    // ------------------------------------------------------------------
    // findResultsFor — happy path
    // ------------------------------------------------------------------

    @Test
    void findResultsFor_singleRow_returnsMappedDto() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("matching_term")).thenReturn("paris");
        when(resultSet.getObject("action_unit_id", Long.class)).thenReturn(11L);
        when(resultSet.getObject("spatial_unit_id", Long.class)).thenReturn(22L);
        when(resultSet.getObject("specimen_id", Long.class)).thenReturn(33L);
        when(resultSet.getLong("similarity_score")).thenReturn(99L);

        List<SearchResultDTO> results = searchRepository.findResultsFor("paris", institution, person);

        assertEquals(1, results.size());
        SearchResultDTO dto = results.get(0);
        assertNotNull(dto);
        assertEquals("paris", dto.getMatchingTerm());
        assertEquals(11L, dto.getActionUnitId());
        assertEquals(22L, dto.getSpatialUnitId());

        verify(statement).setString(1, "paris");
        verify(statement).setLong(2, 7L);
        verify(statement).setLong(3, 13L);
    }

    @Test
    void findResultsFor_multipleRows_returnsAll() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("matching_term")).thenReturn("a", "b");
        when(resultSet.getObject(eq("action_unit_id"), eq(Long.class))).thenReturn(1L, 2L);
        when(resultSet.getObject(eq("spatial_unit_id"), eq(Long.class))).thenReturn(10L, 20L);
        when(resultSet.getObject(eq("specimen_id"), eq(Long.class))).thenReturn(100L, 200L);
        when(resultSet.getLong("similarity_score")).thenReturn(5L, 6L);

        List<SearchResultDTO> results = searchRepository.findResultsFor("foo", institution, person);

        assertEquals(2, results.size());
        assertEquals("a", results.get(0).getMatchingTerm());
        assertEquals("b", results.get(1).getMatchingTerm());
    }

    @Test
    void findResultsFor_emptyResultSet_returnsEmptyList() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        List<SearchResultDTO> results = searchRepository.findResultsFor("x", institution, person);

        assertTrue(results.isEmpty());
    }

    @Test
    void findResultsFor_nullInput_isPropagatedToStatement() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        searchRepository.findResultsFor(null, institution, person);

        verify(statement).setString(1, null);
    }

    // ------------------------------------------------------------------
    // SQL exception branches — every failure must short-circuit to []
    // ------------------------------------------------------------------

    @Test
    void findResultsFor_connectionFailure_returnsEmptyList() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("conn-down"));

        List<SearchResultDTO> results = searchRepository.findResultsFor("x", institution, person);

        assertTrue(results.isEmpty());
    }

    @Test
    void findResultsFor_prepareStatementFailure_returnsEmptyList() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("syntax"));

        List<SearchResultDTO> results = searchRepository.findResultsFor("x", institution, person);

        assertTrue(results.isEmpty());
    }

    @Test
    void findResultsFor_executeQueryFailure_returnsEmptyList() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("exec-fail"));

        List<SearchResultDTO> results = searchRepository.findResultsFor("x", institution, person);

        assertTrue(results.isEmpty());
    }

    @Test
    void findResultsFor_rowMappingFailure_yieldsNullEntryInList() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        // The mapping call itself throws SQLException → rowToDTO swallows and returns null.
        when(resultSet.getString("matching_term")).thenThrow(new SQLException("bad-column"));

        List<SearchResultDTO> results = searchRepository.findResultsFor("x", institution, person);

        // processResultSet still adds the (null) row produced by rowToDTO before
        // returning, so we observe the failure as a single null entry.
        assertEquals(1, results.size());
        assertEquals(null, results.get(0));
    }

    @Test
    void findResultsFor_setLongFailure_returnsEmptyList() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        // Simulate failure when binding params.
        org.mockito.Mockito.doThrow(new SQLException("bind-fail"))
                .when(statement).setLong(anyInt(), anyLong());

        List<SearchResultDTO> results = searchRepository.findResultsFor("x", institution, person);

        assertTrue(results.isEmpty());
    }
}
