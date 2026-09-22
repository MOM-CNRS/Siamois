package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static fr.siamois.ui.table.definitions.TableDefinitions.systemField;

/**
 * Ordre et visibilité par défaut des colonnes « champ système » de la liste des unités
 * d'enregistrement.
 *
 * <p>Source unique de vérité, consommée à la fois par {@link RecordingUnitTableDefinitionFactory}
 * (table JSF) et par l'API {@code GET /api/v1/projects/{id}/recording-unit-types} (table React),
 * exactement comme {@link ActionUnitTableColumnDefaults} l'est pour le projet.</p>
 *
 * <p>Ne contient <strong>pas</strong> les trois colonnes structurelles — chip identifiant
 * ({@code identifierCol}), et les deux compteurs de relation ({@code relationships},
 * {@code specimen}) : elles ne viennent pas du catalogue de champs (pas de {@link CustomField}
 * associé) et sont construites à la main de chaque côté (React les fabrique depuis
 * {@code fullIdentifier} et {@code _counts}).</p>
 *
 * <p>{@code toggleable(true)} est posé pour chacune de ces colonnes lors de leur consommation par
 * la factory JSF — la factory d'origine ne le posait pas sur la plupart d'entre elles (seuls
 * {@code relationships}/{@code specimen}, déjà visibles par défaut, l'avaient), ce qui aurait
 * rendu ces colonnes indisponibles depuis le sélecteur de colonnes JSF malgré le commentaire
 * "Toggleables, masquées par défaut" à côté. Corrigé ici en même temps que l'extraction, comme
 * {@link ActionUnitTableColumnDefaults} le fait déjà pour le projet.</p>
 */
public final class RecordingUnitTableColumnDefaults {

    private RecordingUnitTableColumnDefaults() {}

    /**
     * @param columnId  identifiant de colonne côté table (inchangé depuis la version JSF)
     * @param headerKey clé i18n de l'en-tête
     * @param field     champ système du formulaire de détail ({@code RecordingUnit.DETAILS_FORM}) —
     *                  son id est celui que le catalogue de champs de l'API expose
     * @param visible   visible par défaut (sinon disponible dans le sélecteur de colonnes)
     * @param sortable  triable
     * @param filterable filtrable
     * @param sortField clé de tri synthétique (compteur ou libellé de concept) ; {@code null} si le
     *                  tri se fait directement sur la colonne (le binding du champ lui-même)
     * @param required  requis dans le contexte table (édition en ligne)
     * @param readOnly  lecture seule dans le contexte table
     */
    public record ColumnDefault(String columnId, String headerKey, CustomField field, boolean visible,
                                 boolean sortable, boolean filterable, String sortField,
                                 boolean required, boolean readOnly) {
        public String fieldId() {
            return field.getId() == null ? null : String.valueOf(field.getId());
        }
    }

