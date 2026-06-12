package fr.siamois.ui.config.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propriétés {@code siamois.jwt.*}. Les contraintes sur {@code secret} sont appliquées dans
 * {@link JwtService} au démarrage pour ne pas exiger Hibernate Validator sur le binding
 * {@link ConfigurationProperties}.
 */
@Data
@ConfigurationProperties(prefix = "siamois.jwt")
public class JwtProperties {

    /** Secret UTF-8 pour HS256 — au moins 32 caractères, validé dans {@link JwtService}. */
    private String secret;

    private Duration accessTokenValidity = Duration.ofMinutes(15);
}
