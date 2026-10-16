package fr.siamois.domain.models.relation;

/**
 * The kinds of entity a {@link RelationLink} can join. Each one has its own nullable FK on each side of
 * {@code relation_link} (real foreign keys, no polymorphic id); a person is only ever a target, since
 * it owns no form.
 */
public enum RelationEntityKind {
    RECORDING_UNIT("RecordingUnit"),
    SPECIMEN("Specimen"),
    PHASE("Phase"),
    CONTAINER("Container"),
    SPATIAL_UNIT("SpatialUnit"),
    ACTION_UNIT("ActionUnit"),
    PERSON("Person"),
    DOCUMENT("Document"),
    STUDY("Study");

    private final String propertySuffix;

    RelationEntityKind(String propertySuffix) {
        this.propertySuffix = propertySuffix;
    }

    /** What follows {@code fk_source_} / {@code fk_target_} in the column of {@code relation_link} of this kind (before {@code _id}). */
    public String columnSuffix() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** What follows {@code source} / {@code target} in the name of the {@link RelationLink} property of this kind. */
    public String propertySuffix() {
        return propertySuffix;
    }

    /** Whether this kind can be the source of a link (the source is the entity that owns the field). */
    public boolean canBeSource() {
        return this != PERSON;
    }
}
