package fr.siamois.domain.models.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntityKind;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.springframework.lang.Nullable;

/**
 * Ce dont une feuille expose les lignes : une des entités d'un projet, ou le projet lui-même. Sert
 * à retrouver les champs (système et additionnels) que peut lire un modèle.
 */
public enum ExportSubject {
    PROJECT(null),
    RECORDING_UNIT(ConfigurableTable.UE),
    SPECIMEN(ConfigurableTable.MOBILIER),
    PHASE(ConfigurableTable.PHASE),
    CONTAINER(ConfigurableTable.CONTENANT),
    DOCUMENT(ConfigurableTable.DOCUMENT),
    SPATIAL_UNIT(null);

    @Nullable
    private final ConfigurableTable table;

    ExportSubject(@Nullable ConfigurableTable table) {
        this.table = table;
    }

    /** Table configurable par type, ou {@code null} (projet, lieu) : seuls leurs champs système sont lisibles. */
    @Nullable
    public ConfigurableTable table() {
        return table;
    }

    public static ExportSubject of(EntityKind kind) {
        return switch (kind) {
            case RECORDING_UNIT -> RECORDING_UNIT;
            case SPECIMEN -> SPECIMEN;
            case PHASE -> PHASE;
            case CONTAINER -> CONTAINER;
            case DOCUMENT -> DOCUMENT;
            case SPATIAL_UNIT -> SPATIAL_UNIT;
        };
    }
}
