package fr.siamois.ui.api.openapi.v1.resource.concept;

import fr.siamois.dto.entity.vocabulary.ConceptAltLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedConceptResourceTest {

    private static ConceptDTO concept(boolean withVocabulary) {
        ConceptDTO c = new ConceptDTO();
        c.setId(7L);
        c.setExternalId("42");
        if (withVocabulary) {
            VocabularyDTO v = new VocabularyDTO();
            v.setBaseUri("https://opentheso.example");
            v.setExternalVocabularyId("th1");
            c.setVocabulary(v);
        }
        return c;
    }

    private static ConceptAutocompleteDTO dto(ConceptLabelDTO label, ConceptDTO c, String parents) {
        label.setConcept(c);
        label.setLabel("Label");
        return new ConceptAutocompleteDTO(label, "Pref", List.of("a"), "Def", parents);
    }

    @Test
    void from_prefLabel_hasThesaurusUrlAndNoPrefLabel() {
        ResolvedConceptResource r = ResolvedConceptResource.from(dto(new ConceptPrefLabelDTO(), concept(true), "P1 > P2"));

        assertThat(r.getThesaurusUrl()).isEqualTo("https://opentheso.example/?idc=42&idt=th1");
        assertThat(r.getPrefLabel()).isNull();
        assertThat(r.getParents()).isEqualTo("P1 > P2");
    }

    @Test
    void from_altLabel_carriesOriginalPrefLabel() {
        ResolvedConceptResource r = ResolvedConceptResource.from(dto(new ConceptAltLabelDTO(), concept(true), ""));

        assertThat(r.getPrefLabel()).isEqualTo("Pref");
        assertThat(r.getParents()).isNull();
    }

    @Test
    void from_noVocabulary_hasNoThesaurusUrl() {
        ResolvedConceptResource r = ResolvedConceptResource.from(dto(new ConceptPrefLabelDTO(), concept(false), null));

        assertThat(r.getThesaurusUrl()).isNull();
        assertThat(r.getId()).isEqualTo("7");
    }
}
