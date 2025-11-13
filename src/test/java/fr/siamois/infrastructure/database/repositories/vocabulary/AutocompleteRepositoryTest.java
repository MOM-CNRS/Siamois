package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutocompleteRepositoryTest {

    @Mock
    private HikariDataSource dataSource;

    @InjectMocks
    private AutocompleteRepository autocompleteRepository;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    @Test
    void shouldReturnListWithAltLabelsAndTruncatedDefinition() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        // Simulate a single row
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("vocabulary_type_id")).thenReturn(10L);
        when(resultSet.getString("vocabulary_type_label")).thenReturn("TypeLabel");
        when(resultSet.getLong("vocabulary_id")).thenReturn(20L);
        when(resultSet.getString("vocabulary_base_uri")).thenReturn("http://base");
        when(resultSet.getString("vocabulary_external_id")).thenReturn("extVocab");
        when(resultSet.getLong("concept_id")).thenReturn(30L);
        when(resultSet.getString("concept_external_id")).thenReturn("extConcept");
        when(resultSet.getLong("parent_concept_id")).thenReturn(40L);
        when(resultSet.getString("parent_concept_external_id")).thenReturn("extParent");
        when(resultSet.getLong("concept_label_id")).thenReturn(50L);
        when(resultSet.getString("concept_label_label")).thenReturn("prefLabel");

        // alt labels separated by ;# -> should create two alt label DTOs + original
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn("alt1;#alt2");

        // long definition to trigger truncation
        String longDef = "A".repeat(AutocompleteRepository.DESCRIPTION_TRUNCATE_LENGTH + 10);
        when(resultSet.getString("data_definition")).thenReturn(longDef);
        when(resultSet.getString("data_hierarchy_str")).thenReturn("hierarchy");

        Concept concept = new Concept();
        concept.setId(123L);

        // Act
        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", "in", 10);

        // Assert
        // one row -> original dto + 2 alt label dtos
        assertEquals(3, results.size(), "Expected 3 entries (1 pref + 2 alt)");

        // the original pref label should be present as one of the entries
        boolean foundPref = results.stream().anyMatch(d -> d.getOriginalPrefLabel().equals("prefLabel"));
        assertTrue(foundPref);

        // definition should be truncated on the original dto (and alt dtos keep same truncated def)
        for (ConceptAutocompleteDTO dto : results) {
            assertNotNull(dto.getDefinition());
            assertTrue(dto.getDefinition().length() <= AutocompleteRepository.DESCRIPTION_TRUNCATE_LENGTH + 3); // ... may add
            assertTrue(dto.getDefinition().startsWith("A"));
        }

        // verify resources closed
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void shouldReturnSingleDtoWhenNoAltLabelsAndNullDefinition() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("vocabulary_type_id")).thenReturn(1L);
        when(resultSet.getString("vocabulary_type_label")).thenReturn("Type");
        when(resultSet.getLong("vocabulary_id")).thenReturn(2L);
        when(resultSet.getString("vocabulary_base_uri")).thenReturn("base");
        when(resultSet.getString("vocabulary_external_id")).thenReturn("ext");
        when(resultSet.getLong("concept_id")).thenReturn(3L);
        when(resultSet.getString("concept_external_id")).thenReturn("extC");
        when(resultSet.getLong("parent_concept_id")).thenReturn(4L);
        when(resultSet.getString("parent_concept_external_id")).thenReturn("extP");
        when(resultSet.getLong("concept_label_id")).thenReturn(5L);
        when(resultSet.getString("concept_label_label")).thenReturn("label");

        // no alt labels
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn(null);
        // null definition -> should become empty string
        when(resultSet.getString("data_definition")).thenReturn(null);
        when(resultSet.getString("data_hierarchy_str")).thenReturn(null);

        Concept concept = new Concept();
        concept.setId(1L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "en", null, 5);

        assertEquals(1, results.size());
        ConceptAutocompleteDTO dto = results.get(0);
        assertEquals("", dto.getDefinition(), "Null definition should be converted to empty string");
        assertEquals(0, dto.getAltLabels().size(), "Alt labels list should be empty");
    }

    @Test
    void shouldReturnEmptyListWhenStatementThrows() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("boom"));

        Concept concept = new Concept();
        concept.setId(2L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", "in", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void shouldReturnEmptyListWhenDataSourceThrows() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("no conn"));

        Concept concept = new Concept();
        concept.setId(3L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", "in", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

}