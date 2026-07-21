package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.dto.entity.ConceptAltLabelDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.VocabularyDTO;
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

        ConceptDTO parentConcept = new ConceptDTO();
        parentConcept.setId(resultSet.getLong("parent_concept_id"));
        parentConcept.setVocabulary(vocabulary);
        parentConcept.setDeleted(false);
        parentConcept.setExternalId(resultSet.getString("parent_concept_external_id"));

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

            return processResultSet(field, lang, statement);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for field id {}: {}", field.getId(), e.getMessage(), e);
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
                                                                           @NonNull Concept baseValue,
                                                                           @NonNull String lang,
                                                                           @Nullable String input,
                                                                           int limitResults) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ca.* FROM concept_autocomplete_related(?, ?, ?, ?, ?) ca")) {
            log.trace("Executing findMatchingConceptsFromRelatedFor with field id {}, lang {}, input '{}', limit {}", field.getId(), lang, input, limitResults);
            statement.setLong(1, field.getId());
            statement.setLong(2, baseValue.getId());
            statement.setString(3, lang);
            statement.setString(4, input);
            statement.setInt(5, limitResults);

            return processResultSet(field, lang, statement);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for field id {} and related of {} : {}", field.getId(), baseValue.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    private List<ConceptAutocompleteDTO> processResultSet(@NonNull Concept concept, @NonNull String lang, PreparedStatement statement) {
        List<ConceptAutocompleteDTO> results = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ConceptAutocompleteDTO dto = rowToDTO(resultSet, lang);
                addAllAltLabelsToResults(lang, dto, results);
                results.add(dto);
            }
            return results;
        } catch (Exception e) {
            log.error("Error while processing result set for concept autocomplete for concept id {}: {}", concept.getId(), e.getMessage(), e);
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
