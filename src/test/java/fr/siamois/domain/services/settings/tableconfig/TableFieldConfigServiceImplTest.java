package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.*;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableFieldConfigServiceImplTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long FIELD_CONCEPT_ID = 100L;
    private static final Long CERAMIQUE_CONCEPT_ID = 200L;
    private static final Long PERSON_ID = 2L;
    private static final Long FORM_ID = 500L;

    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private LabelService labelService;
    @Mock
    private ActionUnitRepository actionUnitRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private FormRepository formRepository;
    @Mock
    private FormConfigRepository formConfigRepository;
    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;
    @Mock
    private CustomFieldRepository customFieldRepository;
    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private TableFieldConfigServiceImpl service;

    private Concept fieldConcept;
    private Concept ceramiqueConcept;
    private FormConfig defaultConfig;
    private FormConfig ceramiqueConfig;
    private InstitutionDTO institution;
    private PersonDTO person;

    @BeforeEach
    void setUp() throws Exception {
        institution = new InstitutionDTO();
        institution.setId(1L);
        person = new PersonDTO();
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

    // ========== Existing Tests ==========

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
    void getActiveAdditionalFields_shouldReturnOnlyActiveNonSystemFields() {
        CustomField identifier = textField(1L, "Identifiant", true);
        CustomField activeAdditional = textField(9L, "Couleur", false);
        CustomField inactiveAdditional = textField(8L, "Poids", false);
        givenFormOf(null, column(identifier, true));
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(
                fieldConfig(defaultConfig, activeAdditional, true, false),
                fieldConfig(defaultConfig, inactiveAdditional, false, false)
        ));

        List<CustomField> fields =
                service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "_default");

        assertThat(fields).containsExactly(activeAdditional);
    }

    @Test
    void getActiveAdditionalFields_shouldReturnEmptyListWhenOnlySystemFieldsExist() {
        CustomField identifier = textField(1L, "Identifiant", true);
        givenFormOf(null, column(identifier, true));
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<CustomField> fields =
                service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "_default");

        assertThat(fields).isEmpty();
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

        assertThat(field.getType()).isEqualTo(FieldType.SELECT_ONE);
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
                TypeFormConfig.builder().typeName("Céramique").build());

        ArgumentCaptor<FormConfig> saved = ArgumentCaptor.forClass(FormConfig.class);
        verify(formConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getActionUnit()).isEqualTo(project);
        assertThat(saved.getValue().getFieldConcept()).isEqualTo(fieldConcept);
        assertThat(saved.getValue().getValueConcept()).isEqualTo(ceramiqueConcept);
    }

    @Test
    void saveFormConfig_shouldNotCreateAnythingWhenTheConfigurationAlreadyExists() {
        service.saveFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER,
                TypeFormConfig.builder().typeName("Céramique").build());

        verify(formConfigRepository, never()).save(any(FormConfig.class));
    }

    // ========== New Tests ==========

    // --- listConfigurableTypes ---
    @Test
    void listConfigurableTypes_shouldReturnEmptyListWhenNoMatches() throws NoConfigForFieldException {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CATEGORY"), eq("xyz"), eq(PROJECT_ID)))
                .thenReturn(List.of());
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of());

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "xyz"))
                .isEmpty();
    }

    @Test
    void listConfigurableTypes_shouldReturnEmptyListWhenInputIsNull() throws NoConfigForFieldException {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CATEGORY"), isNull(), eq(PROJECT_ID)))
                .thenReturn(List.of());
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of());

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, null))
                .isEmpty();
    }

    @Test
    void listConfigurableTypes_shouldHandleDuplicatesInAutocomplete() throws NoConfigForFieldException {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CATEGORY"), eq("é"), eq(PROJECT_ID)))
                .thenReturn(List.of(autocomplete("Céramique"), autocomplete("Céramique"), autocomplete("Métal")));
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of(ceramiqueConfig));
        when(labelService.findLabelOf(ceramiqueConcept, "fr")).thenReturn(prefLabel("Céramique"));

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "é"))
                .containsExactly("Métal");
    }

    // --- saveFormConfig ---
    @Test
    void saveFormConfig_shouldThrowWhenTypeNameIsInvalid() {
        TypeFormConfig config = TypeFormConfig.builder().typeName("Inconnu").build();

        assertThatThrownBy(() -> service.saveFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, config))
                .isInstanceOf(NoSuchElementException.class);
    }

    // --- searchFieldCatalog ---
    @Test
    void searchFieldCatalog_shouldReturnEmptyListWhenQueryIsNull() {
        assertThat(service.searchFieldCatalog(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", null)).isEmpty();
    }

    @Test
    void searchFieldCatalog_shouldReturnEmptyListWhenQueryIsEmpty() {
        assertThat(service.searchFieldCatalog(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "   ")).isEmpty();
    }

    @Test
    void searchFieldCatalog_shouldFilterByLabelAndHint() {
        CustomField field1 = textField(1L, "Couleur", false);
        field1.setHint("Description de la couleur");
        CustomField field2 = textField(2L, "Matériau", false);
        field2.setHint("Type de matériau");
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(field1, field2));

        List<FieldCatalogEntry> results = service.searchFieldCatalog(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "couleur");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Couleur");
        assertThat(results.get(0).getDescription()).isEqualTo("Description de la couleur");
    }

    @Test
    void searchFieldCatalog_shouldExcludeSystemFields() {
        CustomField customField = textField(2L, "Couleur", false);
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(customField));

        List<FieldCatalogEntry> results = service.searchFieldCatalog(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Identifiant");

        assertThat(results).isEmpty();
    }

    @Test
    void searchFieldCatalog_shouldHandleNullLabelOrHint() {
        CustomField field1 = textField(1L, "Couleur", false);
        field1.setHint(null);
        CustomField field2 = textField(2L, null, false);
        field2.setHint("Description");
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(field1, field2));

        List<FieldCatalogEntry> results = service.searchFieldCatalog(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Couleur");
    }

    @Test
    void searchFieldCatalog_shouldOnlyOfferTheFieldsOfTheCurrentInstitution() {
        InstitutionDTO otherInstitution = new InstitutionDTO();
        otherInstitution.setId(2L);
        ExecutionContextHolder.set(new UserInfo(otherInstitution, person, "fr"));
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(textField(1L, "Couleur", false)));
        when(customFieldRepository.findAllReusableByInstitution(2L))
                .thenReturn(List.of(textField(2L, "Matériau", false)));

        List<FieldCatalogEntry> results = service.searchFieldCatalog(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", null);

        assertThat(results).extracting(FieldCatalogEntry::getName).containsExactly("Matériau");
    }

    @Test
    void searchFieldCatalog_shouldNotOfferFieldsAlreadyConfiguredOnTheType() {
        CustomField couleur = textField(1L, "Couleur", false);
        CustomField materiau = textField(2L, "Matériau", false);
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(couleur, materiau));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, couleur, true, false)));

        List<FieldCatalogEntry> results = service.searchFieldCatalog(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", null);

        assertThat(results).extracting(FieldCatalogEntry::getName).containsExactly("Matériau");
    }

    @Test
    void searchFieldCatalog_shouldNotOfferFieldsInheritedFromTheDefaultConfiguration() {
        CustomField couleur = textField(1L, "Couleur", false);
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(couleur));
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, couleur, true, false)));

        assertThat(service.searchFieldCatalog(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", null))
                .isEmpty();
    }

    @Test
    void searchFieldCatalog_shouldNotOfferFieldsTheFormAlreadyLaysOut() {
        CustomField couleur = textField(1L, "Couleur", false);
        givenFormOf(null, column(couleur, false));
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(couleur));

        assertThat(service.searchFieldCatalog(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", null))
                .isEmpty();
    }




    @Test
    void createField_shouldThrowWhenTypeNameIsInvalid() {
        assertThatThrownBy(() ->
                service.createField(PROJECT_ID, ConfigurableTable.MOBILIER, "Inconnu", "Nouveau champ", FieldType.TEXT, "Description"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // --- addExistingField ---
    @Test
    void addExistingField_shouldThrowWhenFieldNotFound() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.of(ceramiqueConfig));
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                service.addExistingField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Inconnu"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void addExistingField_shouldReturnExistingFieldIfAlreadyPresent() {
        CustomField existingField = textField(1L, "Couleur", false);
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.of(ceramiqueConfig));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, existingField, true, false)));
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(existingField));

        TypeFieldFormConfig result = service.addExistingField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(result.getName()).isEqualTo("Couleur");
        verify(fieldFormConfigRepository, never()).save(any());
    }

    @Test
    void addExistingField_shouldAddFieldToType() {
        CustomField existingField = textField(1L, "Couleur", false);
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.of(ceramiqueConfig));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of());
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(existingField));
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class)))
                .thenAnswer(call -> call.getArgument(0));

        TypeFieldFormConfig result = service.addExistingField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(result.getName()).isEqualTo("Couleur");
        verify(fieldFormConfigRepository).save(any(FieldFormConfig.class));
    }

    // --- updateField ---
    @Test
    void updateField_shouldReturnNullForSystemField() {
        CustomField systemField = textField(1L, "Identifiant", true);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, systemField, true, false)));

        assertThat(service.updateField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Identifiant", "Nouveau nom", FieldType.TEXT, "Desc"))
                .isNull();
    }

    @Test
    void updateField_shouldReturnNullWhenFieldNotFound() {
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of());

        assertThat(service.updateField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Inconnu", "Nouveau nom", FieldType.TEXT, "Desc"))
                .isNull();
    }




    // --- deleteAdditionalField ---
    @Test
    void deleteAdditionalField_shouldDoNothingWhenFieldNotFound() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.of(ceramiqueConfig));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of());

        service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Inconnu");

        verify(fieldFormConfigRepository, never()).delete(any());
        verify(customFieldRepository, never()).delete(any());
    }

    @Test
    void deleteAdditionalField_shouldDoNothingWhenTypeNotFound() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());

        service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Inconnu", "Couleur");

        verify(fieldFormConfigRepository, never()).delete(any());
        verify(customFieldRepository, never()).delete(any());
    }


    // --- Edge Cases ---
    @Test
    void listTypes_shouldUseEnglishLabels() {
        ExecutionContextHolder.set(new UserInfo(institution, person, "en"));
        when(labelService.findLabelOf(ceramiqueConcept, "en")).thenReturn(prefLabel("Ceramic"));
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of(defaultConfig, ceramiqueConfig));

        List<TypeSummary> types = service.listTypes(PROJECT_ID, ConfigurableTable.MOBILIER);

        assertThat(types).extracting(TypeSummary::getName).containsExactly("_default", "Ceramic");
    }


    @Test
    void getFieldsConfig_shouldMapAllFieldTypesCorrectly() {
        CustomFieldSelectOneFromFieldCode selectField = CustomFieldSelectOneFromFieldCode.builder()
                .id(3L).label("Catégorie").isSystemField(true).fieldCode("SIAS.CATEGORY").build();
        givenFormOf(null, column(selectField, false));

        List<TypeFieldFormConfig> fields = service.getFieldsConfig(
                PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getType()).isEqualTo(FieldType.SELECT_ONE);
        assertThat(fields.get(0).getSourceLabel()).isEqualTo("SIAS.CATEGORY");
    }

    @Test
    void getFieldsConfig_shouldHandleEmptyForm() {
        when(formRepository.findEffectiveFormIdByTypeAndInstitution(any(), anyLong()))
                .thenReturn(Optional.empty());

        List<TypeFieldFormConfig> fields = service.getFieldsConfig(
                PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).isEmpty();
    }

    // --- createField ---
    @Test
    void createField_shouldCreateAndLinkANewTextField() {
        givenCurrentPersonExists();
        CustomField saved = textField(42L, "Nouveau champ", false);
        when(customFieldRepository.save(any(CustomField.class))).thenReturn(saved);
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class)))
                .thenAnswer(call -> call.getArgument(0));

        TypeFieldFormConfig result = service.createField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Nouveau champ", FieldType.TEXT, "Une description");

        assertThat(result.getName()).isEqualTo("Nouveau champ");
        assertThat(result.getType()).isEqualTo(FieldType.TEXT);
        ArgumentCaptor<CustomField> savedField = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository).save(savedField.capture());
        assertThat(savedField.getValue()).isInstanceOf(CustomFieldText.class);
        assertThat(savedField.getValue().getLabel()).isEqualTo("Nouveau champ");
        assertThat(savedField.getValue().getIsSystemField()).isFalse();
        assertThat(savedField.getValue().getHint()).isEqualTo("Une description");
        ArgumentCaptor<FieldFormConfig> savedLink = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(savedLink.capture());
        assertThat(savedLink.getValue().getFormConfig()).isEqualTo(ceramiqueConfig);
        assertThat(savedLink.getValue().isActive()).isTrue();
        assertThat(savedLink.getValue().isMandatory()).isFalse();
        assertThat(savedLink.getValue().isInstitutionLocked()).isFalse();
    }

    @Test
    void createField_shouldInstantiateTheCustomFieldSubclassMatchingEachType() {
        givenCurrentPersonExists();
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class)))
                .thenAnswer(call -> call.getArgument(0));

        assertThat(service.createField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                "Champ", FieldType.INTEGER, "").getType()).isEqualTo(FieldType.INTEGER);
        assertThat(service.createField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                "Champ", FieldType.MEASUREMENT, "").getType()).isEqualTo(FieldType.MEASUREMENT);
        assertThat(service.createField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                "Champ", FieldType.SELECT_ONE, "").getType()).isEqualTo(FieldType.SELECT_ONE);
        assertThat(service.createField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                "Champ", FieldType.SELECT_MULTIPLE, "").getType()).isEqualTo(FieldType.SELECT_MULTIPLE);
    }

    // --- updateField ---
    @Test
    void updateField_shouldUpdateTheCustomFieldInPlaceWhenTypeIsUnchanged() {
        CustomField existing = textField(1L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, existing, true, false)));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        TypeFieldFormConfig result = service.updateField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur", "Teinte", FieldType.TEXT, "Desc");

        assertThat(result.getName()).isEqualTo("Teinte");
        assertThat(existing.getLabel()).isEqualTo("Teinte");
        assertThat(existing.getHint()).isEqualTo("Desc");
        verify(customFieldRepository).save(existing);
        verify(fieldFormConfigRepository, never()).delete(any());
    }

    @Test
    void updateField_shouldReplaceTheFieldAndDeleteTheOldOneWhenTypeChangesAndOwnedByThisType() {
        givenCurrentPersonExists();
        CustomField existing = textField(1L, "Couleur", false);
        FieldFormConfig existingLink = fieldConfig(ceramiqueConfig, existing, true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(existingLink));
        CustomField replacement = CustomFieldInteger.builder().id(2L).label("Couleur").build();
        when(customFieldRepository.save(any(CustomField.class))).thenReturn(replacement);
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(fieldFormConfigRepository.countByFieldId(1L)).thenReturn(0L);

        TypeFieldFormConfig result = service.updateField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur", "Teinte", FieldType.INTEGER, "Desc");

        assertThat(result.getType()).isEqualTo(FieldType.INTEGER);
        verify(fieldFormConfigRepository).delete(existingLink);
        verify(customFieldRepository).delete(existing);
        ArgumentCaptor<CustomField> newFieldCaptor = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository).save(newFieldCaptor.capture());
        assertThat(newFieldCaptor.getValue()).isInstanceOf(CustomFieldInteger.class);
        assertThat(newFieldCaptor.getValue().getLabel()).isEqualTo("Teinte");
    }

    @Test
    void updateField_shouldKeepTheOldFieldWhenStillReferencedElsewhereAfterTypeChange() {
        givenCurrentPersonExists();
        CustomField existing = textField(1L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, existing, true, false)));
        CustomField replacement = CustomFieldInteger.builder().id(2L).label("Couleur").build();
        when(customFieldRepository.save(any(CustomField.class))).thenReturn(replacement);
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(fieldFormConfigRepository.countByFieldId(1L)).thenReturn(1L);

        service.updateField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur", "Teinte", FieldType.INTEGER, "Desc");

        verify(customFieldRepository, never()).delete(any(CustomField.class));
    }

    @Test
    void updateField_shouldNotDeleteAnInheritedFieldsLinkWhenTypeChanges() {
        givenCurrentPersonExists();
        CustomField inherited = textField(1L, "Description", true);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, inherited, true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of());
        CustomField replacement = CustomFieldInteger.builder().id(2L).label("Description").build();
        when(customFieldRepository.save(any(CustomField.class))).thenReturn(replacement);
        when(fieldFormConfigRepository.save(any(FieldFormConfig.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(fieldFormConfigRepository.countByFieldId(1L)).thenReturn(1L);

        service.updateField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Description", "Desc2", FieldType.INTEGER, "");

        verify(fieldFormConfigRepository, never()).delete(any(FieldFormConfig.class));
    }

    // --- createFormConfig / findFieldConcept / findValueConcept edge cases ---
    @Test
    void addConfiguration_shouldThrowWhenProjectHasNoFieldConfiguration() throws Exception {
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), anyString(), any(Long.class)))
                .thenThrow(new NoConfigForFieldException("no config"));
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.addConfiguration(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addConfiguration_shouldRefuseAnAmbiguousTypeName() {
        when(conceptRepository.findAllByFieldContextAndExactLabel(FIELD_CONCEPT_ID, "fr", "Céramique"))
                .thenReturn(List.of(ceramiqueConcept, concept(201L, "ceramique-bis")));
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.addConfiguration(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Céramique");
    }

    // --- listConfigurableTypes / fieldValues ---
    @Test
    void listConfigurableTypes_shouldReturnEmptyListWhenProjectHasNoFieldConfiguration() throws Exception {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CATEGORY"), any(), eq(PROJECT_ID)))
                .thenThrow(new NoConfigForFieldException("no config"));

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "é")).isEmpty();
    }

    // --- typeOf ---
    @Test
    void getFieldsConfig_shouldMapSpatialAndRecordingUnitFields() {
        CustomFieldSelectOneSpatialUnit spatialField = CustomFieldSelectOneSpatialUnit.builder()
                .id(4L).label("Localisation").isSystemField(true).build();
        CustomFieldSelectOneRecordingUnit recordingField = CustomFieldSelectOneRecordingUnit.builder()
                .id(5L).label("Unité").isSystemField(true).build();
        givenFormOf(null, column(spatialField, false), column(recordingField, false));

        List<TypeFieldFormConfig> fields = service.getFieldsConfig(
                PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getType)
                .containsExactly(FieldType.SELECT_ONE_SPATIAL_UNIT, FieldType.SELECT_ONE_RECORDING_UNIT);
    }

    @Test
    void getFieldsConfig_shouldMapMultipleSelectFieldsAndDashUnsetSources() {
        CustomFieldSelectMultipleFromFieldCode vocabularyField = CustomFieldSelectMultipleFromFieldCode.builder()
                .id(6L).label("Matériaux").isSystemField(true).fieldCode("  ").build();
        givenFormOf(null, column(vocabularyField, false));

        TypeFieldFormConfig field = service.getFieldsConfig(
                PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields().get(0);

        assertThat(field.getType()).isEqualTo(FieldType.SELECT_MULTIPLE);
        assertThat(field.getSourceLabel()).isEqualTo("—");
    }

    // --- currentUser / currentPerson ---
    @Test
    void getFormConfig_shouldThrowWhenNoUserIsBoundToTheThread() {
        ExecutionContextHolder.clear();

        assertThatThrownBy(() -> service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createField_shouldThrowWhenAuthenticatedUserIsNotAKnownPerson() {
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createField(
                PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Champ", FieldType.TEXT, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    private void givenCurrentPersonExists() {
        Person author = new Person();
        author.setId(PERSON_ID);
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(author));
    }

    // ========== Helper Methods ==========

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