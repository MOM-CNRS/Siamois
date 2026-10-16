package fr.siamois.infrastructure.database.repositories.relation;

/**
 * A relation field's values, as {@link RelationFieldRepository} reads them: where its (owner, target) pairs come
 * from and which table holds its targets. Either one of the {@link RelationField} constants (the structural
 * relations of the model) or a {@link DefinitionRelation} (one side of a configurable relation's links).
 * <p>
 * Every fragment is a constant of the code, never built from a request: the repository inlines them in native SQL.
 */
public interface RelationSource {

    /** A query of (owner_id, target_id) pairs with the columns role, concept_id, asynchronous, concept_direction, uncertain. */
    String pairsSql();

    String targetTable();

    String targetKey();

    /** The SQL expression of a target's label, on the target table aliased {@code t}. */
    String targetLabel();

    /** The targets' API resourceType. */
    String resourceType();

    /** Whether each value carries what the relation says of it (its type, whether it is uncertain). */
    boolean qualified();

    /** Whether the field can't be written through the owner's own answers. */
    boolean readOnly();
}
