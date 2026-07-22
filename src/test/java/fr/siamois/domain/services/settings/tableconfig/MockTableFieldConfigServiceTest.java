package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
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

        assertThat(config.getFields()).anyMatch(TypeFieldFormConfig::isSystemField);
        assertThat(config.getFields()).filteredOn(f -> !f.isSystemField())
                .extracting(TypeFieldFormConfig::getName)
                .contains("Technique de fabrication", "Nombre de tessons");
    }

    @Test
    void getFieldsConfig_shouldReturnDefensiveCopiesNotSharedInstances() {
        TypeFieldsConfig first = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");
        first.getFields().get(0).setActive(false);

        TypeFieldsConfig second = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");

        assertThat(second.getFields().get(0).isActive()).isTrue();
    }

    @Test
    void setFieldActive_shouldPersistAcrossCalls() {
        service.setFieldActive(1L, ConfigurableTable.MOBILIER, "Lithique", "Localisation", false);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");
        boolean active = config.getFields().stream()
                .filter(f -> f.getName().equals("Localisation"))
                .findFirst().orElseThrow().isActive();

        assertThat(active).isFalse();
    }

    @Test
    void setFieldActive_shouldBeNoOpOnInstitutionLockedField() {
        // "Identifiant" is seeded institutionLocked=true
        service.setFieldActive(1L, ConfigurableTable.MOBILIER, "Lithique", "Identifiant", false);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");
        boolean active = config.getFields().stream()
                .filter(f -> f.getName().equals("Identifiant"))
                .findFirst().orElseThrow().isActive();

        assertThat(active).isTrue();
    }

    @Test
    void setFieldMandatory_shouldBeNoOpOnInstitutionLockedField() {
        // "Projet" is seeded institutionLocked=true
        service.setFieldMandatory(1L, ConfigurableTable.MOBILIER, "Lithique", "Projet", false);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Lithique");
        boolean mandatory = config.getFields().stream()
                .filter(f -> f.getName().equals("Projet"))
                .findFirst().orElseThrow().isMandatory();

        assertThat(mandatory).isTrue();
    }

    @Test
    void setFieldMandatory_shouldUpdateAdditionalFieldWhenNotLocked() {
        service.setFieldMandatory(1L, ConfigurableTable.MOBILIER, "Céramique", "Technique de fabrication", true);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.MOBILIER, "Céramique");
        boolean mandatory = config.getFields().stream()
                .filter(f -> f.getName().equals("Technique de fabrication"))
                .findFirst().orElseThrow().isMandatory();

        assertThat(mandatory).isTrue();
    }

    @Test
    void addAdditionalField_shouldAppendAndAvoidNameCollisions() {
        service.addAdditionalField(1L, ConfigurableTable.PHASE, "Occupation");
        service.addAdditionalField(1L, ConfigurableTable.PHASE, "Occupation");

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.PHASE, "Occupation");

        assertThat(config.getFields()).filteredOn(f -> !f.isSystemField())
                .extracting(TypeFieldFormConfig::getName)
                .containsExactly("Nouveau champ", "Nouveau champ 2");
    }

    @Test
    void deleteAdditionalField_shouldRemoveIt() {
        service.addAdditionalField(1L, ConfigurableTable.PHASE, "Occupation");

        service.deleteAdditionalField(1L, ConfigurableTable.PHASE, "Occupation", "Nouveau champ");

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.PHASE, "Occupation");
        assertThat(config.getFields()).noneMatch(f -> !f.isSystemField());
    }

    @Test
    void deleteAdditionalField_shouldNotRemoveSystemFields() {
        service.deleteAdditionalField(1L, ConfigurableTable.PHASE, "Occupation", "Identifiant");

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.PHASE, "Occupation");
        assertThat(config.getFields()).anyMatch(f -> f.getName().equals("Identifiant"));
    }

    @Test
    void projectStatesAreIsolatedFromEachOther() {
        service.setFieldActive(1L, ConfigurableTable.MOBILIER, "Lithique", "Localisation", false);

        TypeFieldsConfig otherProjectConfig = service.getFieldsConfig(2L, ConfigurableTable.MOBILIER, "Lithique");
        boolean active = otherProjectConfig.getFields().stream()
                .filter(f -> f.getName().equals("Localisation"))
                .findFirst().orElseThrow().isActive();

        assertThat(active).isTrue();
    }

    @Test
    void saveFormConfig_shouldPersistChanges() {
        TypeFormConfig config = service.getFormConfig(1L, ConfigurableTable.UE, "Creusement");
        config.setDescription("Une fosse de creusement");

        service.saveFormConfig(1L, ConfigurableTable.UE, config);

        TypeFormConfig reloaded = service.getFormConfig(1L, ConfigurableTable.UE, "Creusement");
        assertThat(reloaded.getDescription()).isEqualTo("Une fosse de creusement");
    }

    @Test
    void getFieldsConfig_shouldSeedUeSystemFieldsMatchingRecordingUnitFormValueBindings() {
        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.UE, "_default");

        assertThat(config.getFields()).extracting(TypeFieldFormConfig::getValueBinding)
                .contains("type", "geomorphologicalCycle", "normalizedInterpretation", "fullIdentifier",
                        "description", "openingDate", "closingDate", "zInf", "zSup", "phases");
        assertThat(config.getFields()).noneMatch(f -> "Identifiant".equals(f.getName()));
    }

    @Test
    void setFieldActive_shouldBeReflectedInUeValueBindingLookup() {
        service.setFieldActive(1L, ConfigurableTable.UE, "_default", "Description", false);

        TypeFieldsConfig config = service.getFieldsConfig(1L, ConfigurableTable.UE, "_default");
        boolean active = config.getFields().stream()
                .filter(f -> "description".equals(f.getValueBinding()))
                .findFirst().orElseThrow().isActive();

        assertThat(active).isFalse();
    }

    @Test
    void getFormConfig_shouldExposeValueConceptLabelAsTypeNameExceptForDefault() {
        TypeFormConfig ceramique = service.getFormConfig(1L, ConfigurableTable.MOBILIER, "Céramique");
        TypeFormConfig defaultType = service.getFormConfig(1L, ConfigurableTable.MOBILIER, "_default");

        assertThat(ceramique.getValueConceptLabel()).isEqualTo("Céramique");
        assertThat(defaultType.getValueConceptLabel()).isEmpty();
    }
}
