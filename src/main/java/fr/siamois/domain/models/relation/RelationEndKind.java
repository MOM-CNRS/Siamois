package fr.siamois.domain.models.relation;

/**
 * The kind of record at one end of a {@link RelationLink}. Stored by its name, never by its ordinal; the name is also
 * the first argument the delete trigger of the record's table passes (changelog 2026-11-26-1), so renaming a constant
 * needs a changelog.
 */
public enum RelationEndKind {
    ACTION_UNIT,
    RECORDING_UNIT,
    SPECIMEN,
    PHASE,
    CONTAINER,
    DOCUMENT,
    STUDY,
    /** Places do not belong to a project: not accepted at either end for now (docs/modele-relations.md, §10). */
    SPATIAL_UNIT
}
