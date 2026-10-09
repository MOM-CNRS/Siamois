package fr.siamois.ui.api.openapi.v1.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The identifier part of a PATCH: the same rules for every entity (free text, never blank, unique in its project). */
final class IdentifierPatch {

    private IdentifierPatch() {
    }

    /** @return the trimmed requested identifier, or {@code null} when the request leaves it alone. */
    static String requested(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identifier ne peut pas être vide");
        }
        return trimmed;
    }

    /** @return {@code true} when a requested identifier differs from the current one. */
    static boolean changes(String requested, String current) {
        return requested != null && !requested.equals(current);
    }

    /** Rejects an identifier already used in the project. */
    static void requireFree(boolean alreadyExists) {
        if (alreadyExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant existe déjà dans le projet");
        }
    }
}
