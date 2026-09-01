package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionCode;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableFieldConfigServiceImplTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long FIELD_CONCEPT_ID = 100L;
    private static final Long CERAMIQUE_CONCEPT_ID = 200L;
    private static final Long PERSON_ID = 2L;
    private static final String IDENTIFIER_FIELD = "recordingunit.field.identifier";
    private static final String CATEGORY_FIELD = "specimen.field.category";
    private static final String MATERIAL_FIELD = "specimen.field.material";
    private static final String RECORDING_UNIT_FIELD = "specimen.field.recordingUnit";
    private static final String AUTHORS_FIELD = "specimen.field.authors";
    /** Kept clear of the ids the tests give their own fields, so the two never collide. */
    private static final long SYSTEM_FIELD_FIRST_ID = 100L;

    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private LabelService labelService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ActionUnitRepository actionUnitRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private FormConfigRepository formConfigRepository;
    @Mock
    private FieldFormConfigRepository fieldFormConfigRepository;
    @Mock
    private CustomFieldRepository customFieldRepository;
    @Mock
    private CustomFieldAnswerRepository customFieldAnswerRepository;
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
        assertThat(ConfigurableTable.MOBILIER.getFieldCode()).isEqualTo("SIAS.CAT");
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
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CAT"), eq("é"), eq(PROJECT_ID)))
                .thenReturn(List.of(autocomplete("Céramique"), autocomplete("Métal"), autocomplete("Céramique")));
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of(ceramiqueConfig));
        when(labelService.findLabelOf(ceramiqueConcept, "fr")).thenReturn(prefLabel("Céramique"));

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "é"))
                .containsExactly("Métal");
    }

    /**
     * When the table's own type field is itself a known system field (a {@code CustomFieldConceptFromFieldCode}
     * whose field code matches {@link ConfigurableTable#getFieldCode()} — true for {@code UE}, whose
     * "recordingunit.property.type" field carries {@code RecordingUnit.TYPE_FIELD_CODE}), a branch/collection
     * restriction configured on it through the field-settings drawer must be honored — so the search goes
     * through the field-entity overload, not the field-code one.
     */
    @Test
    void listConfigurableTypes_shouldUseTheFieldEntityOverload_whenTheTypeFieldIsAKnownSystemField() throws Exception {
        givenSystemFieldsOf(ConfigurableTable.UE, "recordingunit.property.type");
        when(fieldConfigurationService.fetchAutocomplete(any(CustomFieldConcept.class), eq("é"), eq(PROJECT_ID)))
                .thenReturn(List.of(autocomplete("Céramique")));
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of());

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.UE, "é"))
                .containsExactly("Céramique");
        verify(fieldConfigurationService, never()).fetchAutocomplete(any(UserInfo.class), anyString(), any(), any());
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
    void getFieldsConfig_shouldListTheSystemFieldsOfTheTableEvenWhenNothingIsConfigured() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD, CATEGORY_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getName)
                .containsExactly(IDENTIFIER_FIELD, CATEGORY_FIELD);
        assertThat(fields).allMatch(TypeFieldFormConfig::isSystemField);
        assertThat(fields).allMatch(TypeFieldFormConfig::isActive);
        // Mandatory falls back on the requiredness SpecimenDetailsForm's real panel declares for
        // each field — neither the identifier nor the category column is marked required there.
        assertThat(fields.get(0).isMandatory()).isFalse();
        assertThat(fields.get(1).isMandatory()).isFalse();
    }

    /**
     * The definition lays the fields out in an order of its own, which is the one the screen shows
     * them in — not the order the rows come back from the database in.
     */
    @Test
    void getFieldsConfig_shouldFollowTheOrderOfTheTableDefinitionRatherThanTheOneOfTheRows() {
        Map<String, CustomField> fields = givenSystemFieldsOf(
                ConfigurableTable.MOBILIER, CATEGORY_FIELD, IDENTIFIER_FIELD, MATERIAL_FIELD);
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(
                fields.get(MATERIAL_FIELD), fields.get(CATEGORY_FIELD), fields.get(IDENTIFIER_FIELD)));
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .extracting(TypeFieldFormConfig::getName)
                .containsExactly(IDENTIFIER_FIELD, CATEGORY_FIELD, MATERIAL_FIELD);
    }

    /**
     * A field defined in code after the last startup has no row yet, so no configuration could be
     * saved for it; the screen leaves it out rather than offering a switch that cannot be written.
     */
    @Test
    void getFieldsConfig_shouldSkipTheSystemFieldsThatHaveNoRowYet() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .extracting(TypeFieldFormConfig::getName).containsExactly(IDENTIFIER_FIELD);
    }

    @Test
    void getFieldsConfig_shouldHoldNoSystemFieldWhenTheyWereNeverInitialized() {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .isEmpty();
    }

    /**
     * Every table reads the same pool of rows, so each must take its own fields out of it: a row
     * belonging to Mobilier (its category field) must not leak into UE's field list.
     */
    @Test
    void getFieldsConfig_shouldOnlyListTheSystemFieldsOfTheTableItIsAskedAbout() {
        String ueIdentifierLabel = "common.label.identifier";
        CustomField ueIdentifier = SystemFieldCatalog.fieldsOf(ConfigurableTable.UE).stream()
                .filter(field -> ueIdentifierLabel.equals(field.getLabel()))
                .findFirst().orElseThrow();
        ueIdentifier.setId(SYSTEM_FIELD_FIRST_ID);
        CustomField mobilierCategory = SystemFieldCatalog.fieldsOf(ConfigurableTable.MOBILIER).stream()
                .filter(field -> CATEGORY_FIELD.equals(field.getLabel()))
                .findFirst().orElseThrow();
        mobilierCategory.setId(SYSTEM_FIELD_FIRST_ID + 1);
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(ueIdentifier, mobilierCategory));
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, "_default").getFields())
                .extracting(TypeFieldFormConfig::getName).containsExactly(ueIdentifierLabel);
    }

    @Test
    void getFieldsConfig_shouldLetTheStoredConfigurationWinOverTheFormDefaults() {
        CustomField identifier = givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD).get(IDENTIFIER_FIELD);
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
        CustomField additional = textField(9L, "Couleur", false);
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, additional, true, false)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getName).containsExactly(IDENTIFIER_FIELD, "Couleur");
        assertThat(fields.get(1).isSystemField()).isFalse();
    }

    @Test
    void getActiveAdditionalFields_shouldReturnOnlyActiveNonSystemFields() {
        CustomField activeAdditional = textField(9L, "Couleur", false);
        CustomField inactiveAdditional = textField(8L, "Poids", false);
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
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
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<CustomField> fields =
                service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "_default");

        assertThat(fields).isEmpty();
    }

    @Test
    void findField_shouldResolveASystemFieldByName() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of());

        Optional<CustomField> field = service.findField(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", IDENTIFIER_FIELD);

        assertThat(field).isPresent();
        assertThat(field.get().getLabel()).isEqualTo(IDENTIFIER_FIELD);
    }

    @Test
    void findField_shouldResolveAnAdditionalFieldByName() {
        CustomField additional = textField(9L, "Couleur", false);
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, additional, true, false)));

        Optional<CustomField> field = service.findField(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", "Couleur");

        assertThat(field).contains(additional);
    }

    @Test
    void findField_shouldReturnEmptyWhenNoFieldMatchesTheName() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of());

        Optional<CustomField> field = service.findField(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", "Unknown");

        assertThat(field).isEmpty();
    }

    @Test
    void setFieldActive_shouldCreateTheRowOfASystemFieldThatWasNeverConfigured() {
        CustomField identifier = givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD).get(IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        service.setFieldActive(PROJECT_ID, ConfigurableTable.MOBILIER, "_default", IDENTIFIER_FIELD, false);

        ArgumentCaptor<FieldFormConfig> saved = ArgumentCaptor.forClass(FieldFormConfig.class);
        verify(fieldFormConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getField()).isEqualTo(identifier);
        assertThat(saved.getValue().getFormConfig()).isEqualTo(defaultConfig);
        assertThat(saved.getValue().isActive()).isFalse();
        // Falls back on SpecimenDetailsForm's real requiredness for the identifier column: false.
        assertThat(saved.getValue().isMandatory()).isFalse();
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
    void findFormConfig_shouldReturnTheDefaultConfig() {
        Optional<FormConfig> result = service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default");

        assertThat(result).contains(defaultConfig);
    }

    @Test
    void findFormConfig_shouldReturnTheTypeOwnConfig() {
        Optional<FormConfig> result = service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(result).contains(ceramiqueConfig);
    }

    @Test
    void findFormConfig_shouldReturnEmptyWhenTypeHasNoConfiguration() {
        when(conceptRepository.findAllByFieldContextAndExactLabel(FIELD_CONCEPT_ID, "fr", "Métal"))
                .thenReturn(List.of());

        Optional<FormConfig> result = service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Métal");

        assertThat(result).isEmpty();
    }

    // ========== Concept-id-keyed overloads ==========
    // These skip the label round-trip (Concept -> label -> re-resolved Concept) that the
    // String-keyed overloads above go through via findValueConcept/conceptRepository. They must
    // never touch labelService/conceptRepository at all.

    @Test
    void findFormConfig_byId_shouldReturnTheDefaultConfigWhenTypeConceptIdIsNull() {
        Optional<FormConfig> result = service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, (Long) null);

        assertThat(result).contains(defaultConfig);
        verifyNoInteractions(labelService);
        verify(conceptRepository, never()).findAllByFieldContextAndExactLabel(any(), any(), any());
    }

    @Test
    void findFormConfig_byId_shouldReturnTheTypeOwnConfig() {
        Optional<FormConfig> result = service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID);

        assertThat(result).contains(ceramiqueConfig);
        verifyNoInteractions(labelService);
        verify(conceptRepository, never()).findAllByFieldContextAndExactLabel(any(), any(), any());
    }

    @Test
    void findFormConfig_byId_shouldReturnEmptyWhenTypeHasNoConfiguration() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, 999L))
                .thenReturn(Optional.empty());

        Optional<FormConfig> result = service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void getFieldsConfig_byId_shouldListTheSystemFieldsOfTheTableEvenWhenNothingIsConfigured() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD, CATEGORY_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, (Long) null).getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getName)
                .containsExactly(IDENTIFIER_FIELD, CATEGORY_FIELD);
        verifyNoInteractions(labelService);
    }

    @Test
    void getFieldsConfig_byId_shouldOverrideInheritedDefaultFieldsWithTheTypeOwnOnes() {
        CustomField shared = textField(1L, "Description", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, shared, true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, shared, false, true)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID).getFields();

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getName()).isEqualTo("Description");
        assertThat(fields.get(0).isActive()).isFalse();
        assertThat(fields.get(0).isMandatory()).isTrue();
        verifyNoInteractions(labelService);
    }

    @Test
    void getActiveAdditionalFields_byId_shouldReturnOnlyActiveNonSystemFields() {
        CustomField activeAdditional = textField(9L, "Couleur", false);
        CustomField inactiveAdditional = textField(8L, "Poids", false);
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(
                fieldConfig(defaultConfig, activeAdditional, true, false),
                fieldConfig(defaultConfig, inactiveAdditional, false, false)
        ));

        List<CustomField> fields =
                service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, (Long) null);

        assertThat(fields).containsExactly(activeAdditional);
        verifyNoInteractions(labelService);
    }

    @Test
    void getActiveAdditionalFields_byId_shouldReadFieldsFromTheGivenTypeConceptId() {
        CustomField additional = textField(9L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of());
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, additional, true, false)));

        List<CustomField> fields =
                service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID);

        assertThat(fields).containsExactly(additional);
    }

    @Test
    void createOrGetFormConfig_shouldReturnTheConfigurationTheTypeAlreadyHas() {
        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(result).contains(ceramiqueConfig);
        verify(formConfigRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfig_shouldMaterializeTheConfigurationOfATypeThatHasNone() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(result).isPresent();
        assertThat(result.get().getValueConcept()).isEqualTo(ceramiqueConcept);
        verify(formConfigRepository).save(any(FormConfig.class));
    }

    @Test
    void createOrGetFormConfig_shouldInheritTheIdentifierConfigurationOfTheProjectDefault() {
        // Until it has a row of its own, a type is generated with the identifier configuration of the
        // default one; materializing that row must not silently move it back onto the built-in bounds.
        defaultConfig.setIdentifierFormat("MOB-{NUM_MOBILIER:000}");
        defaultConfig.setMinCode(100);
        defaultConfig.setMaxCode(500);
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(result).isPresent();
        assertThat(result.get().getIdentifierFormat()).isEqualTo("MOB-{NUM_MOBILIER:000}");
        assertThat(result.get().getMinCode()).isEqualTo(100);
        assertThat(result.get().getMaxCode()).isEqualTo(500);
    }

    @Test
    void createOrGetFormConfig_shouldFallBackOnTheBuiltInIdentifierConfigurationWithoutADefaultToInheritFrom() {
        when(formConfigRepository.findDefaultByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, (Long) null);

        assertThat(result).isPresent();
        assertThat(result.get().getIdentifierFormat())
                .isEqualTo(ConfigurableTable.MOBILIER.getDefaultIdentifierFormat());
        assertThat(result.get().getMinCode()).isOne();
        assertThat(result.get().getMaxCode()).isEqualTo(999);
    }

    @Test
    void createOrGetFormConfig_shouldReturnEmptyRatherThanCreateOneForAnUnknownType() {
        when(conceptRepository.findAllByFieldContextAndExactLabel(FIELD_CONCEPT_ID, "fr", "Métal"))
                .thenReturn(List.of());

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Métal");

        assertThat(result).isEmpty();
        verify(formConfigRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfig_byId_shouldReturnTheConfigurationTheTypeAlreadyHas() {
        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID);

        assertThat(result).contains(ceramiqueConfig);
        verify(formConfigRepository, never()).save(any());
        verifyNoInteractions(labelService);
    }

    @Test
    void createOrGetFormConfig_byId_shouldMaterializeTheConfigurationOfATypeThatHasNone() {
        Long metalConceptId = 300L;
        Concept metalConcept = concept(metalConceptId, "metal");
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, metalConceptId))
                .thenReturn(Optional.empty());
        when(conceptRepository.findById(metalConceptId)).thenReturn(Optional.of(metalConcept));
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, metalConceptId);

        assertThat(result).isPresent();
        assertThat(result.get().getValueConcept()).isEqualTo(metalConcept);
        verify(formConfigRepository).save(any(FormConfig.class));
        verifyNoInteractions(labelService);
    }

    @Test
    void createOrGetFormConfig_byId_shouldMaterializeTheDefaultConfigurationWhenTypeConceptIdIsNull() {
        when(formConfigRepository.findDefaultByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, (Long) null);

        assertThat(result).isPresent();
        assertThat(result.get().getValueConcept()).isNull();
        verify(formConfigRepository).save(any(FormConfig.class));
        verify(conceptRepository, never()).findById(any());
    }

    @Test
    void createOrGetFormConfig_byId_shouldFailWhenTheProjectDoesNotExist() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(PROJECT_ID.toString());
    }

    @Test
    void createOrGetFormConfig_byId_shouldFailWhenTheConceptIdIsUnknown() {
        Long unknownConceptId = 404L;
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, unknownConceptId))
                .thenReturn(Optional.empty());
        when(conceptRepository.findById(unknownConceptId)).thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, unknownConceptId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(unknownConceptId.toString());
        verify(formConfigRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfig_byId_shouldReturnEmptyRatherThanCreateOneWhenProjectHasNoVocabularyConfigured() throws Exception {
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), anyString(), any(Long.class)))
                .thenThrow(new NoConfigForFieldException("no config"));

        Optional<FormConfig> result = service.createOrGetFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID);

        assertThat(result).isEmpty();
        verify(formConfigRepository, never()).save(any());
    }

    @Test
    void getFieldsConfig_shouldMapVocabularyFieldsToTheirTypeAndSource() {
        CustomFieldSelectOneFromFieldCode vocabularyField = CustomFieldSelectOneFromFieldCode.builder()
                .id(3L).label("Catégorie").isSystemField(true).fieldCode("SIAS.CAT").build();
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, vocabularyField, true, false)));

        TypeFieldFormConfig field =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields().get(0);

        assertThat(field.getType()).isEqualTo(FieldType.SELECT_ONE);
        assertThat(field.isConfigurable()).isTrue();
        assertThat(field.getSourceLabel()).isEqualTo("SIAS.CAT");
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
    void deleteAdditionalField_shouldDropTheLinkOfTheType() {
        CustomField additional = textField(5L, "Couleur", false);
        FieldFormConfig link = fieldConfig(ceramiqueConfig, additional, true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(link));

        boolean deleted = service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(deleted).isTrue();
        verify(fieldFormConfigRepository).delete(link);
        verify(customFieldRepository, never()).delete(any(CustomField.class));
    }

    /**
     * The additional fields of a type include the ones it inherits from the default configuration:
     * deleting one of those has to reach the default configuration, or the field would be read back
     * — and displayed — right after being "deleted".
     */
    @Test
    void deleteAdditionalField_shouldDropTheLinkAFieldIsInheritedThrough() {
        CustomField additional = textField(5L, "Couleur", false);
        FieldFormConfig inherited = fieldConfig(defaultConfig, additional, true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(inherited));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of());

        boolean deleted = service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(deleted).isTrue();
        verify(fieldFormConfigRepository).delete(inherited);
    }

    @Test
    void deleteAdditionalField_shouldDropBothTheTypesOwnLinkAndTheInheritedOne() {
        CustomField additional = textField(5L, "Couleur", false);
        FieldFormConfig inherited = fieldConfig(defaultConfig, additional, true, false);
        FieldFormConfig own = fieldConfig(ceramiqueConfig, additional, true, true);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(inherited));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(own));

        boolean deleted = service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(deleted).isTrue();
        verify(fieldFormConfigRepository).delete(inherited);
        verify(fieldFormConfigRepository).delete(own);
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
    void deleteAdditionalField_shouldKeepAFieldTheProjectAlreadyAnswered() {
        CustomField additional = textField(5L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, additional, true, false)));
        when(customFieldAnswerRepository.countByFieldIdAndProjectId(5L, PROJECT_ID)).thenReturn(3L);

        boolean deleted = service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(deleted).isFalse();
        verify(fieldFormConfigRepository, never()).delete(any(FieldFormConfig.class));
        verify(customFieldRepository, never()).delete(any(CustomField.class));
    }

    /** A single answer is already one too many: it is what deleting the field would strand. */
    @Test
    void deleteAdditionalField_shouldKeepAFieldTheProjectAnsweredExactlyOnce() {
        CustomField additional = textField(5L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, additional, true, false)));
        when(customFieldAnswerRepository.countByFieldIdAndProjectId(5L, PROJECT_ID)).thenReturn(1L);

        boolean deleted = service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(deleted).isFalse();
        verify(fieldFormConfigRepository, never()).delete(any(FieldFormConfig.class));
    }

    /** Answers of another project are none of this project's business: they don't hold it back. */
    @Test
    void deleteAdditionalField_shouldIgnoreAnswersGivenInAnotherProject() {
        CustomField additional = textField(5L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, additional, true, false)));
        when(customFieldAnswerRepository.countByFieldIdAndProjectId(5L, PROJECT_ID)).thenReturn(0L);

        boolean deleted = service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Couleur");

        assertThat(deleted).isTrue();
        verify(fieldFormConfigRepository).delete(any(FieldFormConfig.class));
    }

    @Test
    void getFormConfig_shouldReportTheTypeNameAndTheConceptDefinition() {
        LocalizedConceptData data = new LocalizedConceptData();
        data.setDefinition("Objets façonnés en terre cuite.");
        when(conceptService.getLocalizedConceptDataByConceptAndLangCode(ceramiqueConcept, "fr")).thenReturn(data);

        var config = service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(config.getTypeName()).isEqualTo("Céramique");
        assertThat(config.getDefinition()).isEqualTo("Objets façonnés en terre cuite.");
    }

    @Test
    void getFormConfig_shouldLeaveTheDefaultTypeWithoutDefinition() {
        var config = service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default");

        assertThat(config.getDefinition()).isEmpty();
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
        assertThat(saved.getValue().getIdentifierFormat()).isEqualTo("{NUM_MOBILIER}");
        assertThat(saved.getValue().getMinCode()).isZero();
        assertThat(saved.getValue().getMaxCode()).isEqualTo(999);
    }

    @Test
    void saveFormConfig_shouldNotCreateAnythingWhenTheConfigurationAlreadyExists() {
        service.saveFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER,
                TypeFormConfig.builder().typeName("Céramique").build());

        verify(formConfigRepository, never()).save(any(FormConfig.class));
    }

    @Test
    void getFormConfig_shouldInheritIdentifierSettingsFromDefaultRow() {
        FormConfig defaults = formConfig(12L, null);
        defaults.setIdentifierFormat("M-{NUM_MOBILIER:0000}");
        defaults.setMinCode(10);
        defaults.setMaxCode(8000);
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        when(formConfigRepository.findDefaultByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(Optional.of(defaults));

        TypeFormConfig result = service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(result.getIdentifierFormat()).isEqualTo("M-{NUM_MOBILIER:0000}");
        assertThat(result.getMinCode()).isEqualTo(10);
        assertThat(result.getMaxCode()).isEqualTo(8000);
    }

    @Test
    void saveFormConfig_shouldPersistIdentifierSettingsOnExistingType() {
        TypeFormConfig changes = TypeFormConfig.builder()
                .typeName("Céramique")
                .identifierFormat("CER-{NUM_MOBILIER:000}")
                .minCode(5)
                .maxCode(500)
                .build();

        service.saveFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, changes);

        assertThat(ceramiqueConfig.getIdentifierFormat()).isEqualTo("CER-{NUM_MOBILIER:000}");
        assertThat(ceramiqueConfig.getMinCode()).isEqualTo(5);
        assertThat(ceramiqueConfig.getMaxCode()).isEqualTo(500);
        verify(formConfigRepository).save(ceramiqueConfig);
    }

    @Test
    void resolveIdentifierConfig_shouldReturnTypedRowBeforeDefault() {
        FormConfig result = service.resolveIdentifierConfig(PROJECT_ID, ConfigurableTable.MOBILIER, CERAMIQUE_CONCEPT_ID);

        assertThat(result).isSameAs(ceramiqueConfig);
        verify(formConfigRepository, never()).findDefaultByActionUnitAndField(anyLong(), anyLong());
    }

    @Test
    void configurableTables_shouldDeclareTheirIdentifierDefaults() {
        assertThat(ConfigurableTable.UE.getDefaultIdentifierFormat()).isEqualTo("{NUM_UE:000}");
        assertThat(ConfigurableTable.MOBILIER.getDefaultIdentifierFormat()).isEqualTo("{NUM_MOBILIER:000}");
        assertThat(ConfigurableTable.CONTENANT.getDefaultIdentifierFormat()).isEqualTo("{NUM_CONTAINER:000}");
        assertThat(ConfigurableTable.PHASE.getDefaultIdentifierFormat()).isEqualTo("{NUM_PHASE:000}");
    }

    // ========== New Tests ==========

    // --- listConfigurableTypes ---
    @Test
    void listConfigurableTypes_shouldReturnEmptyListWhenNoMatches() throws NoConfigForFieldException {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CAT"), eq("xyz"), eq(PROJECT_ID)))
                .thenReturn(List.of());
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of());

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "xyz"))
                .isEmpty();
    }

    @Test
    void listConfigurableTypes_shouldReturnEmptyListWhenInputIsNull() throws NoConfigForFieldException {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CAT"), isNull(), eq(PROJECT_ID)))
                .thenReturn(List.of());
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of());

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, null))
                .isEmpty();
    }

    @Test
    void listConfigurableTypes_shouldHandleDuplicatesInAutocomplete() throws NoConfigForFieldException {
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CAT"), eq("é"), eq(PROJECT_ID)))
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
    void searchFieldCatalog_shouldNotOfferFieldsTheTableAlreadyLaysOut() {
        CustomField identifier = givenSystemFieldsOf(ConfigurableTable.MOBILIER, IDENTIFIER_FIELD).get(IDENTIFIER_FIELD);
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(identifier));

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
    /** Nothing to remove is not a refusal: the caller must not report the field as being in use. */
    @Test
    void deleteAdditionalField_shouldDoNothingWhenFieldNotFound() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.of(ceramiqueConfig));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of());

        assertThat(service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "Inconnu")).isTrue();

        verify(fieldFormConfigRepository, never()).delete(any());
        verify(customFieldRepository, never()).delete(any());
    }

    @Test
    void deleteAdditionalField_shouldDoNothingWhenTypeNotFound() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.deleteAdditionalField(PROJECT_ID, ConfigurableTable.MOBILIER, "Inconnu", "Couleur")).isTrue();

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
    void getFieldsConfig_shouldMapASystemFieldToItsTypeAndSource() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, CATEGORY_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<TypeFieldFormConfig> fields = service.getFieldsConfig(
                PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getType()).isEqualTo(FieldType.SELECT_ONE);
        assertThat(fields.get(0).getSourceLabel()).isEqualTo(Specimen.CAT_FIELD);
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
        when(fieldConfigurationService.fetchAutocomplete(any(), eq("SIAS.CAT"), any(), eq(PROJECT_ID)))
                .thenThrow(new NoConfigForFieldException("no config"));

        assertThat(service.listConfigurableTypes(PROJECT_ID, ConfigurableTable.MOBILIER, "é")).isEmpty();
    }

    // --- typeOf ---
    @Test
    void getFieldsConfig_shouldMapRecordingUnitFields() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, RECORDING_UNIT_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .extracting(TypeFieldFormConfig::getType)
                .containsExactly(FieldType.SELECT_ONE_RECORDING_UNIT);
    }

    @Test
    void getFieldsConfig_shouldMapSpatialUnitFields() {
        givenSystemFieldsOf(ConfigurableTable.CONTENANT, "container.field.spatialUnit");
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.CONTENANT, "_default").getFields())
                .extracting(TypeFieldFormConfig::getType)
                .containsExactly(FieldType.SELECT_ONE_SPATIAL_UNIT);
    }

    @Test
    void getFieldsConfig_shouldMapMultipleSelectFieldsAndDashUnsetSources() {
        givenSystemFieldsOf(ConfigurableTable.MOBILIER, MATERIAL_FIELD, AUTHORS_FIELD);
        when(fieldFormConfigRepository.findAllByFormConfigId(anyLong())).thenReturn(List.of());

        List<TypeFieldFormConfig> fields = service.getFieldsConfig(
                PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields.get(0).getType()).isEqualTo(FieldType.SELECT_MULTIPLE);
        assertThat(fields.get(0).getSourceLabel()).isEqualTo(Specimen.MATIERE_FIELD);
        assertThat(fields.get(1).getSourceLabel()).isEqualTo("—");
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

    // --- reorderAdditionalFields ---

    @Test
    void reorderAdditionalFields_shouldNumberTheFieldsFromOneInTheOrderGiven() {
        FieldFormConfig couleur = fieldConfig(ceramiqueConfig, textField(1L, "Couleur", false), true, false);
        FieldFormConfig poids = fieldConfig(ceramiqueConfig, textField(2L, "Poids", false), true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(couleur, poids));

        service.reorderAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                List.of("Poids", "Couleur"));

        assertThat(poids.getPosition()).isEqualTo(1);
        assertThat(couleur.getPosition()).isEqualTo(2);
        verify(fieldFormConfigRepository).save(poids);
        verify(fieldFormConfigRepository).save(couleur);
    }

    @Test
    void reorderAdditionalFields_shouldMatchFieldNamesRegardlessOfCase() {
        FieldFormConfig couleur = fieldConfig(ceramiqueConfig, textField(1L, "Couleur", false), true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(couleur));

        service.reorderAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                List.of("COULEUR"));

        assertThat(couleur.getPosition()).isEqualTo(1);
        verify(fieldFormConfigRepository).save(couleur);
    }

    @Test
    void reorderAdditionalFields_shouldIgnoreTheNamesTheTypeDoesNotCarry() {
        FieldFormConfig couleur = fieldConfig(ceramiqueConfig, textField(1L, "Couleur", false), true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(couleur));

        service.reorderAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                List.of("Inexistant", "Couleur"));

        assertThat(couleur.getPosition()).isEqualTo(2);
        verify(fieldFormConfigRepository).save(couleur);
        verify(fieldFormConfigRepository, times(1)).save(any(FieldFormConfig.class));
    }

    /**
     * A field the type only inherits from the default configuration has no row of its own to carry a
     * position, and the default configuration must not be reordered on its behalf: the order would
     * then change for every other type inheriting it too.
     */
    @Test
    void reorderAdditionalFields_shouldLeaveTheConfigurationsInheritedFromTheDefaultTypeAlone() {
        FieldFormConfig inherited = fieldConfig(defaultConfig, textField(1L, "Couleur", false), true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(inherited));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of());

        service.reorderAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique",
                List.of("Couleur"));

        assertThat(inherited.getPosition()).isZero();
        verify(fieldFormConfigRepository, never()).save(any(FieldFormConfig.class));
    }

    @Test
    void reorderAdditionalFields_shouldCreateTheConfigurationOfATypeThatHasNoneYet() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        project.setCreatedByInstitution(new Institution());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(formConfigRepository.save(any(FormConfig.class))).thenAnswer(call -> call.getArgument(0));

        service.reorderAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", List.of("Couleur"));

        ArgumentCaptor<FormConfig> saved = ArgumentCaptor.forClass(FormConfig.class);
        verify(formConfigRepository).save(saved.capture());
        assertThat(saved.getValue().getValueConcept()).isEqualTo(ceramiqueConcept);
    }

    @Test
    void reorderAdditionalFields_shouldSaveNothingWhenNoOrderIsGiven() {
        FieldFormConfig couleur = fieldConfig(ceramiqueConfig, textField(1L, "Couleur", false), true, false);
        when(fieldFormConfigRepository.findAllByFormConfigId(11L)).thenReturn(List.of(couleur));

        service.reorderAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", List.of());

        assertThat(couleur.getPosition()).isZero();
        verify(fieldFormConfigRepository, never()).save(any(FieldFormConfig.class));
    }

    // --- typeOf / toDto ---

    @Test
    void getFieldsConfig_shouldMapActionUnitAndActionCodeFields() {
        CustomField projectField = CustomFieldSelectOneActionUnit.builder()
                .id(1L).label("Projet").isSystemField(true).build();
        CustomField actionCodeField = CustomFieldSelectOneActionCode.builder()
                .id(2L).label("Code opération").isSystemField(true).build();
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(
                fieldConfig(defaultConfig, projectField, true, false),
                fieldConfig(defaultConfig, actionCodeField, true, false)));

        List<TypeFieldFormConfig> fields =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields();

        assertThat(fields).extracting(TypeFieldFormConfig::getType)
                .containsExactly(FieldType.PROJET, FieldType.SELECT_ONE);
        assertThat(fields).extracting(TypeFieldFormConfig::getSourceLabel).containsOnly("—");
    }

    @Test
    void getFieldsConfig_shouldMapEveryFlavourOfSpatialUnitFieldToTheSameType() {
        CustomField address = CustomFieldSelectOneAddress.builder()
                .id(1L).label("Adresse").isSystemField(true).build();
        CustomField tree = CustomFieldSelectMultipleSpatialUnitTree.builder()
                .id(2L).label("Lieux").isSystemField(true).build();
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(
                fieldConfig(defaultConfig, address, true, false),
                fieldConfig(defaultConfig, tree, true, false)));

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .extracting(TypeFieldFormConfig::getType)
                .containsExactly(FieldType.SELECT_ONE_SPATIAL_UNIT, FieldType.SELECT_ONE_SPATIAL_UNIT);
    }

    /**
     * The screen shows fewer types than the entity hierarchy expresses; one it has no type for falls
     * back on text, and only the controlled-vocabulary types are announced as configurable.
     */
    @Test
    void getFieldsConfig_shouldFallBackOnTextForAFieldItHasNoTypeFor() {
        CustomField dateField = CustomFieldDateTime.builder()
                .id(1L).label("Date d'ouverture").isSystemField(true).build();
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, dateField, true, false)));

        TypeFieldFormConfig field =
                service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields().get(0);

        assertThat(field.getType()).isEqualTo(FieldType.TEXT);
        assertThat(field.isConfigurable()).isFalse();
    }

    /**
     * The data entry screens match a configuration to the field it applies to by value binding (see
     * {@code RecordingUnitPanel#inactiveSystemFieldBindings}), so the DTO has to carry it.
     */
    @Test
    void getFieldsConfig_shouldCarryTheValueBindingOfTheField() {
        CustomField field = textField(1L, "Identifiant", true);
        field.setValueBinding("fullIdentifier");
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, field, true, false)));

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .extracting(TypeFieldFormConfig::getValueBinding)
                .containsExactly("fullIdentifier");
    }

    @Test
    void getFieldsConfig_shouldReportAFieldLockedByTheInstitutionAsSuch() {
        FieldFormConfig locked = fieldConfig(defaultConfig, textField(1L, "Identifiant", true), true, true);
        locked.setInstitutionLocked(true);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L)).thenReturn(List.of(locked));

        assertThat(service.getFieldsConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default").getFields())
                .extracting(TypeFieldFormConfig::isInstitutionLocked)
                .containsExactly(true);
    }

    // --- getActiveAdditionalFields ---

    @Test
    void getActiveAdditionalFields_shouldIncludeTheOnesInheritedFromTheDefaultConfiguration() {
        CustomField inherited = textField(1L, "Couleur", false);
        CustomField ownField = textField(2L, "Poids", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, inherited, true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, ownField, true, false)));

        assertThat(service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique"))
                .containsExactly(inherited, ownField);
    }

    @Test
    void getActiveAdditionalFields_shouldDropAFieldTheTypeDeactivatedOnTopOfTheDefaultConfiguration() {
        CustomField inherited = textField(1L, "Couleur", false);
        when(fieldFormConfigRepository.findAllByFormConfigId(10L))
                .thenReturn(List.of(fieldConfig(defaultConfig, inherited, true, false)));
        when(fieldFormConfigRepository.findAllByFormConfigId(11L))
                .thenReturn(List.of(fieldConfig(ceramiqueConfig, inherited, false, false)));

        assertThat(service.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique"))
                .isEmpty();
    }

    // --- listTypes / getFormConfig / findFormConfig ---

    @Test
    void listTypes_shouldSortTheConfiguredTypesCaseInsensitivelyAfterTheDefaultOne() {
        Concept metalConcept = concept(300L, "metal");
        Concept amphoreConcept = concept(400L, "amphore");
        when(formConfigRepository.findAllByActionUnitAndField(PROJECT_ID, FIELD_CONCEPT_ID))
                .thenReturn(List.of(formConfig(12L, metalConcept), ceramiqueConfig, formConfig(13L, amphoreConcept)));
        when(labelService.findLabelOf(metalConcept, "fr")).thenReturn(prefLabel("métal"));
        when(labelService.findLabelOf(ceramiqueConcept, "fr")).thenReturn(prefLabel("Céramique"));
        when(labelService.findLabelOf(amphoreConcept, "fr")).thenReturn(prefLabel("amphore"));

        assertThat(service.listTypes(PROJECT_ID, ConfigurableTable.MOBILIER))
                .extracting(TypeSummary::getName)
                .containsExactly("_default", "amphore", "Céramique", "métal");
    }

    @Test
    void listTypes_shouldHoldOnlyTheDefaultTypeWhenTheProjectHasNoVocabularyForTheTableField() throws Exception {
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), eq("SIAS.CAT"), eq(PROJECT_ID)))
                .thenThrow(new NoConfigForFieldException("no config"));

        assertThat(service.listTypes(PROJECT_ID, ConfigurableTable.MOBILIER))
                .extracting(TypeSummary::getName).containsExactly("_default");
    }

    @Test
    void findFormConfig_shouldReturnEmptyWhenTheProjectHasNoVocabularyForTheTableField() throws Exception {
        when(fieldConfigurationService.findConfigurationForFieldCode(any(), eq("SIAS.CAT"), eq(PROJECT_ID)))
                .thenThrow(new NoConfigForFieldException("no config"));

        assertThat(service.findFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "_default")).isEmpty();
    }

    /**
     * A type is shown before anything is configured on it, so the screen falls back on the name it
     * was asked for rather than on nothing.
     */
    @Test
    void getFormConfig_shouldFallBackOnTheTypeNameWhenTheTypeHasNoConfigurationYet() {
        when(formConfigRepository.findByActionUnitAndFieldAndValue(PROJECT_ID, FIELD_CONCEPT_ID, CERAMIQUE_CONCEPT_ID))
                .thenReturn(Optional.empty());

        TypeFormConfig config = service.getFormConfig(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(config.getTypeName()).isEqualTo("Céramique");
        assertThat(config.getDefinition()).isEmpty();
    }

    // --- searchFieldCatalog ---

    /**
     * Several institutions can define a field of the same name; the picker offers the name once,
     * since that is all it shows of a field.
     */
    @Test
    void searchFieldCatalog_shouldOfferAFieldOnceWhenSeveralShareTheSameLabel() {
        when(customFieldRepository.findAllReusableByInstitution(1L))
                .thenReturn(List.of(textField(1L, "Couleur", false), textField(2L, "Couleur", false)));

        assertThat(service.searchFieldCatalog(PROJECT_ID, ConfigurableTable.MOBILIER, "Céramique", "couleur"))
                .extracting(FieldCatalogEntry::getName)
                .containsExactly("Couleur");
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
        config.setIdentifierFormat("{NUM_MOBILIER}");
        config.setMinCode(0);
        config.setMaxCode(999);
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

    private Map<String, CustomField> givenSystemFieldsOf(ConfigurableTable table, String... labels) {
        Set<String> wanted = Set.of(labels);
        Map<String, CustomField> rows = new LinkedHashMap<>();
        long id = SYSTEM_FIELD_FIRST_ID;
        for (CustomColUiDto column : SystemFieldCatalog.systemColumnsOf(table)) {
            CustomField definition = column.getField();
            Assertions.assertNotNull(definition);
            if (!wanted.contains(definition.getLabel())) continue;
            definition.setId(id++);
            rows.put(definition.getLabel(), definition);
        }
        assertThat(rows.keySet())
                .as("the labels a test asks for must be ones the table really defines")
                .containsExactlyInAnyOrderElementsOf(wanted);
        when(customFieldRepository.findAllSystemFields()).thenReturn(new ArrayList<>(rows.values()));
        return rows;
    }

    private ConceptAutocompleteDTO autocomplete(String label) {
        ConceptPrefLabelDTO labelDTO = new ConceptPrefLabelDTO();
        labelDTO.setLabel(label);
        return ConceptAutocompleteDTO.builder().conceptLabelToDisplay(labelDTO).build();
    }
}
