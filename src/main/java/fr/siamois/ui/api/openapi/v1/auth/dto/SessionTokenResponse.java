package fr.siamois.ui.api.openapi.v1.auth.dto;

/**
 * Réponse de {@code POST /api/auth/session-token} : un access token JWT courte durée émis pour
 * l'utilisateur de la session JSF courante, sans ré-authentification par mot de passe. Utilisé pour
 * amorcer un widget React (ex. le panel overview Recording Unit) embarqué dans une page JSF.
 */
public record SessionTokenResponse(
        String accessToken,
        long expiresIn,
        String tokenType,
        AuthUserResponse user
) {
}
