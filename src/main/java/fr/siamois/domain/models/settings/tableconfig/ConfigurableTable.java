package fr.siamois.domain.models.settings.tableconfig;

import lombok.Getter;

@Getter
public enum ConfigurableTable {
    UE("UE"),
    MOBILIER("Mobilier"),
    PHASE("Phase"),
    CONTENANT("Contenant");

    private final String label;

    ConfigurableTable(String label) {
        this.label = label;
    }
}
