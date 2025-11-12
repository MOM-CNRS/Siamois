package fr.siamois.infrastructure.database.repositories.vocabulary;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class AutocompleteRepository {

    private final HikariDataSource dataSource;

    @NonNull
    public Set<ConceptAutocompleteDTO> findMatchingConceptsFor(@NonNull Concept concept,
                                                               @NonNull String lang,
                                                               @Nullable String input,
                                                               int limit) {
        Set<ConceptAutocompleteDTO> results = new HashSet<>();

        // Récupéer une ligne = 1 concept, 1 prefLabel dans la langue, n altLabels, 1 definition, 1 string pour la hiérarchie
        Set<ConceptAutocompleteDTO> fetchedResults = new HashSet<>();

        // Pour chaque concept, pour chacun de ses altLabels dans la langue, on crée un ConceptAutocompleteDTO et on l'ajoute
        for (ConceptAutocompleteDTO dto : fetchedResults) {
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
            results.add(dto);
        }

        // Renvoie
        return results;
    }

}
