package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThesaurusUsageServiceTest {

    private static final String LANG = "fr";

    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;
    @Mock
    private LabelService labelService;
    @InjectMocks
    private ThesaurusUsageService service;

    private Vocabulary vocabulary(String label) {
        Vocabulary vocabulary = mock(Vocabulary.class);
        VocabularyLabel vocabularyLabel = new VocabularyLabel();
        vocabularyLabel.setValue(label);
        when(labelService.findLabelOf(vocabulary, LANG)).thenReturn(vocabularyLabel);
        return vocabulary;
    }

    private Concept concept(String label) {
        Concept concept = mock(Concept.class);
        ConceptPrefLabel conceptLabel = new ConceptPrefLabel();
        conceptLabel.setLabel(label);
        when(labelService.findLabelOf(concept, LANG)).thenReturn(conceptLabel);
        return concept;
    }

    private ConceptFieldFormConfig config(String fieldLabel, Concept valueConcept, boolean frozen) {
        ConceptFieldFormConfig config = mock(ConceptFieldFormConfig.class);
        CustomField field = mock(CustomField.class);
        when(field.getLabel()).thenReturn(fieldLabel);
        FormConfig formConfig = mock(FormConfig.class);
        when(formConfig.getValueConcept()).thenReturn(valueConcept);
        when(config.getField()).thenReturn(field);
        when(config.getFormConfig()).thenReturn(formConfig);
        when(config.isFrozen()).thenReturn(frozen);
        return config;
    }

    private ConceptFieldFormConfig branchConfig(Vocabulary vocabulary, String topTermLabel, String fieldLabel,
                                                Concept valueConcept, boolean frozen) {
        Concept topTerm = concept(topTermLabel);
        when(topTerm.getVocabulary()).thenReturn(vocabulary);
        ConceptFieldFormConfig config = config(fieldLabel, valueConcept, frozen);
        when(config.isBranchConfig()).thenReturn(true);
        when(config.getBranchTopTerm()).thenReturn(topTerm);
        return config;
    }

    private ConceptFieldFormConfig collectionConfig(Vocabulary vocabulary, String externalId, String fieldLabel) {
        ConceptCollection collection = mock(ConceptCollection.class);
        when(collection.getVocabulary()).thenReturn(vocabulary);
        when(collection.getExternalId()).thenReturn(externalId);
        ConceptFieldFormConfig config = config(fieldLabel, null, false);
        when(config.isBranchConfig()).thenReturn(false);
        when(config.getCollection()).thenReturn(collection);
        return config;
    }

    @Test
    void findUsagesOfActionUnit_describesBranchWithItsType() {
        Vocabulary vocabulary = vocabulary("Siamois");
        Concept ceramic = concept("Céramique");
        ConceptFieldFormConfig config = branchConfig(vocabulary, "Matériau", "Matière", ceramic, true);
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(List.of(config));

        List<ThesaurusUsage> usages = service.findUsagesOfActionUnit(1L, LANG);

        assertEquals(1, usages.size());
        ThesaurusUsage usage = usages.get(0);
        assertEquals("Siamois", usage.getThesaurusLabel());
        assertEquals(FieldVocabularyConfig.Kind.BRANCH, usage.getKind());
        assertEquals("Matériau", usage.getSourceLabel());
        assertEquals("Matière", usage.getFieldLabel());
        assertEquals("Céramique", usage.getTypeLabel());
        assertTrue(usage.isFrozen());
    }

    @Test
    void findUsagesOfInstitution_describesCollectionWithoutType() {
        Vocabulary vocabulary = vocabulary("Pactols");
        ConceptFieldFormConfig config = collectionConfig(vocabulary, "col-42", "Période");
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfInstitution(2L)).thenReturn(List.of(config));

        List<ThesaurusUsage> usages = service.findUsagesOfInstitution(2L, LANG);

        assertEquals(1, usages.size());
        ThesaurusUsage usage = usages.get(0);
        assertEquals(FieldVocabularyConfig.Kind.COLLECTION, usage.getKind());
        assertEquals("col-42", usage.getSourceLabel());
        assertNull(usage.getTypeLabel());
        assertFalse(usage.isFrozen());
    }

    @Test
    void findUsages_sortsByThesaurusThenField() {
        Vocabulary b = vocabulary("B thesaurus");
        Vocabulary a = vocabulary("A thesaurus");
        List<ConceptFieldFormConfig> configs = List.of(
                collectionConfig(b, "c1", "Zeta"),
                collectionConfig(a, "c2", "Beta"),
                collectionConfig(a, "c3", "Alpha"));
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(configs);

        List<ThesaurusUsage> usages = service.findUsagesOfActionUnit(1L, LANG);

        assertEquals(List.of("Alpha", "Beta", "Zeta"), usages.stream().map(ThesaurusUsage::getFieldLabel).toList());
    }

    @Test
    void findUsages_emptyWhenNothingConfigured() {
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfInstitution(3L)).thenReturn(List.of());

        assertTrue(service.findUsagesOfInstitution(3L, LANG).isEmpty());
    }
}
