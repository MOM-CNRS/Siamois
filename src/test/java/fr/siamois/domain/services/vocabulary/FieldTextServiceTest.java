package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldTextServiceTest {

    private final ConceptRepository conceptRepository = mock(ConceptRepository.class);
    private final ConceptLabelRepository labelRepository = mock(ConceptLabelRepository.class);
    private final LocalizedConceptDataRepository dataRepository = mock(LocalizedConceptDataRepository.class);
    private final FieldTextService service = new FieldTextService(conceptRepository, labelRepository, dataRepository);

    private static CustomField fieldWithConcept(String externalId) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setBaseUri("https://thesaurus.mom.fr");
        vocabulary.setExternalVocabularyId("th337");
        CustomField field = new CustomFieldText();
        field.setConcept(new Concept.Builder().vocabulary(vocabulary).externalId(externalId).build());
        return field;
    }

    private void storeConcept(String externalId, String label, String definition) {
        Concept stored = new Concept();
        stored.setId(7L);
        ConceptPrefLabel pref = new ConceptPrefLabel();
        pref.setLabel(label);
        when(conceptRepository.findConceptByExternalIdIgnoreCase("th337", externalId)).thenReturn(Optional.of(stored));
        when(labelRepository.findPrefLabelByLangCodeAndConcept(eq("fr"), any())).thenReturn(Optional.of(pref));
        LocalizedConceptData data = new LocalizedConceptData();
        data.setDefinition(definition);
        when(dataRepository.findByConceptAndLangCode(7L, "fr")).thenReturn(Optional.ofNullable(definition == null ? null : data));
    }

    @Test
    void conceptLabelAndDefinitionWinOverTheFieldsOwn() {
        storeConcept("1172", "Type d'UE", "Nature de l'unité.");

        var texts = service.resolve(fieldWithConcept("1172"), "fr", "Type", "aide du champ");

        assertThat(texts.label()).isEqualTo("Type d'UE");
        assertThat(texts.definition()).isEqualTo("Nature de l'unité.");
    }

    @Test
    void fieldWithoutConceptKeepsItsOwnLabelAndHint() {
        var texts = service.resolve(new CustomFieldText(), "fr", "Type", "aide du champ");

        assertThat(texts.label()).isEqualTo("Type");
        assertThat(texts.definition()).isEqualTo("aide du champ");
    }

    @Test
    void conceptAbsentFromTheDatabaseFallsBackToTheField() {
        when(conceptRepository.findConceptByExternalIdIgnoreCase(any(), any())).thenReturn(Optional.empty());

        var texts = service.resolve(fieldWithConcept("4291999"), "fr", "Géométrie", null);

        assertThat(texts.label()).isEqualTo("Géométrie");
        assertThat(texts.definition()).isNull();
    }

    @Test
    void conceptWithoutDefinitionKeepsTheFieldHintButUsesItsLabel() {
        storeConcept("1173", "Identifiant", null);

        var texts = service.resolve(fieldWithConcept("1173"), "fr", "Id", "aide");

        assertThat(texts.label()).isEqualTo("Identifiant");
        assertThat(texts.definition()).isEqualTo("aide");
    }

    @Test
    void definitionIsReducedToPlainText() {
        storeConcept("1174", "Auteur", "<strong>UE :</strong></p><p>Nom &amp;amp; prénom</p>");

        var texts = service.resolve(fieldWithConcept("1174"), "fr", "Auteur", null);

        assertThat(texts.definition()).isEqualTo("UE : Nom & prénom");
    }

    @Test
    void sameConceptIsLookedUpOnlyOnce() {
        storeConcept("1175", "Contributeurs", "Personnes.");

        service.resolve(fieldWithConcept("1175"), "fr", "x", null);
        service.resolve(fieldWithConcept("1175"), "fr", "x", null);

        verify(conceptRepository, times(1)).findConceptByExternalIdIgnoreCase("th337", "1175");
        verify(dataRepository, times(1)).findByConceptAndLangCode(anyLong(), eq("fr"));
    }
}