    private static final List<ColumnDefault> COLUMNS = List.of(
            new ColumnDefault("isPartOf", "common.field.parents", systemField(ConfigurableTable.UE, "parents"),
                    true, true, true, RecordingUnitSpec.PARENTS_COUNT_SORT, false, false),
            new ColumnDefault("contains", "common.field.children", systemField(ConfigurableTable.UE, "children"),
                    true, true, true, RecordingUnitSpec.CHILDREN_COUNT_SORT, false, false),
            new ColumnDefault("action", "recordingunit.field.actionUnit", systemField(ConfigurableTable.UE, "actionUnit"),
                    true, true, true, null, true, true),
            new ColumnDefault("type", "recordingunit.property.type", systemField(ConfigurableTable.UE, "type"),
                    true, true, true, null, true, false),
            new ColumnDefault("phases", "recordingunit.field.phases", systemField(ConfigurableTable.UE, "phases"),
                    true, false, false, null, false, false),
            new ColumnDefault("spatial", "recordingunit.field.spatialUnit", systemField(ConfigurableTable.UE, "spatialUnit"),
                    true, true, true, null, false, false),
            new ColumnDefault("matrixColor", "recordingunit.field.matrixColor", systemField(ConfigurableTable.UE, "matrixColor"),
                    false, true, true, null, false, false),
            new ColumnDefault("openingDate", "recordingunit.field.openingDate", systemField(ConfigurableTable.UE, "openingDate"),
                    false, true, true, null, false, false),
            new ColumnDefault("author", "recordingunit.field.author", systemField(ConfigurableTable.UE, "author"),
                    true, true, true, null, true, false),
            new ColumnDefault("contributors", "recordingunit.field.contributors", systemField(ConfigurableTable.UE, "contributors"),
                    false, false, true, null, false, false),
            new ColumnDefault("geomorphologicalCycle", "recordingunit.field.geomorphologicalCycle",
                    systemField(ConfigurableTable.UE, "geomorphologicalCycle"),
                    false, true, true, RecordingUnitSpec.NATURE_LABEL_SORT, false, false),
            new ColumnDefault("geomorphologicalAgent", "recordingunit.field.geomorphologicalAgent",
                    systemField(ConfigurableTable.UE, "geomorphologicalAgent"),
                    false, true, true, RecordingUnitSpec.AGENT_LABEL_SORT, false, false),
            new ColumnDefault("normalizedInterpretation", "recordingunit.field.normalizedInterpretation",
                    systemField(ConfigurableTable.UE, "normalizedInterpretation"),
                    false, true, true, RecordingUnitSpec.INTERPRETATION_LABEL_SORT, false, false),
            new ColumnDefault("tpq", "recordingunit.field.tpq", systemField(ConfigurableTable.UE, "tpq"),
                    false, true, true, null, false, false),
            new ColumnDefault("taq", "recordingunit.field.taq", systemField(ConfigurableTable.UE, "taq"),
                    false, true, true, null, false, false),
            new ColumnDefault("erosionShape", "recordingunit.field.erosionShape", systemField(ConfigurableTable.UE, "erosionShape"),
                    false, false, false, null, false, false),
            new ColumnDefault("erosionProfile", "recordingunit.field.erosionProfile", systemField(ConfigurableTable.UE, "erosionProfile"),
                    false, false, false, null, false, false),
            new ColumnDefault("erosionOrientation", "recordingunit.field.erosionOrientation",
                    systemField(ConfigurableTable.UE, "erosionOrientation"),
                    false, false, false, null, false, false),
            new ColumnDefault("description", "common.field.description", systemField(ConfigurableTable.UE, "description"),
                    false, false, false, null, false, false),
            new ColumnDefault("comments", "common.field.comments", systemField(ConfigurableTable.UE, "comments"),
                    false, false, false, null, false, false),
            new ColumnDefault("chronologicalPhase", "recordingunit.field.chronologicalPhase",
                    systemField(ConfigurableTable.UE, "chronologicalPhase"),
                    false, false, false, null, false, false),
            new ColumnDefault("zInf", "recordingunit.field.zInf", systemField(ConfigurableTable.UE, "zInf"),
                    false, false, false, null, false, false),
            new ColumnDefault("zSup", "recordingunit.field.zSup", systemField(ConfigurableTable.UE, "zSup"),
                    false, false, false, null, false, false),
            new ColumnDefault("closingDate", "recordingunit.field.closingDate", systemField(ConfigurableTable.UE, "closingDate"),
                    false, false, false, null, false, false)
    );

    public static List<ColumnDefault> columns() {
        return COLUMNS;
    }

    /**
     * Ids des champs visibles par défaut — ce que
     * {@code GET /api/v1/projects/{id}/recording-units?fields=default} projette.
     */
    public static Set<String> defaultVisibleFieldIds() {
        return COLUMNS.stream()
                .filter(ColumnDefault::visible)
                .map(ColumnDefault::fieldId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
