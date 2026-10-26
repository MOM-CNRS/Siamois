package fr.siamois.ui.api.openapi.v1.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The identifier part of a PATCH, or of a creation whose form let the user edit the suggested one: the
 * same rules for every entity (free text, never blank, unique in its project).
 */
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

    /**
     * The identifier chosen in a creation form: absent or blank, nothing happens and the server
     * generates it as usual; otherwise it is set, trimmed, then checked to be free in the project.
     *
     * @param setter        writes it on the entity about to be saved
     * @param alreadyExists checks it, once written, against the project's other entities
     * @return {@code true} when a chosen identifier was applied (generation is then skipped)
     */
    static boolean applyChosen(String raw, Consumer<String> setter, BooleanSupplier alreadyExists) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        setter.accept(raw.trim());
        requireFree(alreadyExists.getAsBoolean());
        return true;
    }
}
