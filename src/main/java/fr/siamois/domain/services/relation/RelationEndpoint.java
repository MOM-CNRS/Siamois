package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.lang.Nullable;

/**
 * An end of a link, resolved: the entity itself, the project it belongs to (when it has one: a place or a
 * person belongs to none), its config type (what a definition's accepted types are compared with) and the
 * institution that owns it (the one the caller's reach is checked against).
 */
public record RelationEndpoint(RelationEntityKind kind,
                               Long id,
                               Object entity,
                               @Nullable Long projectId,
                               @Nullable Concept type,
                               @Nullable Long institutionId) {
}
