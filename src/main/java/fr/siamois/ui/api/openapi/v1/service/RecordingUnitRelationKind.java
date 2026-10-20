package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.infrastructure.database.repositories.relation.RelationField;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kinds of relation a recording unit has with another, as the API names them: where the other
 * unit stands relative to it. Each is read from one {@link RelationField}, so when a relation moves
 * to another storage only that field changes, not the names a client filters by.
 */
public enum RecordingUnitRelationKind {

    PARENTS("parents", RelationField.RECORDING_UNIT_PARENTS),
    CHILDREN("children", RelationField.RECORDING_UNIT_CHILDREN),
    ANTERIOR("anterior", RelationField.RECORDING_UNIT_ANTERIOR),
    POSTERIOR("posterior", RelationField.RECORDING_UNIT_POSTERIOR),
    SYNCHRONOUS("synchronous", RelationField.RECORDING_UNIT_SYNCHRONOUS);

    private final String wireName;
    private final RelationField field;

    RecordingUnitRelationKind(String wireName, RelationField field) {
        this.wireName = wireName;
        this.field = field;
    }

    /** The name a client sends and receives. */
    public String wireName() {
        return wireName;
    }

    public RelationField field() {
        return field;
    }

    public static Optional<RecordingUnitRelationKind> ofWireName(String name) {
        return Arrays.stream(values()).filter(kind -> kind.wireName.equals(name)).findFirst();
    }
}
