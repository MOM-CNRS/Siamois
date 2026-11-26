package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * The relation fields whose values are never loaded whole: a recording unit's parents, children,
 * finds and stratigraphic relationships (one field per side), a phase's recording units, a container's finds. Each can
 * run to hundreds of values, so a list only ever reads a preview of them and a count
 * ({@link RelationFieldRepository#previews}), and their full list is paged
 * ({@link RelationFieldRepository#page}).
 * <p>
 * Keyed by the field's owner and {@code valueBinding} — the same pair every other part of the
 * field machinery identifies a system field by. Each constant knows how its (owner, target) pairs
 * are stored and which table holds its targets; the stratigraphic one also carries the relation's
 * own columns (concept, direction, certainty) and the owner's side of it.
 */
public enum RelationField implements RelationSource {

    RECORDING_UNIT_PARENTS(RecordingUnit.class, "parents",
            "SELECT h.fk_child_id AS owner_id, h.fk_parent_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit_hierarchy h",
            Target.RECORDING_UNIT, false),

    RECORDING_UNIT_CHILDREN(RecordingUnit.class, "children",
            "SELECT h.fk_parent_id AS owner_id, h.fk_child_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit_hierarchy h",
            Target.RECORDING_UNIT, false),

    RECORDING_UNIT_FINDS(RecordingUnit.class, "specimenList",
            "SELECT s.fk_recording_unit_id AS owner_id, s.specimen_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM specimen s",
            Target.FIND, true),

    // The stratigraphy, on the configurable relations (docs/reprise-stratigraphie.md): the order goes from the
    // posterior unit (its source, whose field -328 lists the units anterior to it) to the anterior one; the synchrony
    // is symmetric. The values keep the columns of the former table (role, direction, asynchronous), so the API's
    // stratigraphic qualifier is unchanged: the owner of -328 is "unit2", that of -329 "unit1".
    RECORDING_UNIT_POSTERIOR(RecordingUnit.class, "posteriorRelationships",
            "SELECT l.target_id AS owner_id, l.source_id AS target_id, 'unit1' AS role, "
                    + "COALESCE(mq.mirror_id, l.fk_qualifier_concept_id) AS concept_id, TRUE AS asynchronous, "
                    + "CASE WHEN l.fk_qualifier_concept_id IS NULL THEN NULL ELSE TRUE END AS concept_direction, l.uncertain, l.relation_link_id AS link_id "
                    + "FROM relation_link l " + Stratigraphy.ORDER + DefinitionRelation.MIRROR_JOIN
                    + "WHERE l.source_kind = 'RECORDING_UNIT' AND l.target_kind = 'RECORDING_UNIT'",
            Target.RECORDING_UNIT, true),

    RECORDING_UNIT_ANTERIOR(RecordingUnit.class, "anteriorRelationships",
            "SELECT l.source_id AS owner_id, l.target_id AS target_id, 'unit2' AS role, "
                    + "l.fk_qualifier_concept_id AS concept_id, TRUE AS asynchronous, "
                    + "CASE WHEN l.fk_qualifier_concept_id IS NULL THEN NULL ELSE FALSE END AS concept_direction, l.uncertain, l.relation_link_id AS link_id "
                    + "FROM relation_link l " + Stratigraphy.ORDER
                    + "WHERE l.source_kind = 'RECORDING_UNIT' AND l.target_kind = 'RECORDING_UNIT'",
            Target.RECORDING_UNIT, true),

    RECORDING_UNIT_SYNCHRONOUS(RecordingUnit.class, "synchronousRelationships",
            "SELECT l.source_id AS owner_id, l.target_id AS target_id, 'unit1' AS role, l.fk_qualifier_concept_id AS concept_id, "
                    + "FALSE AS asynchronous, CAST(NULL AS boolean) AS concept_direction, l.uncertain, l.relation_link_id AS link_id "
                    + "FROM relation_link l " + Stratigraphy.SYNCHRONY
                    + "WHERE l.source_kind = 'RECORDING_UNIT' AND l.target_kind = 'RECORDING_UNIT' "
                    + "UNION ALL "
                    + "SELECT l.target_id, l.source_id, 'unit2', l.fk_qualifier_concept_id, FALSE, CAST(NULL AS boolean), l.uncertain, l.relation_link_id "
                    + "FROM relation_link l " + Stratigraphy.SYNCHRONY
                    + "WHERE l.source_kind = 'RECORDING_UNIT' AND l.target_kind = 'RECORDING_UNIT'",
            Target.RECORDING_UNIT, true),

    ACTION_UNIT_RECORDING_UNITS(ActionUnit.class, "recordingUnitList",
            "SELECT ru.fk_action_unit_id AS owner_id, ru.recording_unit_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit ru WHERE ru.fk_action_unit_id IS NOT NULL",
            Target.RECORDING_UNIT, true),

    PHASE_RECORDING_UNITS(Phase.class, "recordingUnits",
            "SELECT rp.fk_phase_id AS owner_id, rp.fk_recording_unit_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit_phase rp",
            Target.RECORDING_UNIT, true),

    CONTAINER_FINDS(Container.class, "specimens",
            "SELECT sc.fk_container_id AS owner_id, sc.fk_specimen_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM specimen_container sc",
            Target.FIND, true);

    private final Class<?> ownerType;
    private final String valueBinding;
    private final String pairsSql;
    private final Target target;
    private final boolean readOnly;

    RelationField(Class<?> ownerType, String valueBinding, String pairsSql, Target target, boolean readOnly) {
        this.ownerType = ownerType;
        this.valueBinding = valueBinding;
        this.pairsSql = pairsSql;
        this.target = target;
        this.readOnly = readOnly;
    }

    /** The relation field bound to {@code valueBinding} on {@code ownerType}, if it is one. */
    public static Optional<RelationField> of(Class<?> ownerType, @Nullable String valueBinding) {
        if (valueBinding == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(r -> r.ownerType.equals(ownerType) && r.valueBinding.equals(valueBinding))
                .findFirst();
    }

    public static boolean isRelation(Class<?> ownerType, @Nullable String valueBinding) {
        return of(ownerType, valueBinding).isPresent();
    }

    public Class<?> ownerType() {
        return ownerType;
    }

    public String valueBinding() {
        return valueBinding;
    }

    /** Its targets' API resourceType. */
    @Override
    public String resourceType() {
        return target.resourceType;
    }

    /**
     * Whether the field can't be written through the owner's own answers: an inverse relation (its
     * other end owns it — a find's recording unit, containers, phases) or a qualified one (a
     * stratigraphic relationship is more than a target).
     */
    @Override
    public boolean readOnly() {
        return readOnly;
    }

    /** Whether its references carry their relation's own columns (type, uncertainty): the stratigraphic ones. */
    @Override
    public boolean qualified() {
        return isStratigraphic();
    }

    /** One of the three sides of a recording unit's stratigraphic relationships. */
    public boolean isStratigraphic() {
        return stratigraphicPosition() != null;
    }

    /**
     * Where the units of this field stand relative to the owner — {@code anterior}, {@code posterior}
     * or {@code synchronous} — or null for a field that isn't stratigraphic.
     */
    @Nullable
    @Override
    public String stratigraphicPosition() {
        return switch (this) {
            case RECORDING_UNIT_ANTERIOR -> "anterior";
            case RECORDING_UNIT_POSTERIOR -> "posterior";
            case RECORDING_UNIT_SYNCHRONOUS -> "synchronous";
            default -> null;
        };
    }

    /** The stratigraphic field whose units stand at {@code position} relative to the owner, if there is one. */
    public static Optional<RelationField> stratigraphic(@Nullable String position) {
        return Arrays.stream(values()).filter(r -> r.stratigraphicPosition() != null && r.stratigraphicPosition().equals(position)).findFirst();
    }

    String pairsSql() {
        return pairsSql;
    }

    /** Every (owner, target) pair, with its target's label and the relation's own columns. */
    @Override
    public String valuesSql() {
        return "SELECT p.owner_id, p.target_id, " + targetLabel() + " AS label, p.role, p.concept_id, "
                + "c.external_id AS concept_external_id, p.asynchronous, p.concept_direction, p.uncertain, p.link_id "
                + "FROM (" + pairsSql() + ") p "
                + "JOIN " + targetTable() + " t ON t." + targetKey() + " = p.target_id "
                + "LEFT JOIN concept c ON c.concept_id = p.concept_id";
    }

    String targetTable() {
        return target.table;
    }

    String targetKey() {
        return target.key;
    }

    String targetLabel() {
        return target.label;
    }

    private enum Target {
        RECORDING_UNIT("recording_unit", "recording_unit_id", "t.full_identifier", "recording-units"),
        FIND("specimen", "specimen_id", "t.full_identifier", "finds");

        final String table;
        final String key;
        final String label;
        final String resourceType;

        Target(String table, String key, String label, String resourceType) {
            this.table = table;
            this.key = key;
            this.label = label;
            this.resourceType = resourceType;
        }
    }

    /** The joins that keep the links of the two stratigraphic definitions, found by their system fields. */
    public static final class Stratigraphy {
        /** The order's source field: on a unit, the units anterior to it. */
        public static final long ORDER_SOURCE_FIELD = -328L;
        /** The synchrony's field, both of its sides. */
        public static final long SYNCHRONY_FIELD = -330L;

        static final String ORDER = "JOIN relation_definition sd ON sd.relation_definition_id = l.fk_relation_definition_id "
                + "AND sd.fk_source_field_id = " + ORDER_SOURCE_FIELD + " ";
        static final String SYNCHRONY = "JOIN relation_definition sd ON sd.relation_definition_id = l.fk_relation_definition_id "
                + "AND sd.fk_source_field_id = " + SYNCHRONY_FIELD + " ";

        private Stratigraphy() {
        }
    }

    /** The relation columns a plain (unqualified) relation selects as nulls. */
    private static final class NoQualifier {
        static final String COLUMNS = "CAST(NULL AS text) AS role, CAST(NULL AS bigint) AS concept_id, "
                + "CAST(NULL AS boolean) AS asynchronous, CAST(NULL AS boolean) AS concept_direction, "
                + "CAST(NULL AS boolean) AS uncertain, CAST(NULL AS bigint) AS link_id";
    }
}
