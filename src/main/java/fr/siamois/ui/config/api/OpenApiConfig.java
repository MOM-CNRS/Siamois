package fr.siamois.ui.config.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;


@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Siamois",
                version = "v1",
                description = "Les routes sous /api/v1/** sont sécurisées par JWT Bearer (Authorization). "
                        + "Jeton d'accès : POST /api/v1/auth/login (renouvellement : rappeler login)."
        ),
        security = @SecurityRequirement(name = "bearer-jwt")
)
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token JWT (header Authorization: Bearer …). Généré via POST /api/v1/auth/login."
)
public class OpenApiConfig {
}
