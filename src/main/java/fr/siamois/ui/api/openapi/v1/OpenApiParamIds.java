package fr.siamois.ui.api.openapi.v1;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Parsing des identifiants passés en chaîne par l'API OpenAPI (query, path, body).
 */
public final class OpenApiParamIds {

    private OpenApiParamIds() {
    }

    public static String requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, paramName + " est obligatoire");
        }
        return value.trim();
    }

    /**
     * {@code concept_id} numérique (chaîne décimale).
     */
    public static long parseRequiredConceptId(String raw, String paramName) {
        String trimmed = requireNonBlank(raw, paramName);
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    paramName + " invalide (attendu : identifiant numérique) : " + trimmed);
        }
    }
}
