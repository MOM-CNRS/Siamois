package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntityKind;

import java.io.Serializable;

/**
 * Icône (Bootstrap) et famille de couleur d'une table de l'éditeur. Les couleurs vivent dans la feuille
 * de style ({@code --ent-context-*}, {@code --ent-ground-*}, {@code --ent-third-*}) : ici, seulement le nom de classe.
 */
public record ExportEntityStyle(String icon, String cssClass) implements Serializable {

    private static final ExportEntityStyle CONTEXT_PROJECT = new ExportEntityStyle("bi-arrow-down-square", "xe-ent-context");
    private static final ExportEntityStyle CONTEXT_PLACE = new ExportEntityStyle("bi-geo-alt", "xe-ent-context");
    private static final ExportEntityStyle GROUND_UNIT = new ExportEntityStyle("bi-pencil-square", "xe-ent-ground");
    private static final ExportEntityStyle GROUND_SPECIMEN = new ExportEntityStyle("bi-bucket", "xe-ent-ground");
    private static final ExportEntityStyle GROUND_PHASE = new ExportEntityStyle("bi-layers", "xe-ent-ground");
    private static final ExportEntityStyle THIRD_CONTAINER = new ExportEntityStyle("bi-box-seam", "xe-ent-third");
    private static final ExportEntityStyle THIRD_DOCUMENT = new ExportEntityStyle("bi-file-earmark-text", "xe-ent-third");
    private static final ExportEntityStyle THIRD_STUDY = new ExportEntityStyle("bi-clipboard-data", "xe-ent-third");
    private static final ExportEntityStyle NEUTRAL = new ExportEntityStyle("bi-table", "xe-ent-neutral");

    public static ExportEntityStyle of(EntityKind kind) {
        return switch (kind) {
            case RECORDING_UNIT -> GROUND_UNIT;
            case SPECIMEN -> GROUND_SPECIMEN;
            case PHASE -> GROUND_PHASE;
            case SPATIAL_UNIT -> CONTEXT_PLACE;
            case CONTAINER -> THIRD_CONTAINER;
            case DOCUMENT -> THIRD_DOCUMENT;
            case STUDY -> THIRD_STUDY;
        };
    }

    public static ExportEntityStyle project() {
        return CONTEXT_PROJECT;
    }

    /** Table technique, table du cœur commun ou source inconnue. */
    public static ExportEntityStyle neutral() {
        return NEUTRAL;
    }
}
