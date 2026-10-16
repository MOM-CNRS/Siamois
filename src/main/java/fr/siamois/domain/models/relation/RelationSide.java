package fr.siamois.domain.models.relation;

/** The two ends of a relation definition. A link is stored once; each side reads it from its own end. */
public enum RelationSide {
    SOURCE,
    TARGET
}
