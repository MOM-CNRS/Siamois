package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTableFieldSettingsBeanTest {

    private ProjectTableFieldSettingsBean bean;

    @Mock
    private TableFieldConfigService tableFieldConfigService;

    @Mock
    private LangBean langBean;

    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        bean = new ProjectTableFieldSettingsBean(tableFieldConfigService, langBean);
        project = new ActionUnitDTO();
        project.setId(42L);

        when(tableFieldConfigService.listTables()).thenReturn(
                List.of(ConfigurableTable.UE, ConfigurableTable.MOBILIER));
        when(tableFieldConfigService.listTypes(eq(42L), any())).thenReturn(
                List.of(new TypeSummary("_default", true), new TypeSummary("Céramique", false)));
        when(tableFieldConfigService.getFormConfig(eq(42L), any(), any())).thenReturn(
                TypeFormConfig.builder().typeName("Céramique").build());
        when(tableFieldConfigService.getFieldsConfig(eq(42L), any(), any())).thenReturn(
                new TypeFieldsConfig());
    }

    @Test
    void init_shouldSelectFirstTableAndFirstNonDefaultType() {
        bean.init(project);

        assertThat(bean.getSelectedTable()).isEqualTo(ConfigurableTable.UE);
        assertThat(bean.getSelectedTypeName()).isEqualTo("Céramique");
        assertThat(bean.getFormConfig()).isNotNull();
        assertThat(bean.getFieldsConfig()).isNotNull();
    }

    @Test
    void selectTable_shouldReloadTypesAndConfigsForNewTable() {
        bean.init(project);

        bean.selectTable(ConfigurableTable.MOBILIER);

        assertThat(bean.getSelectedTable()).isEqualTo(ConfigurableTable.MOBILIER);
        verify(tableFieldConfigService).listTypes(42L, ConfigurableTable.MOBILIER);
    }

    @Test
    void selectType_shouldReloadFormAndFieldsConfig() {
        bean.init(project);

        bean.selectType("_default");

        assertThat(bean.getSelectedTypeName()).isEqualTo("_default");
        verify(tableFieldConfigService).getFormConfig(42L, ConfigurableTable.UE, "_default");
        verify(tableFieldConfigService).getFieldsConfig(42L, ConfigurableTable.UE, "_default");
    }

    @Test
    void toggleFieldActive_shouldForwardTheFieldsAlreadyUpdatedValue() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder().name("Localisation").type(FieldType.TEXTE).systemField(true).active(false).build();

        bean.toggleFieldActive(field);

        verify(tableFieldConfigService).setFieldActive(42L, ConfigurableTable.UE, "Céramique", "Localisation", false);
    }

    @Test
    void toggleFieldMandatory_shouldForwardTheFieldsAlreadyUpdatedValueForAdditionalField() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder().name("Remontage").type(FieldType.PARENTS).systemField(false).mandatory(true).build();

        bean.toggleFieldMandatory(field);

        verify(tableFieldConfigService).setFieldMandatory(42L, ConfigurableTable.UE, "Céramique", "Remontage", true);
    }

    @Test
    void getSystemFields_and_getAdditionalFields_shouldPartitionByIsSystemField() {
        bean.init(project);
        TypeFieldFormConfig sys = TypeFieldFormConfig.builder().name("Identifiant").systemField(true).build();
        TypeFieldFormConfig custom = TypeFieldFormConfig.builder().name("Remontage").systemField(false).build();
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setFields(List.of(sys, custom));
        bean.setFieldsConfig(config);

        assertThat(bean.getSystemFields()).containsExactly(sys);
        assertThat(bean.getAdditionalFields()).containsExactly(custom);
    }

    @Test
    void deleteAdditionalField_shouldDelegateAndReload() {
        bean.init(project);

        bean.deleteAdditionalField("Remontage");

        verify(tableFieldConfigService).deleteAdditionalField(42L, ConfigurableTable.UE, "Céramique", "Remontage");
        verify(tableFieldConfigService, atLeastOnce()).getFieldsConfig(42L, ConfigurableTable.UE, "Céramique");
    }

    @Test
    void addField_shouldDelegateAndReload() {
        bean.init(project);

        bean.addField();

        verify(tableFieldConfigService).addAdditionalField(42L, ConfigurableTable.UE, "Céramique");
    }

    @Test
    void reset_shouldClearAllState() {
        bean.init(project);

        bean.reset();

        assertThat(bean.getProject()).isNull();
        assertThat(bean.getTables()).isEmpty();
        assertThat(bean.getSelectedTable()).isNull();
        assertThat(bean.getSelectedTypeName()).isNull();
        assertThat(bean.getFormConfig()).isNull();
        assertThat(bean.getFieldsConfig()).isNull();
    }
}
