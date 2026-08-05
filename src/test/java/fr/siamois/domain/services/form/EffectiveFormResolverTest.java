package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectiveFormResolverTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long TYPE_CONCEPT_ID = 50L;

    @Mock
    private TableFieldConfigService tableFieldConfigService;

    @InjectMocks
    private EffectiveFormResolver resolver;

    @Test
    void resolveEffectiveForm_removesInactiveSystemFieldsByValueBinding() {
        FormUiDto baseForm = formOf(
                col("kept", "kept"),
                col("dropped", "dropped"));
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(fieldsConfig(inactiveField("dropped")));
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(List.of());

        FormUiDto result = resolver.resolveEffectiveForm(baseForm, PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID);

        assertThat(bindingsOf(result)).containsExactly("kept");
    }

    @Test
    void resolveEffectiveForm_appendsActiveAdditionalFieldsAsATrailingPanel() {
        FormUiDto baseForm = formOf(col("existing", "existing"));
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(new TypeFieldsConfig());
        CustomFieldText additionalField = CustomFieldText.builder().id(9L).label("Couleur").valueBinding("couleur").build();
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(List.of(additionalField));

        FormUiDto result = resolver.resolveEffectiveForm(baseForm, PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID);

        assertThat(result.getLayout()).hasSize(2);
        CustomFormPanelUiDto additionalPanel = result.getLayout().get(1);
        assertThat(additionalPanel.getIsSystemPanel()).isFalse();
        assertThat(additionalPanel.getRows()).hasSize(1);
        assertThat(additionalPanel.getRows().get(0).getColumns()).extracting(c -> c.getField().getValueBinding())
                .containsExactly("couleur");
    }

    @Test
    void resolveEffectiveForm_returnsBaseFormUnchanged_whenNothingIsInactiveOrAdditional() {
        FormUiDto baseForm = formOf(col("kept", "kept"));
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(new TypeFieldsConfig());
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(List.of());

        FormUiDto result = resolver.resolveEffectiveForm(baseForm, PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID);

        assertThat(result).isSameAs(baseForm);
    }

    @Test
    void resolveEffectiveForm_ignoresInactiveEntriesWithNoValueBinding() {
        FormUiDto baseForm = formOf(col("kept", "kept"));
        TypeFieldFormConfig noBindingInactive = TypeFieldFormConfig.builder().active(false).valueBinding(null).build();
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(new TypeFieldsConfig(new java.util.ArrayList<>(List.of(noBindingInactive))));
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID))
                .thenReturn(List.of());

        FormUiDto result = resolver.resolveEffectiveForm(baseForm, PROJECT_ID, ConfigurableTable.UE, TYPE_CONCEPT_ID);

        assertThat(bindingsOf(result)).containsExactly("kept");
    }

    @Test
    void resolveEffectiveForm_supportsNullTypeConceptIdForTheDefaultConfiguration() {
        FormUiDto baseForm = formOf(col("kept", "kept"));
        when(tableFieldConfigService.getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, (Long) null))
                .thenReturn(new TypeFieldsConfig());
        when(tableFieldConfigService.getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.UE, (Long) null))
                .thenReturn(List.of());

        resolver.resolveEffectiveForm(baseForm, PROJECT_ID, ConfigurableTable.UE, null);

        verify(tableFieldConfigService).getFieldsConfig(PROJECT_ID, ConfigurableTable.UE, (Long) null);
        verify(tableFieldConfigService).getActiveAdditionalFields(PROJECT_ID, ConfigurableTable.UE, (Long) null);
    }

    // ---------- helpers ----------

    private FormUiDto formOf(CustomColUiDto... columns) {
        return new FormUiDto.Builder()
                .addPanel(new CustomFormPanelUiDto.Builder()
                        .name("panel")
                        .isSystemPanel(true)
                        .addRow(new CustomRowUiDto.Builder().addColumns(columns).build())
                        .build())
                .build();
    }

    private CustomColUiDto col(String label, String valueBinding) {
        return new CustomColUiDto.Builder()
                .field(CustomFieldText.builder().label(label).valueBinding(valueBinding).build())
                .build();
    }

    private TypeFieldsConfig fieldsConfig(TypeFieldFormConfig... fields) {
        return new TypeFieldsConfig(new java.util.ArrayList<>(List.of(fields)));
    }

    private TypeFieldFormConfig inactiveField(String valueBinding) {
        return TypeFieldFormConfig.builder().active(false).valueBinding(valueBinding).build();
    }

    private List<String> bindingsOf(FormUiDto form) {
        return form.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .map(c -> c.getField().getValueBinding())
                .toList();
    }
}
