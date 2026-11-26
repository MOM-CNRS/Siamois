package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationSide;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

/**
 * The API names of what the relations deal with: a record kind is named by its API collection
 * ({@code recording-units}), a side and a relation kind in lower case.
 */
public final class RelationResourceTypes {

    private static final Map<RelationEndKind, String> COLLECTIONS = Map.of(
            RelationEndKind.ACTION_UNIT, "projects",
            RelationEndKind.RECORDING_UNIT, "recording-units",
            RelationEndKind.SPECIMEN, "finds",
            RelationEndKind.PHASE, "phases",
            RelationEndKind.CONTAINER, "containers",
            RelationEndKind.DOCUMENT, "documents",
            RelationEndKind.STUDY, "studies",
            RelationEndKind.SPATIAL_UNIT, "places");

    private RelationResourceTypes() {
    }

    public static String typeOf(RelationEndKind kind) {
        return COLLECTIONS.get(kind);
    }

    public static RelationEndKind kindOf(String resourceType) {
        return COLLECTIONS.entrySet().stream()
                .filter(e -> e.getValue().equals(resourceType))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> badRequest("resourceType inconnu : " + resourceType + " (attendu : "
                        + String.join(", ", COLLECTIONS.values()) + ")"));
    }

    /** {@code source} by default. */
    public static RelationSide sideOf(String side) {
        if (side == null || side.equalsIgnoreCase("source")) return RelationSide.SOURCE;
        if (side.equalsIgnoreCase("target")) return RelationSide.TARGET;
        throw badRequest("side doit valoir source ou target");
    }

    public static String nameOf(RelationSide side) {
        return side.name().toLowerCase();
    }

    public static RelationKind relationKindOf(String kind) {
        return Arrays.stream(RelationKind.values())
                .filter(k -> k.name().equalsIgnoreCase(kind))
                .findFirst()
                .orElseThrow(() -> badRequest("kind doit valoir simple ou complex"));
    }

    public static String nameOf(RelationKind kind) {
        return kind.name().toLowerCase();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
