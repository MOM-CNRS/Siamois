package fr.siamois.domain.models.settings.tableconfig;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
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
    UE("UE", RecordingUnit.TYPE_FIELD_CODE, "{NUM_UE:000}"),
    MOBILIER("Mobilier", Specimen.TYPE_FIELD, "{NUM_MOBILIER:000}"),
    PHASE("Phase", Phase.TYPE_FIELD, "{NUM_PHASE:000}"),
    CONTENANT("Contenant", Container.TYPE_FIELD, "{NUM_CONTAINER:000}"),
    DOCUMENT("Document", Document.TYPE_FIELD, "DOC{NUM_DOCUMENT:0000}"),
    STUDY("Etude", Study.TYPE_FIELD, "ETU{NUM_ETUDE:0000}"),
    /**
     * The projects themselves: numbered per institution (not per project) and configured at the
     * institution level. Not a per-project configurable table: see {@link #projectScopedTables()}.
     */
    PROJECT("Projet", ActionUnit.TYPE_FIELD_CODE, "PROJ{NUM_PROJECT}");

    private final String label;
    private final String fieldCode;
    private final String defaultIdentifierFormat;

    /** The tables configured inside a project (every table but {@link #PROJECT}). */
    public static java.util.List<ConfigurableTable> projectScopedTables() {
        return java.util.Arrays.stream(values()).filter(table -> table != PROJECT).toList();
    }

    /**
     * The project-scoped table a field code belongs to ({@code SIARU.NATURE} is a recording unit field), or
     * {@code null} when it is not one of a configurable table (places, projects, measurements…).
     */
    public static ConfigurableTable ofFieldCode(String fieldCode) {
        if (fieldCode == null) {
            return null;
        }
        String prefix = fieldCode.split("\\.", 2)[0];
        return projectScopedTables().stream()
                .filter(table -> table.fieldCode.split("\\.", 2)[0].equals(prefix))
                .findFirst().orElse(null);
    }

    ConfigurableTable(String label, String fieldCode, String defaultIdentifierFormat) {
        this.label = label;
        this.fieldCode = fieldCode;
        this.defaultIdentifierFormat = defaultIdentifierFormat;
    }
}
