package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.relation.RelationEntityKind;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** The API collection name of each kind of entity a relation link can join. */
final class RelationResourceTypes {

    private static final Map<String, RelationEntityKind> KINDS = Map.of(
            "recording-units", RelationEntityKind.RECORDING_UNIT,
            "finds", RelationEntityKind.SPECIMEN,
            "phases", RelationEntityKind.PHASE,
            "containers", RelationEntityKind.CONTAINER,
            "places", RelationEntityKind.SPATIAL_UNIT,
            "projects", RelationEntityKind.ACTION_UNIT,
            "persons", RelationEntityKind.PERSON,
            "documents", RelationEntityKind.DOCUMENT,
            "studies", RelationEntityKind.STUDY);

    private RelationResourceTypes() {
    }

    static RelationEntityKind kindOf(String resourceType) {
        RelationEntityKind kind = resourceType == null ? null : KINDS.get(resourceType);
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceType inconnu : " + resourceType);
        }
        return kind;
    }

    static String typeOf(RelationEntityKind kind) {
        return KINDS.entrySet().stream().filter(e -> e.getValue() == kind).map(Map.Entry::getKey).findFirst().orElseThrow();
    }
}
