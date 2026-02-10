package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
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

        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setId(resultSet.getLong("vocabulary_id"));
        vocabulary.setType(type);
        vocabulary.setBaseUri(resultSet.getString("vocabulary_base_uri"));
        vocabulary.setExternalVocabularyId(resultSet.getString("vocabulary_external_id"));

        Concept concept = new Concept();
        concept.setId(resultSet.getLong("concept_id"));
        concept.setVocabulary(vocabulary);
        concept.setDeleted(false);
        concept.setExternalId(resultSet.getString("concept_external_id"));

        Concept parentConcept = new Concept();
        parentConcept.setId(resultSet.getLong("parent_concept_id"));
        parentConcept.setVocabulary(vocabulary);
        parentConcept.setDeleted(false);
        parentConcept.setExternalId(resultSet.getString("parent_concept_external_id"));

        ConceptPrefLabel prefLabel = new ConceptPrefLabel();
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
     * @param concept The field concept to find matches for
     * @param lang    The language code to filter results
     * @param input   The input string to match against concept labels
     * @param limit   The maximum number of results to return
     * @return A list of ConceptAutocompleteDTO containing matching concepts
     */
    @NonNull
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> findMatchingConceptsFor(@NonNull Concept concept,
                                                                @NonNull String lang,
                                                                @Nullable String input,
                                                                int limit) {
        List<ConceptAutocompleteDTO> results = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ca.* FROM concept_autocomplete(?, ?, ?, ?) ca")) {
            log.trace("Executing concept autocomplete with concept id {}, lang {}, input '{}', limit {}", concept.getId(), lang, input, limit);
            statement.setLong(1, concept.getId());
            statement.setString(2, lang);
            statement.setString(3, input != null ? input : "");
            statement.setInt(4, limit);

            return processResultSet(concept, lang, statement, results);
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for concept id {}: {}", concept.getId(), e.getMessage(), e);
            return List.of();
        }

    }

    private List<ConceptAutocompleteDTO> processResultSet(@NonNull Concept concept, @NonNull String lang, PreparedStatement statement, List<ConceptAutocompleteDTO> results) {
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
        Concept currentConcept = dto.getConceptLabelToDisplay().getConcept();
        for (String altLabel : dto.getAltLabels()) {
            ConceptAltLabel unsavedAltLabel = new ConceptAltLabel();
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
