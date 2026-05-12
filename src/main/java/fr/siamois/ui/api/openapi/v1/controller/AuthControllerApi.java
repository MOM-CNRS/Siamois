package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.service.auth.AuthService;
import fr.siamois.ui.api.openapi.v1.auth.dto.AuthUserResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginRequest;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.RefreshTokenRequest;
import fr.siamois.ui.api.openapi.v1.auth.dto.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentification JWT (mobile / API)")
public class AuthControllerApi {

    private final AuthService authService;

    @SecurityRequirements
    @Operation(summary = "Connexion : email / mot de passe → jetons")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @SecurityRequirements
    @Operation(summary = "Rafraîchir le jeton d'accès à partir d'un refresh token")
    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Révoquer un refresh token (logout)",
            description = "Requiert Authorization: Bearer (access token). Corps optionnel : { \"refreshToken\" } pour révoquer ce refresh en base.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Utilisateur courant (Bearer access token)")
    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.currentUser();
    }
}
