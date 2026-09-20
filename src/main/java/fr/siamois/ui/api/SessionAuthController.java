package fr.siamois.ui.api;

import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

/**
 * Bridges the JSF session's authentication into a short-lived JWT, so React content mounted
 * inside an already-authenticated panel page can call {@code /api/v1/**} (stateless JWT chain)
 * without re-entering credentials. Deliberately NOT under {@code /api/v1/**}: it must run on the
 * session-authenticated web filter chain ({@link fr.siamois.ui.config.WebSecurityConfig#webSecurityFilterChain})
 * so it inherits the JSF session cookie and CSRF protection, not the stateless one.
 * <p>
 * Generic — not specific to any entity type or panel; any React mount point can call this.
 */
@ApiIgnore
@Hidden
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SessionAuthController {

    private final AuthService authService;

    @PostMapping("/session-token")
    public LoginResponse sessionToken() {
        return authService.sessionToken();
    }
}
