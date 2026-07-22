package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.AdditionalFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeGeneralConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockTableFieldConfigServiceTest {

    private MockTableFieldConfigService service;

    @BeforeEach
    void setUp() {
        service = new MockTableFieldConfigService();
    }

    @Test
    void listTables_shouldReturnTheFourConfigurableTables() {
        assertThat(service.listTables()).containsExactly(
                ConfigurableTable.UE, ConfigurableTable.MOBILIER, ConfigurableTable.PHASE, ConfigurableTable.CONTENANT);
    }

    @Test
    void listTypes_shouldReturnDefaultTypeFirst() {
        List<TypeSummary> types = service.listTypes(1L, ConfigurableTable.MOBILIER);

        assertThat(types.get(0).getName()).isEqualTo("_default");
        assertThat(types.get(0).isDefault()).isTrue();
        assertThat(types).extracting(TypeSummary::getName).contains("Céramique", "Lithique");
    }

    @Test
    void getFieldsConfig_shouldSeedCeramiqueWithAdditionalFields() {
        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Céramique");

        assertThat(config.getSystemFields()).isNotEmpty();
        assertThat(config.getAdditionalFields()).extracting(AdditionalFieldConfig::getName)
                .contains("Technique de fabrication", "Nombre de tessons");
    }

    @Test
    void getFieldsConfig_shouldReturnDefensiveCopiesNotSharedInstances() {
        TypeFieldsConfig first = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");
        first.getSystemFields().get(0).setVisible(false);

        TypeFieldsConfig second = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");

        assertThat(second.getSystemFields().get(0).isVisible()).isTrue();
    }

    @Test
    void setSystemFieldVisible_shouldPersistAcrossCalls() {
        service.setSystemFieldVisible(1L, ConfigurableTable.MOBILIER, "Lithique", "Localisation", false);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");
        boolean visible = config.getSystemFields().stream()
                .filter(f -> f.getName().equals("Localisation"))
                .findFirst().orElseThrow().isVisible();

        assertThat(visible).isFalse();
    }

    @Test
    void setFieldRequired_shouldUpdateAdditionalFieldWhenNotSystem() {
        service.setFieldRequired(1L, ConfigurableTable.MOBILIER, "Céramique", "Technique de fabrication", false, true);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Céramique");
        boolean required = config.getAdditionalFields().stream()
                .filter(f -> f.getName().equals("Technique de fabrication"))
                .findFirst().orElseThrow().isRequired();

        assertThat(required).isTrue();
    }

    @Test
    void addAdditionalField_shouldAppendAndAvoidNameCollisions() {
        service.addAdditionalField(1L, ConfigurableTable.PHASE, "Occupation");
        service.addAdditionalField(1L, ConfigurableTable.PHASE, "Occupation");

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.PHASE, "Occupation");

        assertThat(config.getAdditionalFields()).extracting(AdditionalFieldConfig::getName)
                .containsExactly("Nouveau champ", "Nouveau champ 2");
    }

    @Test
    void deleteAdditionalField_shouldRemoveIt() {
        service.addAdditionalField(1L, ConfigurableTable.PHASE, "Occupation");

        service.deleteAdditionalField(1L, ConfigurableTable.PHASE, "Occupation", "Nouveau champ");

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.PHASE, "Occupation");
        assertThat(config.getAdditionalFields()).isEmpty();
    }

    @Test
    void projectStatesAreIsolatedFromEachOther() {
        service.setSystemFieldVisible(1L, ConfigurableTable.MOBILIER, "Lithique", "Localisation", false);

        TypeFieldsConfig otherProjectConfig = service.getFieldsConfig(2L, ConfigurableTable.MOBILIER, "Lithique");
        boolean visible = otherProjectConfig.getSystemFields().stream()
                .filter(f -> f.getName().equals("Localisation"))
                .findFirst().orElseThrow().isVisible();

        assertThat(visible).isTrue();
    }

    @Test
    void saveGeneralConfig_shouldPersistChanges() {
        TypeGeneralConfig config = service.getGeneralConfig(1L, ConfigurableTable.UE, "Creusement");
        config.setDescription("Une fosse de creusement");

        service.saveGeneralConfig(1L, ConfigurableTable.UE, config);

        TypeGeneralConfig reloaded = service.getGeneralConfig(1L, ConfigurableTable.UE, "Creusement");
        assertThat(reloaded.getDescription()).isEqualTo("Une fosse de creusement");
    }
}
