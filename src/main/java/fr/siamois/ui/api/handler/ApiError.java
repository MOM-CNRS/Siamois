package fr.siamois.ui.api.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The one body of every error answered by {@code /api/v1} (the 409 revision conflict adds its {@code data}
 * to the same two keys). The HTTP status carries the class of the error; {@code error} says which one, as a
 * stable machine-readable code a client can switch on; {@code message} is for humans and may change.
 */
@Schema(description = "Corps de toute réponse d'erreur de l'API")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        @Schema(description = "Code stable, exploitable par le client : bad_request, validation_failed, unauthorized, "
                + "forbidden, not_found, method_not_allowed, conflict, payload_too_large, unsupported_media_type, "
                + "internal_error", example = "not_found")
        String error,
        @Schema(description = "Explication lisible, non contractuelle (peut changer)", example = "Projet introuvable ou non accessible")
        String message,
        @Schema(description = "validation_failed : les paramètres ou champs en cause")
        List<Detail> details,
        @Schema(description = "internal_error : identifiant à communiquer pour retrouver l'erreur dans les journaux")
        String correlationId
) {

    /** The body of an error with nothing more to say than its code and message. */
    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null, null);
    }

    /** One invalid parameter or field. */
    @Schema(description = "Un paramètre ou un champ invalide")
    public record Detail(
            @Schema(description = "Nom du paramètre, ou chemin du champ dans le corps", example = "limit") String field,
            @Schema(description = "Ce qui ne va pas") String message
    ) {
    }
}
