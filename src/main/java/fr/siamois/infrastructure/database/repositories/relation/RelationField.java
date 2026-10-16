package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEntityKind;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * The relation fields whose values are never loaded whole: a recording unit's parents, children
 * and finds, a phase's recording units, a container's finds. Each can
 * run to hundreds of values, so a list only ever reads a preview of them and a count
 * ({@link RelationFieldRepository#previews}), and their full list is paged
 * ({@link RelationFieldRepository#page}).
 * <p>
 * Keyed by the field's owner and {@code valueBinding} — the same pair every other part of the
 * field machinery identifies a system field by. Each constant knows how its (owner, target) pairs
 * are stored and which table holds its targets;
 */
public enum RelationField implements RelationSource {

    RECORDING_UNIT_PARENTS(RecordingUnit.class, "parents",
            "SELECT h.fk_child_id AS owner_id, h.fk_parent_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit_hierarchy h",
            RelationEntityKind.RECORDING_UNIT, false),

    RECORDING_UNIT_CHILDREN(RecordingUnit.class, "children",
            "SELECT h.fk_parent_id AS owner_id, h.fk_child_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit_hierarchy h",
            RelationEntityKind.RECORDING_UNIT, false),

    RECORDING_UNIT_FINDS(RecordingUnit.class, "specimenList",
            "SELECT s.fk_recording_unit_id AS owner_id, s.specimen_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM specimen s",
            RelationEntityKind.SPECIMEN, true),

    PHASE_RECORDING_UNITS(Phase.class, "recordingUnits",
            "SELECT rp.fk_phase_id AS owner_id, rp.fk_recording_unit_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM recording_unit_phase rp",
            RelationEntityKind.RECORDING_UNIT, true),

    CONTAINER_FINDS(Container.class, "specimens",
            "SELECT sc.fk_container_id AS owner_id, sc.fk_specimen_id AS target_id, " + NoQualifier.COLUMNS
                    + " FROM specimen_container sc",
            RelationEntityKind.SPECIMEN, true);

    private final Class<?> ownerType;
    private final String valueBinding;
    private final String pairsSql;
    private final RelationEntityKind target;
    private final boolean readOnly;

    RelationField(Class<?> ownerType, String valueBinding, String pairsSql, RelationEntityKind target, boolean readOnly) {
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
        return RelationTarget.of(target).resourceType;
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

    @Override
    public boolean qualified() {
        return false;
    }

    @Override
    public String pairsSql() {
        return pairsSql;
    }

    @Override
    public String targetTable() {
        return RelationTarget.of(target).table;
    }

    @Override
    public String targetKey() {
        return RelationTarget.of(target).key;
    }

    @Override
    public String targetLabel() {
        return RelationTarget.of(target).label;
    }

    /** The relation columns a plain (unqualified) relation selects as nulls. */
    private static final class NoQualifier {
        static final String COLUMNS = "CAST(NULL AS text) AS role, CAST(NULL AS bigint) AS concept_id, "
                + "CAST(NULL AS boolean) AS asynchronous, CAST(NULL AS boolean) AS concept_direction, "
                + "CAST(NULL AS boolean) AS uncertain";
    }
}
