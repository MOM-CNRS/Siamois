package fr.siamois.domain.models.relation;

/** How the other end of a link is shown (a view of the one stored row, never a second row). */
public enum RelationMirror {
    /** The link only appears on the field it was entered in. */
    NONE,
    /** One field that is its own mirror (symmetric relation, e.g. synchronous); source and target kinds must match. */
    SELF,
    /** Two fields, one per side (e.g. anterior / posterior). */
    OTHER
}
