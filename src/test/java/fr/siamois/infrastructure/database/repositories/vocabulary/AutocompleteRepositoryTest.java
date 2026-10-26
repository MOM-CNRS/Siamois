package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private ConceptLabelRepository conceptLabelRepository;

    @InjectMocks
    private AutocompleteRepository autocompleteRepository;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    @Test
    void shouldReturnSingleDtoWithPrefLabelWhenAltLabelsExist() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        // Simulate a single row where the matched label is the prefLabel
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

        // alt labels are kept on the DTO but no longer added as separate entries
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn("alt1;#alt2");

        // long definition to trigger truncation
        String longDef = "A".repeat(AutocompleteRepository.DESCRIPTION_TRUNCATE_LENGTH + 10);
        when(resultSet.getString("data_definition")).thenReturn(longDef);
        when(resultSet.getString("data_hierarchy_str")).thenReturn("hierarchy");

        Concept concept = new Concept();
        concept.setId(123L);

        // Act
        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", "in", 10);

        // Assert: one entry per concept, with the prefLabel (altLabels are not separate choices)
        assertEquals(1, results.size(), "Expected 1 entry (the concept, deduplicated)");

        ConceptAutocompleteDTO dto = results.get(0);
        assertEquals("prefLabel", dto.getConceptLabelToDisplay().getLabel());
        assertEquals("prefLabel", dto.getOriginalPrefLabel());
        // altLabels are still carried on the DTO for information
        assertEquals(List.of("alt1", "alt2"), dto.getAltLabels());

        // definition should be truncated
        assertNotNull(dto.getDefinition());
        assertTrue(dto.getDefinition().length() <= AutocompleteRepository.DESCRIPTION_TRUNCATE_LENGTH + 3);
        assertTrue(dto.getDefinition().startsWith("A"));

        // verify resources closed
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void shouldReplaceAltLabelMatchWithPrefLabel() throws Exception {
        // Arrange: the SQL matched on an altLabel ("alt1"), not on the prefLabel
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("vocabulary_type_id")).thenReturn(1L);
        when(resultSet.getString("vocabulary_type_label")).thenReturn("Type");
        when(resultSet.getLong("vocabulary_id")).thenReturn(2L);
        when(resultSet.getString("vocabulary_base_uri")).thenReturn("base");
        when(resultSet.getString("vocabulary_external_id")).thenReturn("ext");
        when(resultSet.getLong("concept_id")).thenReturn(30L);
        when(resultSet.getString("concept_external_id")).thenReturn("extConcept");
        when(resultSet.getLong("parent_concept_id")).thenReturn(40L);
        when(resultSet.getString("parent_concept_external_id")).thenReturn("extParent");
        when(resultSet.getLong("concept_label_id")).thenReturn(50L);
        // The matched label is an altLabel
        when(resultSet.getString("concept_label_label")).thenReturn("alt1");
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn("alt1;#alt2");
        when(resultSet.getString("data_definition")).thenReturn(null);
        when(resultSet.getString("data_hierarchy_str")).thenReturn(null);

        // The prefLabel repository returns the real prefLabel for this concept
        ConceptPrefLabel prefLabel = new ConceptPrefLabel();
        prefLabel.setLabel("prefLabel");
        Concept conceptEntity = new Concept();
        conceptEntity.setId(30L);
        prefLabel.setConcept(conceptEntity);
        when(conceptLabelRepository.findAllPrefLabelsByLangCodeAndConcept_IdIn(eq("fr"), any()))
                .thenReturn(List.of(prefLabel));

        Concept concept = new Concept();
        concept.setId(123L);

        // Act: search by altLabel
        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", "alt1", 10);

        // Assert: the displayed label is the prefLabel, not the matched altLabel
        assertEquals(1, results.size());
        assertEquals("prefLabel", results.get(0).getConceptLabelToDisplay().getLabel(),
                "AltLabel match should be replaced by the prefLabel");
        assertEquals("prefLabel", results.get(0).getOriginalPrefLabel());
        // altLabels are still carried for information
        assertEquals(List.of("alt1", "alt2"), results.get(0).getAltLabels());

        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void shouldDeduplicateConceptsMatchedByMultipleLabels() throws Exception {
        // Arrange: the same concept matched twice — once by prefLabel, once by altLabel
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        // First row: matched by prefLabel
        // Second row: matched by altLabel (same concept)
        when(resultSet.next()).thenReturn(true, true, false);

        // Common columns
        when(resultSet.getLong("vocabulary_type_id")).thenReturn(1L);
        when(resultSet.getString("vocabulary_type_label")).thenReturn("Type");
        when(resultSet.getLong("vocabulary_id")).thenReturn(2L);
        when(resultSet.getString("vocabulary_base_uri")).thenReturn("base");
        when(resultSet.getString("vocabulary_external_id")).thenReturn("ext");
        when(resultSet.getLong("concept_id")).thenReturn(30L);
        when(resultSet.getString("concept_external_id")).thenReturn("extConcept");
        when(resultSet.getLong("parent_concept_id")).thenReturn(40L);
        when(resultSet.getString("parent_concept_external_id")).thenReturn("extParent");
        when(resultSet.getLong("concept_label_id")).thenReturn(50L, 51L);
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn("alt1;#alt2");
        when(resultSet.getString("data_definition")).thenReturn(null);
        when(resultSet.getString("data_hierarchy_str")).thenReturn(null);

        // First row: prefLabel matched
        // Second row: altLabel matched
        when(resultSet.getString("concept_label_label")).thenReturn("prefLabel", "alt1");

        Concept concept = new Concept();
        concept.setId(123L);

        // Act
        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", "a", 10);

        // Assert: deduplicated to one entry, with the prefLabel (not the altLabel)
        assertEquals(1, results.size(), "Concept matched by multiple labels should appear once");
        assertEquals("prefLabel", results.get(0).getConceptLabelToDisplay().getLabel(),
                "PrefLabel should be preferred over altLabel match");

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

    @Test
    void shouldBindEmptyStringWhenInputIsNull() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Concept concept = new Concept();
        concept.setId(7L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFor(concept, "fr", null, 10);

        assertEquals(0, results.size());
        verify(statement).setString(2, "fr");
        verify(statement).setString(3, "");
    }

    @Test
    void shouldQueryRelatedFunctionAndBindFieldThenBaseConcept() throws Exception {
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
        when(resultSet.getLong("parent_concept_id")).thenReturn(77L);
        when(resultSet.getString("parent_concept_external_id")).thenReturn("extField");
        when(resultSet.getLong("concept_label_id")).thenReturn(5L);
        when(resultSet.getString("concept_label_label")).thenReturn("relatedLabel");
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn(null);
        when(resultSet.getString("data_definition")).thenReturn("def");
        when(resultSet.getString("data_hierarchy_str")).thenReturn(null);

        Concept field = new Concept();
        field.setId(77L);
        Concept baseValue = new Concept();
        baseValue.setId(155L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFromRelatedFor(field, baseValue, "fr", "blo", 10);

        assertEquals(1, results.size());
        assertEquals("relatedLabel", results.get(0).getOriginalPrefLabel());
        assertEquals("def", results.get(0).getDefinition());

        // the related search must hit the dedicated DB function, not the field-based one
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("concept_autocomplete_related(?, ?, ?, ?, ?)"),
                "Expected the query to call concept_autocomplete_related with 5 parameters, but was: " + queryCaptor.getValue());

        // the field concept is bound first, the base value second, as declared by concept_autocomplete_related
        verify(statement).setLong(1, 77L);
        verify(statement).setObject(2, 155L);
        verify(statement).setString(3, "fr");
        verify(statement).setString(4, "blo");
        verify(statement).setInt(5, 10);

        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void shouldForwardNullInputWhenRelatedInputIsNull() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Concept field = new Concept();
        field.setId(77L);
        Concept baseValue = new Concept();
        baseValue.setId(155L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFromRelatedFor(field, baseValue, "fr", null, 10);

        assertEquals(0, results.size());
        // concept_autocomplete_search treats a NULL input as "no text filter"
        verify(statement).setString(4, null);
    }

    @Test
    void shouldReturnSingleRelatedResultWithPrefLabelWhenAltLabelsExist() throws Exception {
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
        when(resultSet.getLong("parent_concept_id")).thenReturn(77L);
        when(resultSet.getString("parent_concept_external_id")).thenReturn("extField");
        when(resultSet.getLong("concept_label_id")).thenReturn(5L);
        when(resultSet.getString("concept_label_label")).thenReturn("relatedLabel");
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn("altA;#altB");
        when(resultSet.getString("data_definition")).thenReturn(null);
        when(resultSet.getString("data_hierarchy_str")).thenReturn("hierarchy");

        Concept field = new Concept();
        field.setId(77L);
        Concept baseValue = new Concept();
        baseValue.setId(155L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFromRelatedFor(field, baseValue, "fr", "al", 10);

        // one row -> one deduplicated entry with the prefLabel (altLabels are not separate choices)
        assertEquals(1, results.size());
        assertEquals("relatedLabel", results.get(0).getConceptLabelToDisplay().getLabel());
        assertEquals("relatedLabel", results.get(0).getOriginalPrefLabel());
        assertEquals(List.of("altA", "altB"), results.get(0).getAltLabels());
        assertEquals("", results.get(0).getDefinition());
    }

    @Test
    void shouldReturnEmptyListWhenRelatedStatementThrows() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("boom"));

        Concept field = new Concept();
        field.setId(77L);
        Concept baseValue = new Concept();
        baseValue.setId(155L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFromRelatedFor(field, baseValue, "fr", "in", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void shouldReturnEmptyListWhenDataSourceThrowsOnRelated() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("no conn"));

        Concept field = new Concept();
        field.setId(77L);
        Concept baseValue = new Concept();
        baseValue.setId(155L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsFromRelatedFor(field, baseValue, "fr", "in", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void shouldBindNullBaseConceptWhenRelatedHasNoBaseValue() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Concept field = new Concept();
        field.setId(77L);

        // no value selected yet on the field the current one depends on
        assertEquals(0, autocompleteRepository.findMatchingConceptsFromRelatedFor(field, null, "fr", "in", 10).size());
        verify(statement).setObject(2, null);
    }

    // --- Concepts imported outside of any field context --------------------------------------------

    @Test
    void shouldLeaveParentConceptNullWhenTheLabelHasNoFieldContext() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        stubSingleRow();
        // concepts configured through a branch or a collection carry no field context
        when(resultSet.getLong("parent_concept_id")).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);

        Concept topTerm = new Concept();
        topTerm.setId(99L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "cer", 10);

        assertEquals(1, results.size());
        assertNull(results.get(0).getConceptLabelToDisplay().getParentConcept());
        // the parent concept is the only thing missing, the concept itself is still fully read
        assertEquals("extConcept", results.get(0).concept().getExternalId());
        verify(resultSet, never()).getString("parent_concept_external_id");
    }

    // --- findMatchingConceptsInBranchOf ------------------------------------------------------------

    @Test
    void shouldQueryBranchFunctionAndBindTopTerm() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        stubSingleRow();
        stubParentConcept(99L, "extTopTerm");
        when(resultSet.getString("data_aggregated_alt_labels")).thenReturn("altA;#altB");

        Concept topTerm = new Concept();
        topTerm.setId(99L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "cer", 10);

        // one row -> one deduplicated entry with the prefLabel (altLabels are not separate choices)
        assertEquals(1, results.size());
        assertEquals("prefLabel", results.get(0).getOriginalPrefLabel());
        assertEquals("prefLabel", results.get(0).getConceptLabelToDisplay().getLabel());
        assertEquals(List.of("altA", "altB"), results.get(0).getAltLabels());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("concept_autocomplete_branch(?, ?, ?, ?)"),
                "Expected the query to call concept_autocomplete_branch, but was: " + queryCaptor.getValue());

        verify(statement).setLong(1, 99L);
        verify(statement).setString(2, "fr");
        verify(statement).setString(3, "cer");
        verify(statement).setInt(4, 10);

        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void shouldBindEmptyStringWhenBranchInputIsNull() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Concept topTerm = new Concept();
        topTerm.setId(99L);

        assertEquals(0, autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", null, 10).size());
        verify(statement).setString(3, "");
    }

    @Test
    void shouldReturnEmptyListWhenBranchStatementThrows() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("boom"));

        Concept topTerm = new Concept();
        topTerm.setId(99L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "cer", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void shouldReturnEmptyListWhenDataSourceThrowsOnBranch() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("no conn"));

        Concept topTerm = new Concept();
        topTerm.setId(99L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInBranchOf(topTerm, "fr", "cer", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    // --- findMatchingConceptsInCollection ----------------------------------------------------------

    @Test
    void shouldQueryCollectionFunctionAndBindCollection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        stubSingleRow();
        stubParentConcept(88L, "extParent");
        when(resultSet.getString("data_definition")).thenReturn("def");

        ConceptCollection collection = new ConceptCollection();
        collection.setId(88L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "cer", 10);

        assertEquals(1, results.size());
        assertEquals("prefLabel", results.get(0).getOriginalPrefLabel());
        assertEquals("def", results.get(0).getDefinition());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("concept_autocomplete_collection(?, ?, ?, ?)"),
                "Expected the query to call concept_autocomplete_collection, but was: " + queryCaptor.getValue());

        verify(statement).setLong(1, 88L);
        verify(statement).setString(2, "fr");
        verify(statement).setString(3, "cer");
        verify(statement).setInt(4, 10);

        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void shouldBindEmptyStringWhenCollectionInputIsNull() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        ConceptCollection collection = new ConceptCollection();
        collection.setId(88L);

        assertEquals(0, autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", null, 10).size());
        verify(statement).setString(3, "");
    }

    @Test
    void shouldReturnEmptyListWhenCollectionStatementThrows() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("boom"));

        ConceptCollection collection = new ConceptCollection();
        collection.setId(88L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "cer", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    void shouldReturnEmptyListWhenDataSourceThrowsOnCollection() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("no conn"));

        ConceptCollection collection = new ConceptCollection();
        collection.setId(88L);

        List<ConceptAutocompleteDTO> results = autocompleteRepository.findMatchingConceptsInCollection(collection, "fr", "cer", 10);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    // --- Helpers -----------------------------------------------------------------------------------

    /**
     * Every column of a single result row, except the ones each test cares about.
     */
    private void stubSingleRow() throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("vocabulary_type_id")).thenReturn(1L);
        when(resultSet.getString("vocabulary_type_label")).thenReturn("Type");
        when(resultSet.getLong("vocabulary_id")).thenReturn(2L);
        when(resultSet.getString("vocabulary_base_uri")).thenReturn("base");
        when(resultSet.getString("vocabulary_external_id")).thenReturn("ext");
        when(resultSet.getLong("concept_id")).thenReturn(3L);
        when(resultSet.getString("concept_external_id")).thenReturn("extConcept");
        when(resultSet.getLong("concept_label_id")).thenReturn(5L);
        when(resultSet.getString("concept_label_label")).thenReturn("prefLabel");
        lenient().when(resultSet.getString("data_aggregated_alt_labels")).thenReturn(null);
        lenient().when(resultSet.getString("data_definition")).thenReturn(null);
        lenient().when(resultSet.getString("data_hierarchy_str")).thenReturn(null);
    }

    private void stubParentConcept(long id, String externalId) throws SQLException {
        when(resultSet.getLong("parent_concept_id")).thenReturn(id);
        when(resultSet.getString("parent_concept_external_id")).thenReturn(externalId);
    }

}