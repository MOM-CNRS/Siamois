package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.*;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.vocabulary.*;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptCollectionService conceptCollectionService;
    @Mock
    private VocabularyMapper vocabularyMapper;
    @Mock
    private fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository conceptCollectionRepository;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
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
    void findUsagesOfActionUnit_namesTheThesaurusByLabelIdentifierAndInstance() {
        Vocabulary vocabulary = vocabulary("Siamois - Socle commun");
        when(vocabulary.getExternalVocabularyId()).thenReturn("th338");
        when(vocabulary.getBaseUri()).thenReturn("https://thesaurus.mom.fr/");
        ConceptFieldFormConfig config = branchConfig(vocabulary, "Matériau", "Matière", concept("Céramique"));
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(List.of(config));

        List<ThesaurusUsage> usages = service.findUsagesOfActionUnit(1L, LANG);

        assertEquals("Siamois - Socle commun (th338) - https://thesaurus.mom.fr", usages.get(0).getThesaurusLabel());
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
        return ThesaurusUsage.builder().thesaurusLabel("Siamois").kind(FieldVocabularyConfig.Kind.FIELD_CODE).sourceLabel(code).fieldLabel(field).typeLabel("UE · Sondage").tableName("UE").typeName("Sondage").fieldCode(code).inherited(true).build();
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
        ThesaurusUsage branch = ThesaurusUsage.builder().thesaurusLabel("Siamois").kind(FieldVocabularyConfig.Kind.BRANCH).sourceLabel("x").fieldLabel("Autre").typeLabel(null).tableName("UE").typeName("Sondage").fieldCode(null).inherited(false).build();

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

    private ThesaurusUsage namedRow(FieldVocabularyConfig.Kind kind, String field) {
        return ThesaurusUsage.builder().thesaurusLabel("Ancien").kind(kind).sourceLabel("x").fieldLabel(field).typeLabel("UE · Sondage").tableName("UE").typeName("Sondage").fieldCode(null).inherited(false).build();
    }

    private ConceptAutocompleteDetachedDTO remoteConcept(String externalId, String label, boolean alt) {
        ConceptLabelDTO labelDTO = alt ? new ConceptAltLabelDTO() : new ConceptPrefLabelDTO();
        labelDTO.setLabel(label);
        labelDTO.setConcept(ConceptDTO.builder().externalId(externalId).build());
        return new ConceptAutocompleteDetachedDTO(labelDTO, label, List.of(), "", "uri");
    }

    /** A field of the UE type "Sondage" whose stored source carries the given names. */
    private record Named(FormConfig formConfig, CustomFieldConcept field, VocabularyDTO targetDto) {
    }

    private Named namedField(String branchName, String collectionName) {
        FormConfig formConfig = mock(FormConfig.class);
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        VocabularyDTO targetDto = new VocabularyDTO();
        ConceptFieldFormConfig stored = new ConceptFieldFormConfig();
        stored.setBranchName(branchName);
        stored.setCollectionName(collectionName);
        when(tableFieldConfigService.findFormConfig(1L, ConfigurableTable.UE, "Sondage")).thenReturn(java.util.Optional.of(formConfig));
        when(tableFieldConfigService.findField(1L, ConfigurableTable.UE, "Sondage", "Matière")).thenReturn(java.util.Optional.of(field));
        when(formConfigService.findConceptConfigFor(formConfig, field)).thenReturn(java.util.Optional.of(stored));
        when(vocabularyMapper.convert(any())).thenReturn(targetDto);
        return new Named(formConfig, field, targetDto);
    }

    @Test
    void rewire_movesABranchToTheConceptOfTheSameName() throws Exception {
        Named n = namedField("Matière", null);
        when(conceptService.fetchAutocompleteFromRemoteThesaurus(n.targetDto(), "Matière")).thenReturn(List.of(
                remoteConcept("12", "matière", false), remoteConcept("13", "Matière première", false), remoteConcept("14", "Matière", true)));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.BRANCH, "Matière")), mock(Vocabulary.class));

        assertEquals(new ThesaurusUsageService.RewireResult(1, 0, 0), result);
        verify(formConfigService).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class),
                argThat((ConceptDTO c) -> "12".equals(c.getExternalId())));
        verify(formConfigService).rememberSourceName(n.formConfig(), n.field(), "Matière");
    }

    @Test
    void rewire_leavesABranchAlone_whenTwoConceptsShareTheName() throws Exception {
        Named n = namedField("Matière", null);
        when(conceptService.fetchAutocompleteFromRemoteThesaurus(n.targetDto(), "Matière")).thenReturn(List.of(
                remoteConcept("12", "Matière", false), remoteConcept("15", "Matière", false)));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.BRANCH, "Matière")), mock(Vocabulary.class));

        assertEquals(new ThesaurusUsageService.RewireResult(0, 0, 1), result);
        verify(formConfigService, never()).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), any(ConceptDTO.class));
    }

    @Test
    void rewire_leavesABranchAlone_whenThereIsNoStoredName() {
        namedField(null, null);

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.BRANCH, "Matière")), mock(Vocabulary.class));

        assertEquals(new ThesaurusUsageService.RewireResult(0, 0, 1), result);
        verify(formConfigService, never()).rememberSourceName(any(), any(), any());
    }

    @Test
    void rewire_movesACollectionToTheOneOfTheSameNameInAnyLanguage() {
        Named n = namedField(null, "Mobilier · Classe — Métal");
        ConceptCollectionDetachedDTO other = new ConceptCollectionDetachedDTO("g1", n.targetDto(), "Autre", List.of());
        ConceptCollectionDetachedDTO metal = new ConceptCollectionDetachedDTO("g2", n.targetDto(), "Class — Metal",
                List.of(new LabelDTO("fr", "mobilier · classe — métal")));
        when(conceptCollectionService.fetchCollectionsFromRemoteThesaurus(n.targetDto())).thenReturn(List.of(other, metal));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.COLLECTION, "Matière")), mock(Vocabulary.class));

        assertEquals(new ThesaurusUsageService.RewireResult(1, 0, 0), result);
        verify(formConfigService).addConceptConfigFor(n.formConfig(), n.field(), metal);
        verify(formConfigService).rememberSourceName(n.formConfig(), n.field(), "Mobilier · Classe — Métal");
    }

    @Test
    void rewire_leavesACollectionAlone_whenTheThesaurusHasNoneOfThatName() {
        Named n = namedField(null, "Introuvable");
        when(conceptCollectionService.fetchCollectionsFromRemoteThesaurus(n.targetDto())).thenReturn(
                List.of(new ConceptCollectionDetachedDTO("g1", n.targetDto(), "Autre", List.of())));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.COLLECTION, "Matière")), mock(Vocabulary.class));

        assertEquals(new ThesaurusUsageService.RewireResult(0, 0, 1), result);
        verify(formConfigService, never()).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), any(ConceptCollectionDetachedDTO.class));
    }

    private void secondField(String fieldLabel, String branchName, String collectionName) {
        FormConfig formConfig = mock(FormConfig.class);
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        ConceptFieldFormConfig stored = new ConceptFieldFormConfig();
        stored.setBranchName(branchName);
        stored.setCollectionName(collectionName);
        when(tableFieldConfigService.findFormConfig(1L, ConfigurableTable.UE, "Autre type")).thenReturn(java.util.Optional.of(formConfig));
        when(tableFieldConfigService.findField(1L, ConfigurableTable.UE, "Autre type", fieldLabel)).thenReturn(java.util.Optional.of(field));
        when(formConfigService.findConceptConfigFor(formConfig, field)).thenReturn(java.util.Optional.of(stored));
    }

    private ThesaurusUsage rowOf(FieldVocabularyConfig.Kind kind, String type, String field) {
        return ThesaurusUsage.builder().thesaurusLabel("Ancien").kind(kind).sourceLabel("x").fieldLabel(field).typeLabel("UE · " + type).tableName("UE").typeName(type).fieldCode(null).inherited(false).build();
    }

    @Test
    void rewire_importsASharedBranchOnce() throws Exception {
        Named n = namedField("Matière", null);
        secondField("Matière", "Matière", null);
        when(conceptService.fetchAutocompleteFromRemoteThesaurus(n.targetDto(), "Matière"))
                .thenReturn(List.of(remoteConcept("12", "Matière", false)));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.BRANCH, "Matière"), rowOf(FieldVocabularyConfig.Kind.BRANCH, "Autre type", "Matière")),
                mock(Vocabulary.class));

        assertEquals(new ThesaurusUsageService.RewireResult(2, 0, 0), result);
        verify(conceptService, times(1)).fetchAutocompleteFromRemoteThesaurus(any(), any());
        verify(formConfigService, times(1)).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), any(ConceptDTO.class));
        verify(formConfigService, times(1)).addConceptConfigForLoadedBranch(any(), any(), any());
    }

    @Test
    void rewire_importsASharedCollectionOnce() {
        Named n = namedField(null, "Classe");
        secondField("Matière", null, "Classe");
        ConceptCollectionDetachedDTO collection = new ConceptCollectionDetachedDTO("g2", n.targetDto(), "Classe", List.of());
        when(conceptCollectionService.fetchCollectionsFromRemoteThesaurus(n.targetDto())).thenReturn(List.of(collection));
        ConceptCollection imported = mock(ConceptCollection.class);
        Vocabulary target = mock(Vocabulary.class);
        when(conceptCollectionRepository.findByVocabularyAndExternalId(target, "g2")).thenReturn(java.util.Optional.of(imported));

        service.rewireFieldCodes(1L, 2L, List.of(namedRow(FieldVocabularyConfig.Kind.COLLECTION, "Matière"),
                rowOf(FieldVocabularyConfig.Kind.COLLECTION, "Autre type", "Matière")), target);

        verify(formConfigService, times(1)).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), any(ConceptCollectionDetachedDTO.class));
        verify(formConfigService, times(1)).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), eq(imported));
    }

    @Test
    void rewire_keepsTheOtherFields_whenOneCannotBeMoved() throws Exception {
        Named n = namedField("Matière", null);
        secondField("Matière", "Matière", null);
        when(conceptService.fetchAutocompleteFromRemoteThesaurus(n.targetDto(), "Matière"))
                .thenReturn(List.of(remoteConcept("12", "Matière", false)));
        doThrow(new IllegalArgumentException("import failed"))
                .doNothing()
                .when(formConfigService).addConceptConfigFor(any(FormConfig.class), any(CustomFieldConcept.class), any(ConceptDTO.class));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(1L, 2L,
                List.of(namedRow(FieldVocabularyConfig.Kind.BRANCH, "Matière"), rowOf(FieldVocabularyConfig.Kind.BRANCH, "Autre type", "Matière")),
                mock(Vocabulary.class));

        // the first import failed, so the branch was not marked imported and the second field imports it itself
        assertEquals(new ThesaurusUsageService.RewireResult(1, 0, 1), result);
    }

    private ThesaurusUsage storedCodeRow(String field, String code, Long formConfigId, Long fieldId) {
        return ThesaurusUsage.builder().thesaurusLabel("Ancien").kind(FieldVocabularyConfig.Kind.FIELD_CODE).sourceLabel(code).fieldLabel(field).typeLabel(null).tableName(null).typeName(null).fieldCode(code).inherited(false).thesaurusUrl("https://old/?idt=th1").formConfigId(formConfigId).fieldId(fieldId).build();
    }

    @Test
    void rewireFieldCodes_movesAStoredFieldOfAnOrganisation_throughTheIdsOfItsRow() {
        Vocabulary target = mock(Vocabulary.class);
        FormConfig formConfig = mock(FormConfig.class);
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        when(field.getId()).thenReturn(77L);
        ConceptFieldFormConfig stored = mock(ConceptFieldFormConfig.class);
        when(stored.getField()).thenReturn(field);
        when(stored.getFormConfig()).thenReturn(formConfig);
        when(fieldFormConfigRepository.findAllByFormConfigId(5L)).thenReturn(List.<fr.siamois.domain.models.form.config.FieldFormConfig>of(stored));
        when(fieldCodeResolver.findIn(target, "SIAS.CLASS")).thenReturn(java.util.Optional.of(mock(ConceptFieldConfig.class)));
        when(fieldCodeResolver.findDefaultVocabulary(2L, null, "SIAS.CLASS")).thenReturn(java.util.Optional.empty());

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(null, 2L,
                List.of(storedCodeRow("Classe", "SIAS.CLASS", 5L, 77L)), target);

        assertEquals(new ThesaurusUsageService.RewireResult(1, 0, 0), result);
        verify(formConfigService).addFieldCodeConfigFor(formConfig, field, target, "SIAS.CLASS");
    }

    @Test
    void rewireFieldCodes_ignoresARowWithoutIdsOrTable_whenThereIsNoProject() {
        ThesaurusUsage inherited = ThesaurusUsage.builder().thesaurusLabel("Siamois").kind(FieldVocabularyConfig.Kind.FIELD_CODE).sourceLabel("SIAS.CLASS").fieldLabel("Classe").typeLabel(null).tableName(null).typeName(null).fieldCode("SIAS.CLASS").inherited(true).build();

        Vocabulary target = mock(Vocabulary.class);
        when(fieldCodeResolver.findIn(target, "SIAS.CLASS")).thenReturn(java.util.Optional.of(mock(ConceptFieldConfig.class)));

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(null, 2L, List.of(inherited), target);

        assertEquals(new ThesaurusUsageService.RewireResult(0, 0, 1), result);
        verify(formConfigService, never()).addFieldCodeConfigFor(any(), any(), any(), any());
    }

    @Test
    void findUsagesOfInstitution_namesTheProjectEachSourceBelongsTo() {
        Vocabulary vocabulary = vocabulary("Pactols");
        ConceptFieldFormConfig config = collectionConfig(vocabulary, "col-42", "Période");
        ActionUnit project = mock(ActionUnit.class);
        when(project.getId()).thenReturn(7L);
        when(project.getName()).thenReturn("Fouille A");
        when(config.getFormConfig().getActionUnit()).thenReturn(project);
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfInstitution(2L)).thenReturn(List.of(config));

        ThesaurusUsage usage = service.findUsagesOfInstitution(2L, LANG).get(0);

        assertEquals("Fouille A", usage.getProjectName());
        assertEquals(7L, usage.getProjectId());
    }

    @Test
    void rowKey_tellsTwoProjectsStoredSourcesApart() {
        ThesaurusUsage a = ThesaurusUsage.builder().thesaurusLabel("T").kind(FieldVocabularyConfig.Kind.FIELD_CODE).sourceLabel("SIAS.CLASS").fieldLabel("Classe").typeLabel(null).tableName(null).typeName(null).fieldCode("SIAS.CLASS").inherited(false).thesaurusUrl("u").formConfigId(5L).fieldId(77L).build();
        ThesaurusUsage b = ThesaurusUsage.builder().thesaurusLabel("T").kind(FieldVocabularyConfig.Kind.FIELD_CODE).sourceLabel("SIAS.CLASS").fieldLabel("Classe").typeLabel(null).tableName(null).typeName(null).fieldCode("SIAS.CLASS").inherited(false).thesaurusUrl("u").formConfigId(6L).fieldId(77L).build();

        assertNotEquals(a.getRowKey(), b.getRowKey());
    }

    @Test
    void rewireFieldCodes_readsTheDefaultThesaurusOfTheProjectTheRowBelongsTo() {
        Vocabulary target = mock(Vocabulary.class);
        FormConfig formConfig = mock(FormConfig.class);
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        when(field.getId()).thenReturn(77L);
        ConceptFieldFormConfig stored = mock(ConceptFieldFormConfig.class);
        when(stored.getField()).thenReturn(field);
        when(stored.getFormConfig()).thenReturn(formConfig);
        when(fieldFormConfigRepository.findAllByFormConfigId(5L)).thenReturn(List.<fr.siamois.domain.models.form.config.FieldFormConfig>of(stored));
        when(fieldCodeResolver.findIn(target, "SIAS.CLASS")).thenReturn(java.util.Optional.of(mock(ConceptFieldConfig.class)));
        when(fieldCodeResolver.findDefaultVocabulary(2L, 7L, "SIAS.CLASS")).thenReturn(java.util.Optional.empty());
        ThesaurusUsage row = ThesaurusUsage.builder().thesaurusLabel("Ancien").kind(FieldVocabularyConfig.Kind.FIELD_CODE).sourceLabel("SIAS.CLASS").fieldLabel("Classe").typeLabel(null).tableName(null).typeName(null).fieldCode("SIAS.CLASS").inherited(false).thesaurusUrl("https://old/?idt=th1").formConfigId(5L).fieldId(77L).projectId(7L).projectName("Fouille A").build();

        ThesaurusUsageService.RewireResult result = service.rewireFieldCodes(null, 2L, List.of(row), target);

        assertEquals(new ThesaurusUsageService.RewireResult(1, 0, 0), result);
        verify(fieldCodeResolver).findDefaultVocabulary(2L, 7L, "SIAS.CLASS");
    }

    @Test
    void findUsagesOfActionUnit_namesAThesaurusWhoseLabelIsEmptyByItsIdentifier() {
        Vocabulary vocabulary = mock(Vocabulary.class);
        when(vocabulary.getExternalVocabularyId()).thenReturn("th338");
        when(labelService.findLabelOf(vocabulary, LANG)).thenReturn(new VocabularyLabel());
        ConceptFieldFormConfig config = branchConfig(vocabulary, "Matériau", "Matière", concept("Céramique"));
        when(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(1L)).thenReturn(List.of(config));

        assertEquals("th338", service.findUsagesOfActionUnit(1L, LANG).get(0).getThesaurusLabel());
    }
}
