package fr.siamois.domain.models.relation;

public enum RelationRuleKind {
    /** At most {@code maxCount} links between the same two entities. */
    PAIR_CARDINALITY,
    /** At most {@code maxCount} links of the definition per entity (e.g. a single parent). */
    ENTITY_CARDINALITY,
    /** The relation must stay acyclic (a stratigraphic order is not a hierarchy, but has no cycle). */
    NO_CYCLE,
    /** A pair cannot be linked by two of the rule's definitions at once. */
    EXCLUSION
}
