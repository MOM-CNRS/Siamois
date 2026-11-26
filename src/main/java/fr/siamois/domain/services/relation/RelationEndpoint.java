package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.lang.Nullable;

/**
 * An end of a link, resolved: the record itself, the project it belongs to, its type (what a rule's accepted
 * subtypes are compared with) and the institution that owns it (the one the caller's reach is checked against).
 */
public record RelationEndpoint(RelationEndKind kind,
                               long id,
                               Object entity,
                               @Nullable Long projectId,
                               @Nullable Concept type,
                               @Nullable Long institutionId) {

    /** The order a symmetric pair is stored in: by kind, then by id. */
    public boolean isAfter(RelationEndpoint other) {
        int byKind = kind.name().compareTo(other.kind.name());
        return byKind > 0 || (byKind == 0 && id > other.id);
    }

    public boolean sameRecord(RelationEndpoint other) {
        return kind == other.kind && id == other.id;
    }
}
