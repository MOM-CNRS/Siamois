package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConceptLabelBatchResolverTest {

    private final ConceptLabelRepository labels = mock(ConceptLabelRepository.class);
    private final ConceptLabelBatchResolver resolver = new ConceptLabelBatchResolver(labels);

    private static ConceptDTO dto(long id, String externalId) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        c.setExternalId(externalId);
        return c;
    }

    private static Concept concept(long id) {
        Concept c = new Concept();
        c.setId(id);
        return c;
    }

    private static ConceptPrefLabel pref(long conceptId, String label) {
        ConceptPrefLabel l = new ConceptPrefLabel();
        l.setConcept(concept(conceptId));
        l.setLabel(label);
        return l;
    }

    private static ConceptAltLabel alt(long conceptId, String label) {
        ConceptAltLabel l = new ConceptAltLabel();
        l.setConcept(concept(conceptId));
        l.setLabel(label);
        return l;
    }

    @Test
    void nothingToResolveAsksNothing() {
        assertThat(resolver.resolveLabels(Arrays.asList(null, null), "fr")).isEmpty();
        verify(labels, never()).findAllPrefLabelsByLangCodeAndConcept_IdIn(anyString(), any());
    }

    @Test
    void preferredThenAlternativeThenExternalIdFallback() {
        when(labels.findAllPrefLabelsByLangCodeAndConcept_IdIn(eq("en"), any())).thenReturn(List.of(pref(1, "Wall")));
        when(labels.findAllAltLabelsByLangCodeAndConcept_IdIn(eq("en"), any())).thenReturn(List.of(alt(2, "Ditch"), alt(1, "ignored")));

        Map<Long, String> out = resolver.resolveLabels(
                Arrays.asList(dto(1, "a"), dto(2, "b"), dto(3, "c"), null, dto(1, "dup")), " EN ");

        assertThat(out).containsEntry(1L, "Wall").containsEntry(2L, "Ditch").containsEntry(3L, "[c]");
    }

    @Test
    void theAlternativeQueryIsSkippedWhenEveryConceptHasAPreferredLabel() {
        when(labels.findAllPrefLabelsByLangCodeAndConcept_IdIn(eq("fr"), any())).thenReturn(List.of(pref(1, "Mur")));

        assertThat(resolver.resolveLabels(Set.of(dto(1, "a")), null)).containsEntry(1L, "Mur");
        verify(labels, never()).findAllAltLabelsByLangCodeAndConcept_IdIn(anyString(), any());
    }

    @Test
    void aLabelWithoutConceptIsIgnored() {
        ConceptPrefLabel orphan = new ConceptPrefLabel();
        orphan.setLabel("x");
        when(labels.findAllPrefLabelsByLangCodeAndConcept_IdIn(anyString(), any())).thenReturn(List.of(orphan));

        assertThat(resolver.resolveLabels(List.of(dto(1, "a")), "fr")).containsEntry(1L, "[a]");
    }

    @Test
    void labelOfFallsBackToTheExternalId() {
        assertThat(ConceptLabelBatchResolver.labelOf(null, Map.of())).isNull();
        assertThat(ConceptLabelBatchResolver.labelOf(dto(1, "a"), Map.of(1L, "Mur"))).isEqualTo("Mur");
        assertThat(ConceptLabelBatchResolver.labelOf(dto(2, "b"), Map.of())).isEqualTo("[b]");
    }
}
