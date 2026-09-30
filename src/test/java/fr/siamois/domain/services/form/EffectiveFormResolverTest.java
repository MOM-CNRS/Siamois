package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.ui.form.dto.ColumnWidth;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.utils.TestForms;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectiveFormResolverTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long TYPE_CONCEPT_ID = 50L;
    private static final ConfigurableTable TABLE = ConfigurableTable.UE;

    @Mock
    private TableFieldConfigService tableFieldConfigService;

    @Mock
    private FormLayoutService formLayoutService;

    private EffectiveFormResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EffectiveFormResolver(tableFieldConfigService, formLayoutService, TestForms.seeds());
    }

    @Test
    void withoutAStoredLayout_removesInactiveSystemFieldsByValueBinding() {
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, TABLE, TYPE_CONCEPT_ID))
                .thenReturn(fieldsConfig(inactiveField("geomorphologicalCycle")));
        noAdditionalFields();

        FormUiDto result = resolver.resolveEffectiveForm(PROJECT_ID, TABLE, TYPE_CONCEPT_ID);

        assertThat(bindingsOf(result)).doesNotContain("geomorphologicalCycle").contains("geomorphologicalAgent");
    }

    @Test
    void withoutAStoredLayout_appendsActiveAdditionalFieldsAsATrailingPanel() {
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, TABLE, TYPE_CONCEPT_ID)).thenReturn(new TypeFieldsConfig());
        CustomFieldText additionalField = CustomFieldText.builder().id(9L).label("Couleur").valueBinding("couleur").build();
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, TABLE, TYPE_CONCEPT_ID))
                .thenReturn(List.of(additionalField));
        int seedPanels = TestForms.of(TABLE).getLayout().size();

        FormUiDto result = resolver.resolveEffectiveForm(PROJECT_ID, TABLE, TYPE_CONCEPT_ID);

        assertThat(result.getLayout()).hasSize(seedPanels + 1);
        var additionalPanel = result.getLayout().get(seedPanels);
        assertThat(additionalPanel.getIsSystemPanel()).isFalse();
        assertThat(additionalPanel.getRows()).hasSize(1);
        assertThat(additionalPanel.getRows().get(0).getColumns()).extracting(c -> c.getField().getValueBinding())
                .containsExactly("couleur");
        // Regression: an additional field's column must carry a width, or the client crashes on it.
        assertThat(additionalPanel.getRows().get(0).getColumns().get(0).getWidth()).isEqualTo(ColumnWidth.STANDARD);
    }

    @Test
    void withoutAStoredLayout_returnsTheInitialFormUnchanged_whenNothingIsInactiveOrAdditional() {
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, TABLE, TYPE_CONCEPT_ID)).thenReturn(new TypeFieldsConfig());
        noAdditionalFields();

        FormUiDto result = resolver.resolveEffectiveForm(PROJECT_ID, TABLE, TYPE_CONCEPT_ID);

        assertThat(bindingsOf(result)).isEqualTo(bindingsOf(TestForms.of(TABLE)));
    }

    @Test
    void withoutAStoredLayout_ignoresInactiveEntriesWithNoValueBinding() {
        TypeFieldFormConfig noBindingInactive = TypeFieldFormConfig.builder().active(false).valueBinding(null).build();
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, TABLE, TYPE_CONCEPT_ID))
                .thenReturn(new TypeFieldsConfig(new java.util.ArrayList<>(List.of(noBindingInactive))));
        noAdditionalFields();

        FormUiDto result = resolver.resolveEffectiveForm(PROJECT_ID, TABLE, TYPE_CONCEPT_ID);

        assertThat(bindingsOf(result)).isEqualTo(bindingsOf(TestForms.of(TABLE)));
    }

    @Test
    void withoutAStoredLayout_supportsNullTypeConceptIdForTheDefaultConfiguration() {
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, TABLE, (Long) null)).thenReturn(new TypeFieldsConfig());
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, TABLE, (Long) null)).thenReturn(List.of());

        resolver.resolveEffectiveForm(PROJECT_ID, TABLE, null);

        verify(tableFieldConfigService).getFieldsConfig(PROJECT_ID, TABLE, (Long) null);
        verify(tableFieldConfigService).getActiveAdditionalFields(PROJECT_ID, TABLE, (Long) null);
    }

    @Test
    void withoutAStoredLayout_marksMandatoryFieldsRequired() {
        TypeFieldFormConfig mandatory = TypeFieldFormConfig.builder().active(true).mandatory(true).valueBinding("geomorphologicalCycle").build();
        TypeFieldFormConfig additionalMandatory = TypeFieldFormConfig.builder().active(true).mandatory(true).valueBinding("couleur").build();
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, TABLE, TYPE_CONCEPT_ID))
                .thenReturn(fieldsConfig(mandatory, additionalMandatory));
        CustomFieldText additionalField = CustomFieldText.builder().id(9L).label("Couleur").valueBinding("couleur").build();
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, TABLE, TYPE_CONCEPT_ID))
                .thenReturn(List.of(additionalField));

        FormUiDto result = resolver.resolveEffectiveForm(PROJECT_ID, TABLE, TYPE_CONCEPT_ID);

        assertThat(column(result, "geomorphologicalCycle").isRequired()).isTrue();
        assertThat(column(result, "couleur").isRequired()).isTrue();
        assertThat(column(result, "geomorphologicalAgent").isRequired()).isFalse();
        // the initial form is rebuilt each time: marking a field never leaks into the next resolution
        assertThat(column(TestForms.of(TABLE), "geomorphologicalCycle").isRequired()).isFalse();
    }

    @Test
    void aStoredLayoutIsTheFormAsIs_withoutMergingTheLegacyConfiguration() {
        var nature = TestForms.layoutOf(TABLE).groups().stream().flatMap(g -> g.items().stream())
                .filter(i -> "geomorphologicalCycle".equals(i.field().getValueBinding())).findFirst().orElseThrow();
        var agent = TestForms.layoutOf(TABLE).groups().stream().flatMap(g -> g.items().stream())
                .filter(i -> "geomorphologicalAgent".equals(i.field().getValueBinding())).findFirst().orElseThrow();
        FormLayout stored = new FormLayout(List.of(new FormLayout.Group(1L, "Mon groupe", List.of(
                new FormLayout.Item(agent.field(), FieldWidth.FULL, true, true, false, FieldRules.NONE),
                new FormLayout.Item(nature.field(), FieldWidth.HALF, false, false, false, FieldRules.NONE)))));
        when(formLayoutService.storedLayout(PROJECT_ID, TABLE, TYPE_CONCEPT_ID)).thenReturn(Optional.of(stored));

        FormUiDto result = resolver.resolveEffectiveForm(PROJECT_ID, TABLE, TYPE_CONCEPT_ID);

        assertThat(result.getLayout()).hasSize(1);
        assertThat(result.getLayout().get(0).getName()).isEqualTo("Mon groupe");
        // the inactive field is dropped; the hidden system fields every form carries are kept
        assertThat(bindingsOf(result)).contains("geomorphologicalAgent").doesNotContain("geomorphologicalCycle");
        CustomColUiDto agentColumn = column(result, "geomorphologicalAgent");
        assertThat(agentColumn.getWidth()).isEqualTo(ColumnWidth.FULL);
        assertThat(agentColumn.isRequired()).isTrue();
        verifyNoInteractions(tableFieldConfigService);
    }

    @Test
    void withoutAProject_theInitialFormIsReturnedAsIs() {
        FormUiDto result = resolver.resolveEffectiveForm(null, TABLE, TYPE_CONCEPT_ID);

        assertThat(bindingsOf(result)).isEqualTo(bindingsOf(TestForms.of(TABLE)));
        verifyNoInteractions(tableFieldConfigService, formLayoutService);
    }

    // ---------- helpers ----------

    private void noAdditionalFields() {
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, TABLE, TYPE_CONCEPT_ID)).thenReturn(List.of());
    }

    private TypeFieldsConfig fieldsConfig(TypeFieldFormConfig... fields) {
        return new TypeFieldsConfig(new java.util.ArrayList<>(List.of(fields)));
    }

    private TypeFieldFormConfig inactiveField(String valueBinding) {
        return TypeFieldFormConfig.builder().active(false).valueBinding(valueBinding).build();
    }

    private CustomColUiDto column(FormUiDto form, String binding) {
        return form.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .filter(c -> binding.equals(c.getField().getValueBinding()))
                .findFirst().orElseThrow();
    }

    private List<String> bindingsOf(FormUiDto form) {
        return form.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .map(c -> c.getField().getValueBinding())
                .toList();
    }
}
