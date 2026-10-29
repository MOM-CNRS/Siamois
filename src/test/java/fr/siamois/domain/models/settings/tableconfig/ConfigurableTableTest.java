package fr.siamois.domain.models.settings.tableconfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableTableTest {

    @Test
    void ofFieldCode_aFieldOfTheTable_isTheTable() {
        assertThat(ConfigurableTable.ofFieldCode("SIARU.NATURE")).isEqualTo(ConfigurableTable.UE);
        assertThat(ConfigurableTable.ofFieldCode(ConfigurableTable.MOBILIER.getFieldCode())).isEqualTo(ConfigurableTable.MOBILIER);
    }

    @Test
    void ofFieldCode_aFieldOfNoConfigurableTable_isNull() {
        assertThat(ConfigurableTable.ofFieldCode("UNKNOWN.FIELD")).isNull();
        assertThat(ConfigurableTable.ofFieldCode(null)).isNull();
    }
}
