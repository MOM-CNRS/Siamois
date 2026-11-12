package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeConceptDescriptionsTest {

    @Test
    void resourceOf_describedConcept_carriesItsAltLabelsDefinitionAndParents() {
        ConceptDTO concept = conceptWithId(42L);
        ConceptPrefLabelDTO label = new ConceptPrefLabelDTO();
        label.setConcept(concept);
        label.setLabel("Couche");
        TypeConceptDescriptions descriptions = TypeConceptDescriptions.of(Map.of(42L,
                new ConceptAutocompleteDTO(label, "Couche", List.of("US"), "Une couche", "Unité")));

        ResolvedConceptResource resource = descriptions.resourceOf(concept, c -> new ResolvedConceptResource());

        assertThat(resource.getId()).isEqualTo("42");
        assertThat(resource.getResolvedLabel()).isEqualTo("Couche");
        assertThat(resource.getAltLabels()).containsExactly("US");
        assertThat(resource.getDefinition()).isEqualTo("Une couche");
        assertThat(resource.getParents()).isEqualTo("Unité");
    }

    @Test
    void resourceOf_conceptWithoutDescription_isTheBareResource() {
        ResolvedConceptResource bare = new ResolvedConceptResource().setResolvedLabel("[1234]");

        ResolvedConceptResource resource = TypeConceptDescriptions.of(Map.of()).resourceOf(conceptWithId(7L), c -> bare);

        assertThat(resource).isSameAs(bare);
    }

    private static ConceptDTO conceptWithId(Long id) {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(id);
        return concept;
    }
}
