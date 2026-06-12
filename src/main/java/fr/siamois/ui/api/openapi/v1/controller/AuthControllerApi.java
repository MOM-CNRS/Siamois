package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.auth.dto.AuthUserResponse;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginRequest;
import fr.siamois.ui.api.openapi.v1.auth.dto.LoginResponse;
import fr.siamois.ui.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = OpenApiTags.AUTH)
public class AuthControllerApi {

    private final AuthService authService;

    @SecurityRequirements
    @Operation(
            summary = "Connexion",
            description = "Email / mot de passe → access token JWT. Quand le token expire, rappeler cet endpoint pour en obtenir un nouveau."
    )
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @Operation(summary = "Utilisateur courant (Bearer access token)")
    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.currentUser();
    }
}
