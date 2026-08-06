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
    UE("UE", RecordingUnit.TYPE_FIELD_CODE),
    MOBILIER("Mobilier", Specimen.CATEGORY_FIELD),
    PHASE("Phase", Phase.TYPE_FIELD),
    CONTENANT("Contenant", Container.TYPE_FIELD);

    private final String label;
    private final String fieldCode;

    ConfigurableTable(String label, String fieldCode) {
        this.label = label;
        this.fieldCode = fieldCode;
    }
}
