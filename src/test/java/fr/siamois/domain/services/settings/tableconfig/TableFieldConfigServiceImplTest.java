package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableFieldConfigServiceImplTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long FIELD_CONCEPT_ID = 100L;
    private static final Long CERAMIQUE_CONCEPT_ID = 200L;
    // Typed as Long so stubs bind to findById(Long) and not to the findById(long) overload of PersonRepository.
    private static final Long PERSON_ID = 2L;
    private static final Long FORM_ID = 500L;

    @Mock private FieldConfigurationService fieldConfigurationService;
    @Mock private LabelService labelService;
    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private FormRepository formRepository;
    @Mock private FormConfigRepository formConfigRepository;
    @Mock private FieldFormConfigRepository fieldFormConfigRepository;
    @Mock private CustomFieldRepository customFieldRepository;
    @Mock private PersonRepository personRepository;

    private TableFieldConfigServiceImpl service;

    private Concept fieldConcept;
    private Concept ceramiqueConcept;
    private FormConfig defaultConfig;
    private FormConfig ceramiqueConfig;

    @BeforeEach
    void setUp() throws Exception {
        service = new TableFieldConfigServiceImpl(fieldConfigurationService, labelService, actionUnitRepository,
                conceptRepository, formRepository, formConfigRepository, fieldFormConfigRepository,
                customFieldRepository, personRepository);

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        PersonDTO person = new PersonDTO();
        person.setId(PERSON_ID);
        ExecutionContextHolder.set(new UserInfo(institution, person, "fr"));

        fieldConcept = concept(FIELD_CONCEPT_ID, "field");
        ceramiqueConcept = concept(CERAMIQUE_CONCEPT_ID, "ceramique");

        ConceptFieldConfig conceptFieldConfig = new ConceptFieldConfig();
        conceptFieldConfig.setConcept(fieldConcept);
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), anyString(), any(Long.class)))
                .thenReturn(conceptFieldConfig);

        defaultConfig = formConfig(10L, null);
        ceramiqueConfig = formConfig(11L, ceramiqueConcept);

        when(formConfigRepository.findDefaultByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(Optional.of(defaultConfig));
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.of(ceramiqueConfig));
        when(conceptRepository.findAllByFieldContextAndExactLabel(FIELD_CONCEPT_ID, "fr", "Céramique"))
                .thenReturn(List.of(ceramiqueConcept));
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    void listTables_shouldExposeTheFourTablesWithTheirTypeFieldCode() {
        assertThat(service.listTables()).containsExactly(
                ConfigurableTable.UE, ConfigurableTable.MOBILIER, ConfigurableTable.PHASE, ConfigurableTable.CONTENANT);
        assertThat(ConfigurableTable.MOBILIER.getFieldCode()).isEqualTo("SIAS.CATEGORY");
    }

    @Test
    void listTypes_shouldOnlyListTheTypesTheProjectConfigured() {
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of(defaultConfig, ceramiqueConfig));
        when(labelService.findLabelOf(ceramiqueConcept, "fr")).thenReturn(prefLabel("Céramique"));

        List<TypeSummary> types = service.listTypes(PROJECT_ID, ConfigurableTable.MOBILIER);

        assertThat(types).extracting(TypeSummary::getName).containsExactly("_default", "Céramique");
        assertThat(types.get(0).isDefault()).isTrue();
    }

    @Test
    void listTypes_shouldHoldOnlyTheDefaultTypeWhenNothingWasConfigured() {
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID)).thenReturn(List.of());

        assertThat(service.listTypes(PROJECT_ID, ConfigurableTable.MOBILIER))
                .extracting(TypeSummary::getName).containsExactly("_default");
    }

    @Test
    void listConfigurableTypes_shouldOfferTheVocabularyValuesThatAreNotConfiguredYet() throws Exception {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CATEGORY"), eq("é"), eq(PROJECT_ID)))
                .thenReturn(List.of(autocomplete("Céramique"), autocomplete("Métal"), autocomplete("Céramique")));
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of(ceramiqueConfig));
        when(labelService.findLabelOf(ceramiqueConcept, "fr")).thenReturn(prefLabel("Céramique"));

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "é"))
                .containsExactly("Métal");
    }

    @Test
    void addConfiguration_shouldCreateTheFormConfigOfTheChosenValue() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        TypeSummary created = service.addConfiguration(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(created.getName()).isEqualTo("Céramique");
        assertThat(created.isDefault()).isFalse();
        ArgumentCaptor<FormConfig> saved = ArgumentCaptor.forClass(FormConfig.class);
        verify(formConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getValueConcept()).isEqualTo(ceramiqueConcept);
    }

    @Test
    void addConfiguration_shouldRefuseTheDefaultType() {
        assertThatThrownBy(() -> service.addConfiguration(PROJECT_ID, ConfigurableTable.MOBILIER, "_default"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(formConfigRepository, never()).save(any(FormConfig.class));
    }

    @Test
    void getFieldsConfig_shouldListTheSystemFieldsOfTheFormEvenWhenNothingIsConfigured() {
        CustomField identifier = textField(1L, "Identifiant", true);
        CustomField description = textField(2L, "Description", true);
        givenFormOf(null, column(identifier, true), column(description, false));
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getName).containsExactly("Identifiant", "Description");
        assertThat(fields).allMatch(TypeFieldFormConfig::isSystemField);
        assertThat(fields).allMatch(TypeFieldFormConfig::isActive);
        // Requiredness falls back on what the form declares while nothing is configured.
        assertThat(fields.get(0).isMandatory()).isTrue();
        assertThat(fields.get(1).isMandatory()).isFalse();
    }

    @Test
    void getFieldsConfig_shouldLetTheStoredConfigurationWinOverTheFormDefaults() {
        CustomField identifier = textField(1L, "Identifiant", true);
        givenFormOf(null, column(identifier, true));
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, identifier, false, false)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).isActive()).isFalse();
        assertThat(fields.get(0).isMandatory()).isFalse();
    }

    @Test
    void getFieldsConfig_shouldAppendTheAdditionalFieldsAfterTheFormOnes() {
        CustomField identifier = textField(1L, "Identifiant", true);
        CustomField additional = textField(9L, "Couleur", false);
        givenFormOf(null, column(identifier, false));
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, additional, true, false)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getName).containsExactly("Identifiant", "Couleur");
        assertThat(fields.get(1).isSystemField()).isFalse();
    }

    @Test
    void setFieldActive_shouldCreateTheRowOfASystemFieldThatWasNeverConfigured() {
        CustomField identifier = textField(1L, "Identifiant", true);
        givenFormOf(null, column(identifier, true));
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        service.setFieldActive(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", "Identifiant", false);

        ArgumentCaptor<FieldFormConfig> saved = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getField()).isEqualTo(identifier);
        assertThat(saved.getValue().getFormConfig()).isEqualTo(defaultConfig);
        assertThat(saved.getValue().isActive()).isFalse();
        // The requiredness the form declared is carried over rather than reset.
        assertThat(saved.getValue().isMandatory()).isTrue();
    }

    @Test
    void getFieldsConfig_shouldOverrideInheritedDefaultFieldsWithTheTypeOwnOnes() {
        CustomField shared = textField(1L, "Description", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, shared, true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, shared, false, true)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique").getFields();

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getName()).isEqualTo("Description");
        assertThat(fields.get(0).isActive()).isFalse();
        assertThat(fields.get(0).isMandatory()).isTrue();
    }

    @Test
    void getFieldsConfig_shouldReadDefaultTypeAlone() {
        CustomField ownField = textField(1L, "Identifiant", true);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, ownField, true, true)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).isSystemField()).isTrue();
        verify(fieldFormConfigRepository, never()).findAllByFormConfigId(11L);
    }

    @Test
    void getFieldsConfig_shouldMapVocabularyFieldsToTheirTypeAndSource() {
        CustomFieldSelectOneFromFieldCode vocabularyField = CustomFieldSelectOneFromFieldCode.builder()
                .id(3L).label("Catégorie").isSystemField(true).fieldCode("SIAS.CATEGORY").build();
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, vocabularyField, true, false)));

        TypeFieldFormConfig field =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields().get(0);

        assertThat(field.getType()).isEqualTo(FieldType.VOCABULAIRE_CONTROLE);
        assertThat(field.isConfigurable()).isTrue();
        assertThat(field.getSourceLabel()).isEqualTo("SIAS.CATEGORY");
    }

    @Test
    void setFieldActive_shouldWriteOnTheTypeOwnConfig() {
        CustomField ownField = textField(1L, "Description", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of());
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, ownField, true, false)));

        service.setFieldActive(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Description", false);

        ArgumentCaptor<FieldFormConfig> saved = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(saved.getValue().getFormConfig()).isEqualTo(ceramiqueConfig);
    }

    @Test
    void setFieldMandatory_shouldCopyAnInheritedFieldOntoTheTypeInsteadOfEditingTheDefault() {
        CustomField inherited = textField(1L, "Description", true);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, inherited, true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of());

        service.setFieldMandatory(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Description", true);

        ArgumentCaptor<FieldFormConfig> saved = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getFormConfig()).isEqualTo(ceramiqueConfig);
        assertThat(saved.getValue().isMandatory()).isTrue();
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void setFieldActive_shouldLeaveAnInstitutionLockedFieldUntouched() {
        FieldFormConfig locked = fieldConfig(defaultConfig, textField(1L, "Identifiant", true), true, true);
        locked.setInstitutionLocked(true);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(locked));

        service.setFieldActive(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", "Identifiant", false);

        verify(fieldFormConfigRepository, never()).save(any(FieldFormConfig.class));
        assertThat(locked.isActive()).isTrue();
    }

    @Test
    void setFieldActive_shouldIgnoreAnUnknownField() {
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        service.setFieldActive(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Inexistant", false);

        verify(fieldFormConfigRepository, never()).save(any(FieldFormConfig.class));
    }

    @Test
    void addAdditionalField_shouldCreateANonSystemTextFieldLinkedToTheType() {
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(new Person()));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> {
            CustomField field = call.getArgument(0);
            field.setId(42L);
            return field;
        });
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class))).thenAnswer(call -> call.getArgument(0));

        TypeFieldFormConfig created = service.addAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(created.getName()).isEqualTo("Nouveau champ");
        assertThat(created.isSystemField()).isFalse();
        assertThat(created.getType()).isEqualTo(FieldType.TEXTE);
        assertThat(created.isActive()).isTrue();

        ArgumentCaptor<FieldFormConfig> link = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(link.capture());
        assertThat(link.getValue().getFormConfig()).isEqualTo(ceramiqueConfig);
    }

    @Test
    void addAdditionalField_shouldSuffixTheNameWhenTheBaseNameIsTaken() {
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, textField(1L, "Nouveau champ", false), true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of());
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(new Person()));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class))).thenAnswer(call -> call.getArgument(0));

        TypeFieldFormConfig created = service.addAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(created.getName()).isEqualTo("Nouveau champ 2");
    }

    @Test
    void deleteAdditionalField_shouldDropTheLinkAndTheNowUnusedField() {
        CustomField additional = textField(5L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, additional, true, false)));
        when(fieldFormConfigRepository.countByFieldId(5L)).thenReturn(0L);

        service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        verify(fieldFormConfigRepository).delete(any(FieldFormConfig.class));
        verify(customFieldRepository).delete(additional);
    }

    @Test
    void deleteAdditionalField_shouldKeepAFieldStillConfiguredElsewhere() {
        CustomField additional = textField(5L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, additional, true, false)));
        when(fieldFormConfigRepository.countByFieldId(5L)).thenReturn(1L);

        service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        verify(fieldFormConfigRepository).delete(any(FieldFormConfig.class));
        verify(customFieldRepository, never()).delete(any(CustomField.class));
    }

    @Test
    void deleteAdditionalField_shouldRefuseToDeleteASystemField() {
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, textField(5L, "Identifiant", true), true, false)));

        service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Identifiant");

        verify(fieldFormConfigRepository, never()).delete(any(FieldFormConfig.class));
        verify(customFieldRepository, never()).delete(any(CustomField.class));
    }

    @Test
    void getFormConfig_shouldReportTheValueConceptLabelOfTheType() {
        when(labelService.findLabelOf(ceramiqueConcept, "fr")).thenReturn(prefLabel("Céramique"));

        var config = service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(config.getTypeName()).isEqualTo("Céramique");
        assertThat(config.getValueConceptLabel()).isEqualTo("Céramique");
        assertThat(config.isInheritsDefaultFields()).isTrue();
    }

    @Test
    void getFormConfig_shouldLeaveTheDefaultTypeWithoutValueConcept() {
        var config = service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default");

        assertThat(config.getValueConceptLabel()).isEmpty();
        assertThat(config.isInheritsDefaultFields()).isFalse();
    }

    @Test
    void saveFormConfig_shouldCreateTheConfigurationWhenTheTypeHasNoneYet() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        service.saveFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER,
                fr.siamois.domain.models.settings.tableconfig.TypeFormConfig.builder().typeName("Céramique").build());

        ArgumentCaptor<FormConfig> saved = ArgumentCaptor.forClass(FormConfig.class);
        verify(formConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getActionUnit()).isEqualTo(project);
        assertThat(saved.getValue().getFieldConcept()).isEqualTo(fieldConcept);
        assertThat(saved.getValue().getValueConcept()).isEqualTo(ceramiqueConcept);
    }

    @Test
    void saveFormConfig_shouldNotCreateAnythingWhenTheConfigurationAlreadyExists() {
        service.saveFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER,
                fr.siamois.domain.models.settings.tableconfig.TypeFormConfig.builder().typeName("Céramique").build());

        verify(formConfigRepository, never()).save(any(FormConfig.class));
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    private Concept concept(Long id, String externalId) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setExternalId(externalId);
        concept.setVocabulary(new Vocabulary());
        return concept;
    }

    private FormConfig formConfig(Long id, Concept valueConcept) {
        FormConfig config = new FormConfig();
        config.setId(id);
        config.setFieldConcept(fieldConcept);
        config.setValueConcept(valueConcept);
        return config;
    }

    private CustomField textField(Long id, String label, boolean systemField) {
        return CustomFieldText.builder().id(id).label(label).isSystemField(systemField).isTextArea(false).build();
    }

    private FieldFormConfig fieldConfig(FormConfig owner, CustomField field, boolean active, boolean mandatory) {
        FieldFormConfig config = new FieldFormConfig();
        config.setField(field);
        config.setFormConfig(owner);
        config.setActive(active);
        config.setMandatory(mandatory);
        return config;
    }

    private ConceptPrefLabel prefLabel(String label) {
        ConceptPrefLabel prefLabel = new ConceptPrefLabel();
        prefLabel.setLabel(label);
        return prefLabel;
    }

    /**
     * Publishes a form laying out the given fields as the one that applies to a type, the way the
     * repository serves it: raw {@code [fieldId, isRequired]} rows plus the fields themselves.
     */
    private void givenFormOf(Long valueConceptId, Object[]... layoutFields) {
        when(formRepository.findEffectiveFormIdByTypeAndInstitution(valueConceptId, 1L))
                .thenReturn(Optional.of(FORM_ID));

        List<Object[]> rows = new ArrayList<>();
        List<CustomField> fields = new ArrayList<>();
        for (Object[] layoutField : layoutFields) {
            CustomField field = (CustomField) layoutField[0];
            rows.add(new Object[]{field.getId(), layoutField[1]});
            fields.add(field);
        }
        when(formRepository.findLayoutFieldsByFormId(FORM_ID)).thenReturn(rows);
        when(customFieldRepository.findAllById(any())).thenReturn(fields);
    }

    private Object[] column(CustomField field, boolean required) {
        return new Object[]{field, required};
    }

    private ConceptAutocompleteDTO autocomplete(String label) {
        fr.siamois.dto.entity.ConceptPrefLabelDTO labelDTO = new fr.siamois.dto.entity.ConceptPrefLabelDTO();
        labelDTO.setLabel(label);
        return ConceptAutocompleteDTO.builder().conceptLabelToDisplay(labelDTO).build();
    }
}
