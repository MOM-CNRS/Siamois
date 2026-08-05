package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.dto.entity.vocabulary.ConceptAltLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Repository for fetching autocomplete suggestions for concepts.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AutocompleteRepository {

    private final HikariDataSource dataSource;

    public static final int DESCRIPTION_TRUNCATE_LENGTH = 200;

    private ConceptAutocompleteDTO rowToDTO(@NonNull ResultSet resultSet, @NonNull String langcode) throws SQLException {
        VocabularyType type = new VocabularyType();
        type.setId(resultSet.getLong("vocabulary_type_id"));
        type.setLabel(resultSet.getString("vocabulary_type_label"));

        VocabularyDTO vocabulary = new VocabularyDTO();
        vocabulary.setId(resultSet.getLong("vocabulary_id"));
        vocabulary.setType(type);
        vocabulary.setBaseUri(resultSet.getString("vocabulary_base_uri"));
        vocabulary.setExternalVocabularyId(resultSet.getString("vocabulary_external_id"));

        ConceptDTO concept = new ConceptDTO();
        concept.setId(resultSet.getLong("concept_id"));
        concept.setVocabulary(vocabulary);
        concept.setDeleted(false);
        concept.setExternalId(resultSet.getString("concept_external_id"));

        ConceptDTO parentConcept = parentConceptOf(resultSet, vocabulary);

        ConceptPrefLabelDTO prefLabel = new ConceptPrefLabelDTO();
        prefLabel.setId(resultSet.getLong("concept_label_id"));
        prefLabel.setConcept(concept);
        prefLabel.setLabel(resultSet.getString("concept_label_label"));
        prefLabel.setParentConcept(parentConcept);
        prefLabel.setLangCode(langcode);

        String altLabelsStr = resultSet.getString("data_aggregated_alt_labels");
        List<String> altLabels;
        if (altLabelsStr != null) {
            altLabels = List.of(altLabelsStr.split(";#"));
        } else {
            altLabels = List.of();
        }

        return new ConceptAutocompleteDTO(
                prefLabel,
                prefLabel.getLabel(),
                altLabels,
                truncate(resultSet.getString("data_definition")),
                resultSet.getString("data_hierarchy_str")
        );
    }

    /**
     * The concept in whose field context the label was imported. Concepts configured through a branch
     * or a collection are imported outside of any field context, so the column is then null.
     */
    @Nullable
    private ConceptDTO parentConceptOf(@NonNull ResultSet resultSet, @NonNull VocabularyDTO vocabulary) throws SQLException {
        long parentConceptId = resultSet.getLong("parent_concept_id");
        if (resultSet.wasNull()) {
            return null;
        }

        ConceptDTO parentConcept = new ConceptDTO();
        parentConcept.setId(parentConceptId);
        parentConcept.setVocabulary(vocabulary);
        parentConcept.setDeleted(false);
        parentConcept.setExternalId(resultSet.getString("parent_concept_external_id"));
        return parentConcept;
    }

    /**
     * Find matching concepts for the given concept in the specified language, input string, and limit.
     * This method calls the database function concept_autocomplete.
     *
     * @param field The field concept to find matches for
     * @param lang  The language code to filter results
     * @param input The input string to match against concept labels. Can be null, then treated as no text filter
     * @param limit The maximum number of results to return
     * @return A list of ConceptAutocompleteDTO containing matching concepts
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> findMatchingConceptsFor(@NonNull Concept field,
                                                                @NonNull String lang,
                                                                @Nullable String input,
                                                                int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ca.* FROM concept_autocomplete(?, ?, ?, ?) ca")) {
            log.trace("Executing findMatchingConceptsFor with field id {}, lang {}, input '{}', limit {}", field.getId(), lang, input, limit);
            statement.setLong(1, field.getId());
            statement.setString(2, lang);
            statement.setString(3, input != null ? input : "");
            statement.setInt(4, limit);

            return processResultSet("field id " + field.getId(), lang, statement);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for field id {}: {}", field.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find matching concepts among the children and sub-children of the given top term, in the specified
     * language, input string and limit. This method calls the database function concept_autocomplete_branch.
     * The top term itself is never a candidate.
     *
     * @param topTerm The concept the candidates must descend from
     * @param lang    The language code to filter results
     * @param input   The input string to match against concept labels. Can be null, then treated as no text filter
     * @param limit   The maximum number of results to return
     * @return A list of ConceptAutocompleteDTO containing the matching concepts of the branch
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> findMatchingConceptsInBranchOf(@NonNull Concept topTerm,
                                                                      @NonNull String lang,
                                                                      @Nullable String input,
                                                                      int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ca.* FROM concept_autocomplete_branch(?, ?, ?, ?) ca")) {
            log.trace("Executing findMatchingConceptsInBranchOf with top term id {}, lang {}, input '{}', limit {}", topTerm.getId(), lang, input, limit);
            statement.setLong(1, topTerm.getId());
            statement.setString(2, lang);
            statement.setString(3, input != null ? input : "");
            statement.setInt(4, limit);

            return processResultSet("branch of concept id " + topTerm.getId(), lang, statement);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for branch of concept id {}: {}", topTerm.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find matching concepts among the concepts of the given collection, in the specified language, input
     * string and limit. This method calls the database function concept_autocomplete_collection.
     *
     * @param collection The collection the candidates must belong to
     * @param lang       The language code to filter results
     * @param input      The input string to match against concept labels. Can be null, then treated as no text filter
     * @param limit      The maximum number of results to return
     * @return A list of ConceptAutocompleteDTO containing the matching concepts of the collection
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> findMatchingConceptsInCollection(@NonNull ConceptCollection collection,
                                                                         @NonNull String lang,
                                                                         @Nullable String input,
                                                                         int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ca.* FROM concept_autocomplete_collection(?, ?, ?, ?) ca")) {
            log.trace("Executing findMatchingConceptsInCollection with collection id {}, lang {}, input '{}', limit {}", collection.getId(), lang, input, limit);
            statement.setLong(1, collection.getId());
            statement.setString(2, lang);
            statement.setString(3, input != null ? input : "");
            statement.setInt(4, limit);

            return processResultSet("collection id " + collection.getId(), lang, statement);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for collection id {}: {}", collection.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find matching concepts among the concepts related to the given concept, in the specified language,
     * input string and limit. This method calls the database function concept_autocomplete_related.
     * The candidates are restricted to the concepts imported in the context of the given field concept
     * <em>and</em> related to the given base value.
     *
     * @param field        The field concept the candidates must be configured for
     * @param baseValue    The concept whose related concepts are the autocomplete candidates
     * @param lang         The language code to filter results
     * @param input        The input string to match against concept labels. Can be null, then treated as no text filter
     * @param limitResults The maximum number of results to return
     * @return A list of ConceptAutocompleteDTO containing matching related concepts
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> findMatchingConceptsFromRelatedFor(@NonNull Concept field,
                                                                           @Nullable Concept baseValue,
                                                                           @NonNull String lang,
                                                                           @Nullable String input,
                                                                           int limitResults) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ca.* FROM concept_autocomplete_related(?, ?, ?, ?, ?) ca")) {
            log.trace("Executing findMatchingConceptsFromRelatedFor with field id {}, lang {}, input '{}', limit {}", field.getId(), lang, input, limitResults);
            statement.setLong(1, field.getId());
            statement.setObject(2, Objects.isNull(baseValue) ? null : baseValue.getId());
            statement.setString(3, lang);
            statement.setString(4, input);
            statement.setInt(5, limitResults);

            return processResultSet("field id " + field.getId(), lang, statement);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for field id {} and related of {} : {}", field.getId(), baseValue.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    private List<ConceptAutocompleteDTO> processResultSet(@NonNull String searchedScope, @NonNull String lang, PreparedStatement statement) {
        List<ConceptAutocompleteDTO> results = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ConceptAutocompleteDTO dto = rowToDTO(resultSet, lang);
                addAllAltLabelsToResults(lang, dto, results);
                results.add(dto);
            }
            return results;
        } catch (Exception e) {
            log.error("Error while processing result set for concept autocomplete of {}: {}", searchedScope, e.getMessage(), e);
            return List.of();
        }
    }

    private static void addAllAltLabelsToResults(String lang, ConceptAutocompleteDTO dto, List<ConceptAutocompleteDTO> results) {
        ConceptDTO currentConcept = dto.getConceptLabelToDisplay().getConcept();
        for (String altLabel : dto.getAltLabels()) {
            ConceptAltLabelDTO unsavedAltLabel = new ConceptAltLabelDTO();
            unsavedAltLabel.setLabel(altLabel);
            unsavedAltLabel.setLangCode(lang);
            unsavedAltLabel.setConcept(currentConcept);

            ConceptAutocompleteDTO altLabelDto = ConceptAutocompleteDTO.builder()
                    .conceptLabelToDisplay(unsavedAltLabel)
                    .originalPrefLabel(dto.getOriginalPrefLabel())
                    .altLabels(dto.getAltLabels())
                    .definition(dto.getDefinition())
                    .hierarchyPrefLabels(dto.getHierarchyPrefLabels())
                    .build();

            results.add(altLabelDto);
        }
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > DESCRIPTION_TRUNCATE_LENGTH ? text.substring(0, DESCRIPTION_TRUNCATE_LENGTH) + "..." : text;
    }
}
