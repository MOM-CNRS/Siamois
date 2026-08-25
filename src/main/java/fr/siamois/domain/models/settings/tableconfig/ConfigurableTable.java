package fr.siamois.domain.models.settings.tableconfig;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import lombok.Getter;

/**
 * A table whose fields can be configured per type.
 * <p>
 * {@code fieldCode} designates the entity's "type" field, i.e. the field whose values (Creusement,
 * Céramique, ...) each get their own {@code FormConfig}. It is the field code the
 * {@code FormConfig.fieldConcept} of that table is configured on.
 */
@Getter
public enum ConfigurableTable {
    UE("UE", RecordingUnit.TYPE_FIELD_CODE, "{NUM_UE}"),
    MOBILIER("Mobilier", Specimen.CAT_FIELD, "{NUM_MOBILIER}"),
    PHASE("Phase", Phase.TYPE_FIELD, "{NUM_PHASE}"),
    CONTENANT("Contenant", Container.TYPE_FIELD, "{NUM_CONTAINER}");

    private final String label;
    private final String fieldCode;
    private final String defaultIdentifierFormat;

    ConfigurableTable(String label, String fieldCode, String defaultIdentifierFormat) {
        this.label = label;
        this.fieldCode = fieldCode;
        this.defaultIdentifierFormat = defaultIdentifierFormat;
    }
}
