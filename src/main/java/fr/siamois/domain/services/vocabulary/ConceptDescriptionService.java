package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Describes known concepts the way a concept suggestion shows them (label, alt labels, definition,
 * parents and the vocabulary that links to the thesaurus), for a picker that offers a fixed list of
 * concepts instead of searching the thesaurus — the types a project declares, for instance.
 */
@Service
@RequiredArgsConstructor
public class ConceptDescriptionService {

    private final AutocompleteRepository autocompleteRepository;

    /**
     * Describes the given concepts in the given language.
     *
     * @param concepts the concepts to describe
     * @param lang     the language code of the labels
     * @return the description of each concept found, by concept id; a concept with no label in the
     * language has none
     */
    @NonNull
    public Map<Long, ConceptAutocompleteDTO> describeAll(@NonNull Collection<Concept> concepts, @NonNull String lang) {
        List<Long> conceptIds = concepts.stream()
                .map(Concept::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return autocompleteRepository.findConceptsByIds(conceptIds, lang).stream()
                .collect(Collectors.toMap(this::conceptIdOf, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }

    private Long conceptIdOf(ConceptAutocompleteDTO description) {
        return Objects.requireNonNull(description.concept()).getId();
    }
}
