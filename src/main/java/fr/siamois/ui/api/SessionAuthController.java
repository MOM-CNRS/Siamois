package fr.siamois.ui.api;

import fr.siamois.ui.api.openapi.v1.auth.dto.SessionTokenResponse;
import fr.siamois.ui.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pont d'authentification pour les widgets React embarqués dans une page JSF (ex. le panel overview
 * Recording Unit). Volontairement hors {@code /api/v1/**} : ce chemin doit passer par la chaîne de
 * sécurité JSF/session ({@code webSecurityFilterChain}, cookie de session + CSRF), pas par la chaîne
 * stateless JWT ({@code apiV1SecurityFilterChain}) qui intercepte tout {@code /api/v1/**} avant elle.
 * <p>
 * Un appel réussi implique donc déjà une session JSF authentifiée ; il émet en échange un access token
 * JWT courte durée utilisable contre l'API {@code /api/v1/**}.
 */
@Hidden
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SessionAuthController {

    private final AuthService authService;

    @PostMapping("/session-token")
    public SessionTokenResponse sessionToken() {
        return authService.sessionToken();
    }
}
