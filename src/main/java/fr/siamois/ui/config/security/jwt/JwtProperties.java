package fr.siamois.ui.config.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "siamois.jwt")
public class JwtProperties {

    /** Secret UTF-8 pour signature HS256 — minimum 32 caractères (256 bits). */
    @NotBlank(message = "JWT secret must not be blank")
    @Size(min = 32, message = "JWT secret must be at least 32 characters (256 bits) for HS256")
    private String secret;

    private Duration accessTokenValidity = Duration.ofDays(30);
}
