package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AutocompleteRepository {

    private final HikariDataSource dataSource;

    private ConceptAutocompleteDTO rowToDTO(ResultSet resultSet) throws SQLException {
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

        List<String> altLabels = List.of(resultSet.getString("data_aggregated_alt_labels").split(";#"));

        return new ConceptAutocompleteDTO(
                prefLabel,
                prefLabel.getLabel(),
                altLabels,
                resultSet.getString("data_definition"),
                resultSet.getString("data_hierarchy_str")
        );
    }

    @NonNull
    public Set<ConceptAutocompleteDTO> findMatchingConceptsFor(@NonNull Concept concept,
                                                               @NonNull String lang,
                                                               @Nullable String input,
                                                               int limit) {
        Set<ConceptAutocompleteDTO> results = new HashSet<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM concept_autocomplete(?, ?, ?, ?)");) {

            statement.setLong(1, concept.getId());
            statement.setString(2, lang);
            statement.setString(3, input != null ? input : "");
            statement.setInt(4, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ConceptAutocompleteDTO dto = rowToDTO(resultSet);
                    addAllAltLabelsToResults(lang, dto, results);
                    results.add(dto);
                }
            }
            return results;
        } catch (SQLException e) {
            log.error("Error while fetching autocomplete results for concept id {}: {}", concept.getId(), e.getMessage(), e);
            return Set.of();
        }

    }

    private static void addAllAltLabelsToResults(String lang, ConceptAutocompleteDTO dto, Set<ConceptAutocompleteDTO> results) {
        Concept currentConcept = dto.conceptLabelToDisplay().getConcept();
        for (String altLabel : dto.altLabels()) {
            ConceptAltLabel unsavedAltLabel = new ConceptAltLabel();
            unsavedAltLabel.setLabel(altLabel);
            unsavedAltLabel.setLangCode(lang);
            unsavedAltLabel.setConcept(currentConcept);

            ConceptAutocompleteDTO altLabelDto = ConceptAutocompleteDTO.builder()
                    .conceptLabelToDisplay(unsavedAltLabel)
                    .originalPrefLabel(dto.originalPrefLabel())
                    .altLabels(dto.altLabels())
                    .definition(dto.definition())
                    .hierarchyPrefLabel(dto.hierarchyPrefLabel())
                    .build();

            results.add(altLabelDto);
        }
    }

}
