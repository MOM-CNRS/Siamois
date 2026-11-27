package fr.siamois.infrastructure.database.repositories.relation;

import org.springframework.lang.Nullable;

/**
 * Where the values of a relation field are read from, for {@link RelationFieldRepository}: a system relation of
 * the current model ({@link RelationField}: hierarchies, stratigraphy, a phase's units…) or a configurable relation
 * ({@link DefinitionRelation}, the links of a definition seen from one side). Either way the same columns come out,
 * so previews, pages and lists keep one contract.
 */
public interface RelationSource {

    /**
     * Every (owner, target) pair, with: {@code owner_id, target_id, label, role, concept_id, concept_external_id,
     * asynchronous, concept_direction, uncertain}. Built only from constants and numeric ids, never from input.
     */
    String valuesSql();

    /** The API resourceType of the targets, or null when it varies by value (then {@code role} carries it). */
    @Nullable
    String resourceType();

    /** Whether its values carry their relation's own columns (qualifier, uncertainty). */
    boolean qualified();

    /** Whether the field can't be written through the owner's own answers. */
    boolean readOnly();

    /** For a stratigraphic system field: where its units stand relative to the owner; null otherwise. */
    @Nullable
    default String stratigraphicPosition() {
        return null;
    }
}
