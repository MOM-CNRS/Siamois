package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * The relation fields whose values are never loaded whole: a recording unit's parents, children,
 * finds and stratigraphic relationships, a phase's recording units, a container's finds. Each can
 * run to hundreds of values, so a list only ever reads a preview of them and a count
 * ({@link RelationFieldRepository#previews}), and their full list is paged
 * ({@link RelationFieldRepository#page}).
 * <p>
 * Keyed by the field's owner and {@code valueBinding} — the same pair every other part of the
 * field machinery identifies a system field by. Each constant knows how its (owner, target) pairs
 * are stored and which table holds its targets; the stratigraphic one also carries the relation's
 * own columns (concept, direction, certainty) and the owner's side of it.
 */
public enum RelationField {

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

    // Each stored relationship is seen from both of its units: once as unit1 (the owner is the
    // relation's first unit), once as unit2.
    RECORDING_UNIT_STRATIGRAPHY(RecordingUnit.class, "stratigraphicRelationships",
            "SELECT r.fk_recording_unit_1_id AS owner_id, r.fk_recording_unit_2_id AS target_id, "
                    + "'unit1' AS role, r.fk_relationship_concept_id AS concept_id, r.asynchronous, "
                    + "r.concept_direction, r.uncertain FROM stratigraphic_relationship r "
                    + "UNION ALL "
                    + "SELECT r.fk_recording_unit_2_id, r.fk_recording_unit_1_id, 'unit2', "
                    + "r.fk_relationship_concept_id, r.asynchronous, r.concept_direction, r.uncertain "
                    + "FROM stratigraphic_relationship r",
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
    public String resourceType() {
        return target.resourceType;
    }

    /**
     * Whether the field can't be written through the owner's own answers: an inverse relation (its
     * other end owns it — a find's recording unit, containers, phases) or a qualified one (a
     * stratigraphic relationship is more than a target).
     */
    public boolean readOnly() {
        return readOnly;
    }

    public boolean qualified() {
        return this == RECORDING_UNIT_STRATIGRAPHY;
    }

    String pairsSql() {
        return pairsSql;
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

    /** The relation columns a plain (unqualified) relation selects as nulls. */
    private static final class NoQualifier {
        static final String COLUMNS = "CAST(NULL AS text) AS role, CAST(NULL AS bigint) AS concept_id, "
                + "CAST(NULL AS boolean) AS asynchronous, CAST(NULL AS boolean) AS concept_direction, "
                + "CAST(NULL AS boolean) AS uncertain";
    }
}
