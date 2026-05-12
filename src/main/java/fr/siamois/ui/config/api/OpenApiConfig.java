package fr.siamois.ui.config.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;


@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Siamois",
                version = "v1",
                description = "Les routes sous /api/v1/** sont sécurisées par JWT Bearer (Authorization). "
                        + "Jetons : POST /api/v1/auth/login ou POST /api/v1/auth/refresh."
        ),
        security = @SecurityRequirement(name = "bearer-jwt")
)
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token JWT (header Authorization: Bearer …). Généré via /api/v1/auth/login ou renouvelé via /api/v1/auth/refresh."
)
public class OpenApiConfig {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("fr.siamois.ui.api.controller")
                        .or(RequestHandlerSelectors.basePackage("fr.siamois.ui.anotherpackage")))
                .paths(PathSelectors.any())
                .build();
    }

}
