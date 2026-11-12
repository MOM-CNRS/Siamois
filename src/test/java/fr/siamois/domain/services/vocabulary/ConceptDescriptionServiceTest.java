package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.AutocompleteRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptDescriptionServiceTest {

    @Mock
    private AutocompleteRepository autocompleteRepository;

    @InjectMocks
    private ConceptDescriptionService conceptDescriptionService;

    @Test
    void describeAll_indexesEachDescriptionByItsConceptId() {
        Concept first = conceptWithId(1L);
        Concept second = conceptWithId(2L);
        ConceptAutocompleteDTO firstDescription = descriptionOf(1L, "Couche");
        ConceptAutocompleteDTO secondDescription = descriptionOf(2L, "Fosse");
        when(autocompleteRepository.findConceptsByIds(List.of(1L, 2L), "fr"))
                .thenReturn(List.of(firstDescription, secondDescription));

        Map<Long, ConceptAutocompleteDTO> descriptions = conceptDescriptionService.describeAll(List.of(first, second, first), "fr");

        assertThat(descriptions).containsExactly(Map.entry(1L, firstDescription), Map.entry(2L, secondDescription));
    }

    @Test
    void describeAll_conceptWithoutLabelInTheLanguage_hasNoDescription() {
        when(autocompleteRepository.findConceptsByIds(List.of(1L), "en")).thenReturn(List.of());

        assertThat(conceptDescriptionService.describeAll(List.of(conceptWithId(1L)), "en")).isEmpty();
    }

    private static Concept conceptWithId(Long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }

    private static ConceptAutocompleteDTO descriptionOf(Long conceptId, String label) {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(conceptId);
        ConceptPrefLabelDTO prefLabel = new ConceptPrefLabelDTO();
        prefLabel.setConcept(concept);
        prefLabel.setLabel(label);
        return new ConceptAutocompleteDTO(prefLabel, label, List.of(), null, null);
    }
}
