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
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThesaurusUsageServiceTest {

    private static final String LANG = "fr";

    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;
    @Mock
    private LabelService labelService;
    @Mock
    private TableFieldConfigService tableFieldConfigService;
    @Mock
    private FormConfigService formConfigService;
    @Mock
    private FieldCodeResolver fieldCodeResolver;
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

    private ConceptFieldFormConfig config(String fieldLabel, Concept valueConcept) {
        ConceptFieldFormConfig config = mock(ConceptFieldFormConfig.class);
        CustomField field = mock(CustomField.class);
        when(field.getLabel()).thenReturn(fieldLabel);
        FormConfig formConfig = mock(FormConfig.class);
        lenient().when(formConfig.getValueConcept()).thenReturn(valueConcept);
        when(config.getField()).thenReturn(field);
        when(config.getFormConfig()).thenReturn(formConfig);
        return config;
    }

    private ConceptFieldFormConfig branchConfig(Vocabulary vocabulary, String topTermLabel, String fieldLabel,
                                                Concept valueConcept) {
        Concept topTerm = concept(topTermLabel);
        when(topTerm.getVocabulary()).thenReturn(vocabulary);
        ConceptFieldFormConfig config = config(fieldLabel, valueConcept);
        when(config.isBranchConfig()).thenReturn(true);
        when(config.getBranchTopTerm()).thenReturn(topTerm);
        return config;
    }

    private ConceptFieldFormConfig collectionConfig(Vocabulary vocabulary, String externalId, String fieldLabel) {
        ConceptCollection collection = mock(ConceptCollection.class);
        when(collection.getVocabulary()).thenReturn(vocabulary);
        when(collection.getExternalId()).thenReturn(externalId);
        ConceptFieldFormConfig config = config(fieldLabel, null);
        when(config.isBranchConfig()).thenReturn(false);
        when(config.getCollection()).thenReturn(collection);
        return config;
    }

    @Test
    void findUsagesOfActionUnit_describesBranchWithItsType() {
        Vocabulary vocabulary = vocabulary("Siamois");
        Concept ceramic = concept("Céramique");
        ConceptFieldFormConfig config = branchConfig(vocabulary, "Matériau", "Matière", ceramic);
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(List.of(config));

        List<ThesaurusUsage> usages = service.findUsagesOfActionUnit(1L, LANG);

        assertEquals(1, usages.size());
        ThesaurusUsage usage = usages.get(0);
        assertEquals("Siamois", usage.getThesaurusLabel());
        assertEquals(FieldVocabularyConfig.Kind.BRANCH, usage.getKind());
        assertEquals("Matériau", usage.getSourceLabel());
        assertEquals("Matière", usage.getFieldLabel());
        assertEquals("Céramique", usage.getTypeLabel());
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

    private TypeFieldsConfig fieldsOf(TypeFieldFormConfig... fields) {
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setFields(new java.util.ArrayList<>(List.of(fields)));
        return config;
    }

    private FormConfig typeFormConfig() {
        FormConfig formConfig = mock(FormConfig.class);
        when(formConfig.getId()).thenReturn(5L);
        return formConfig;
    }

    @Test
    void findAllUsagesOfProject_includesTheFieldsLeftOnTheirDefaultCode() {
        FormConfig formConfig = typeFormConfig();
        Vocabulary siamois = vocabulary("Siamois");
        when(tableFieldConfigService.listTables()).thenReturn(List.of(ConfigurableTable.UE));
        when(tableFieldConfigService.listTypes(1L, ConfigurableTable.UE)).thenReturn(List.of(new TypeSummary("Sondage")));
        when(tableFieldConfigService.findFormConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(java.util.Optional.of(formConfig));
        when(fieldFormConfigRepository.findAllByFormConfigId(5L)).thenReturn(List.of());
        when(tableFieldConfigService.getFieldsConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(fieldsOf(
                TypeFieldFormConfig.builder().id(10L).name("Nature").type(FieldType.SELECT_ONE).sourceLabel("SIAUE.NATURE").build(),
                TypeFieldFormConfig.builder().id(11L).name("Commentaire").type(FieldType.TEXT).sourceLabel("—").build()));
        when(fieldCodeResolver.findDefaultVocabulary(2L, 1L, "SIAUE.NATURE")).thenReturn(java.util.Optional.of(siamois));
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(List.of());

        List<ThesaurusUsage> usages = service.findAllUsagesOfProject(1L, 2L, LANG);

        assertEquals(1, usages.size());
        ThesaurusUsage usage = usages.get(0);
        assertEquals(FieldVocabularyConfig.Kind.FIELD_CODE, usage.getKind());
        assertTrue(usage.isInherited());
        assertEquals("Siamois", usage.getThesaurusLabel());
        assertEquals("SIAUE.NATURE", usage.getFieldCode());
        assertEquals("UE", usage.getTableName());
        assertEquals("Sondage", usage.getTypeName());
        assertEquals("Nature", usage.getFieldLabel());
    }

    @Test
    void findAllUsagesOfProject_showsAStoredFieldCodeInsteadOfTheDefault() {
        FormConfig formConfig = typeFormConfig();
        Vocabulary mine = vocabulary("Mon thésaurus");
        ConceptFieldFormConfig stored = config("Nature", null);
        when(stored.getFormConfig()).thenReturn(formConfig);
        CustomField storedField = stored.getField();
        when(storedField.getId()).thenReturn(10L);
        when(stored.isNotValid()).thenReturn(false);
        when(stored.isBranchConfig()).thenReturn(false);
        when(stored.isFieldCodeConfig()).thenReturn(true);
        when(stored.getVocabulary()).thenReturn(mine);
        when(stored.getFieldCode()).thenReturn("SIAUE.NATURE");
        when(tableFieldConfigService.listTables()).thenReturn(List.of(ConfigurableTable.UE));
        when(tableFieldConfigService.listTypes(1L, ConfigurableTable.UE)).thenReturn(List.of(new TypeSummary("Sondage")));
        when(tableFieldConfigService.findFormConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(java.util.Optional.of(formConfig));
        when(fieldFormConfigRepository.findAllByFormConfigId(5L)).thenReturn(List.of(stored));
        when(tableFieldConfigService.getFieldsConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(fieldsOf(
                TypeFieldFormConfig.builder().id(10L).name("Nature").type(FieldType.SELECT_ONE).sourceLabel("SIAUE.NATURE").build()));
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(List.of(stored));

        List<ThesaurusUsage> usages = service.findAllUsagesOfProject(1L, 2L, LANG);

        assertEquals(1, usages.size());
        assertEquals("Mon thésaurus", usages.get(0).getThesaurusLabel());
        assertEquals(false, usages.get(0).isInherited());
    }

    private ThesaurusUsage codeRow(String field, String code) {
        return new ThesaurusUsage("Siamois", FieldVocabularyConfig.Kind.FIELD_CODE, code, field, "UE · Sondage", "UE", "Sondage", code, true);
    }

    @Test
    void rewireFieldCodes_connectsTheFieldsWhoseCodeTheThesaurusDefines() {
        Vocabulary target = mock(Vocabulary.class);
        when(target.getId()).thenReturn(9L);
        FormConfig formConfig = mock(FormConfig.class);
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        when(fieldCodeResolver.findIn(target, "SIAUE.NATURE")).thenReturn(java.util.Optional.of(mock(ConceptFieldConfig.class)));
        when(fieldCodeResolver.findIn(target, "SIAUE.ABSENT")).thenReturn(java.util.Optional.empty());
        when(fieldCodeResolver.findDefaultVocabulary(2L, 1L, "SIAUE.NATURE")).thenReturn(java.util.Optional.of(mock(Vocabulary.class)));
        when(tableFieldConfigService.findFormConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(java.util.Optional.of(formConfig));
        when(tableFieldConfigService.findField(1L, ConfigurableTable.UE, "Sondage", "Nature")).thenReturn(java.util.Optional.of(field));
        ThesaurusUsage branch = new ThesaurusUsage("Siamois", FieldVocabularyConfig.Kind.BRANCH, "x", "Autre", null, "UE", "Sondage", null, false);

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(codeRow("Nature", "SIAUE.NATURE"), codeRow("Absent", "SIAUE.ABSENT"), branch), target);

        assertEquals(new ThesaurusUsageService.RewireResult(1, 1, 1), result);
        verify(formConfigService).addFieldCodeConfigFor(formConfig, field, target, "SIAUE.NATURE");
    }

    @Test
    void rewireFieldCodes_givesTheFieldBackToItsDefault_whenTheTargetIsItsDefaultThesaurus() {
        Vocabulary target = mock(Vocabulary.class);
        when(target.getId()).thenReturn(9L);
        FormConfig formConfig = mock(FormConfig.class);
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        when(fieldCodeResolver.findIn(target, "SIAUE.NATURE")).thenReturn(java.util.Optional.of(mock(ConceptFieldConfig.class)));
        Vocabulary same = mock(Vocabulary.class);
        when(same.getId()).thenReturn(9L);
        when(fieldCodeResolver.findDefaultVocabulary(2L, 1L, "SIAUE.NATURE")).thenReturn(java.util.Optional.of(same));
        when(tableFieldConfigService.findFormConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(java.util.Optional.of(formConfig));
        when(tableFieldConfigService.findField(1L, ConfigurableTable.UE, "Sondage", "Nature")).thenReturn(java.util.Optional.of(field));

        service.rewireFieldCodes(1L, 2L, List.of(codeRow("Nature", "SIAUE.NATURE")), target);

        verify(formConfigService).clearConceptConfigFor(formConfig, field);
        verify(formConfigService, never()).addFieldCodeConfigFor(any(), any(), any(), any());
    }
}
